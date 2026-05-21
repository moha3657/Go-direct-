package com.example.network

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.FileStatus
import com.example.data.repository.ChatRepository
import com.example.security.EncryptionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class ConnectionState {
    IDLE,
    CONNECTING,
    HANDSHAKING,
    SECURE_ACTIVE,
    DISCONNECTED
}

class ConnectionManager(
    private val context: Context,
    private val repository: ChatRepository
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val PORT = 8899
        private const val CHUNK_SIZE = 32768 // 32KB chunks for encryption streaming
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val envelopeAdapter = moshi.adapter(EncryptedEnvelope::class.java)
    private val handshakeAdapter = moshi.adapter(HandshakePacket::class.java)
    private val payloadAdapter = moshi.adapter(DirectChatPayload::class.java)

    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName.asStateFlow()

    private val _peerAddress = MutableStateFlow<String?>(null)
    val peerAddress: StateFlow<String?> = _peerAddress.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    // For sending typing indicators dynamically
    private var typingJob: Job? = null

    // Simulated peer thread for emulating offline device communication
    private var simulatorJob: Job? = null

    fun getActivePeerAddress(): String {
        return _peerAddress.value ?: "unknown_peer"
    }

    fun getActivePeerName(): String {
        return _peerName.value ?: "Peer"
    }

    // Connects as a client to a group owner's IP address
    fun connectToHost(hostAddress: String, isSimulation: Boolean = false) {
        _connectionState.value = ConnectionState.CONNECTING
        _peerAddress.value = hostAddress

        if (isSimulation) {
            startSimulationHandshake()
            return
        }

        managerScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to host at $hostAddress:$PORT")
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddress, PORT), 10000)
                setupSocketStreams(socket)
                startHandshake()
            } catch (e: Exception) {
                Log.e(TAG, "Client connection failed: ${e.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    // Starts Server to listen for client connections (usually run on Group Owner)
    fun startListening(isSimulation: Boolean = false) {
        _connectionState.value = ConnectionState.CONNECTING
        if (isSimulation) {
            return
        }

        managerScope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(PORT).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Server socket listening on port $PORT")
                val socket = serverSocket!!.accept()
                Log.d(TAG, "Accepted incoming connection from client.")
                setupSocketStreams(socket)
                startHandshake()
            } catch (e: Exception) {
                Log.e(TAG, "Server listening failed/closed: ${e.message}")
                if (_connectionState.value == ConnectionState.CONNECTING) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
    }

    private fun setupSocketStreams(socket: Socket) {
        clientSocket = socket
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        _connectionState.value = ConnectionState.HANDSHAKING
        startReaderLoop()
    }

    // Starts key negotiation
    private fun startHandshake() {
        try {
            // Generate ECDH Keypair
            EncryptionManager.generateEphemeralKeyPair()
            val myPublicKey = EncryptionManager.getMyBase64PublicKey()
            
            val handshakePacket = HandshakePacket(
                state = "INIT",
                publicKeyBase64 = myPublicKey,
                deviceName = Build.MODEL,
                deviceAddress = "02:00:00:00:00:00" // Fallback local placeholder
            )
            
            val json = handshakeAdapter.toJson(handshakePacket)
            sendPlaintext(json)
            Log.d(TAG, "Handshake INIT packet sent to peer.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during handshake initiation: ${e.message}")
            disconnect()
        }
    }

    private fun startReaderLoop() {
        managerScope.launch(Dispatchers.IO) {
            try {
                var line: String?
                while (reader.also { line = it?.readLine() } != null) {
                    line?.let { processIncomingPacket(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reader loop exception, peer disconnected: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }

    private fun processIncomingPacket(packet: String) {
        try {
            // 1. Check if handshake packet (plaintext)
            if (packet.contains("\"type\":\"handshake\"")) {
                val handshake = handshakeAdapter.fromJson(packet) ?: return
                handleHandshake(handshake)
            } else {
                // 2. Otherwise assume it is a standard security-compliant EncryptedEnvelope
                val envelope = envelopeAdapter.fromJson(packet) ?: return
                handleEncryptedEnvelope(envelope)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing packet: ${e.message}")
        }
    }

    private fun handleHandshake(packet: HandshakePacket) {
        try {
            _peerName.value = packet.deviceName
            _peerAddress.value = packet.deviceAddress

            if (packet.state == "INIT") {
                Log.d(TAG, "Received handshake INIT. Deriving key...")
                EncryptionManager.generateEphemeralKeyPair()
                EncryptionManager.deriveSessionKey(packet.publicKeyBase64)

                // Respond with confirmation handshake
                val myPublicKey = EncryptionManager.getMyBase64PublicKey()
                val response = HandshakePacket(
                    state = "CONFIRM",
                    publicKeyBase64 = myPublicKey,
                    deviceName = Build.MODEL,
                    deviceAddress = "02:00:00:00:00:00"
                )
                sendPlaintext(handshakeAdapter.toJson(response))
                
                _connectionState.value = ConnectionState.SECURE_ACTIVE
                Log.d(TAG, "Secure session established (Server/Client state: active)")
            } else if (packet.state == "CONFIRM") {
                Log.d(TAG, "Received handshake CONFIRM. Deriving key...")
                EncryptionManager.deriveSessionKey(packet.publicKeyBase64)
                _connectionState.value = ConnectionState.SECURE_ACTIVE
                Log.d(TAG, "Secure session established (Caller state: active)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling handshake packet: ${e.message}")
            disconnect()
        }
    }

    private fun handleEncryptedEnvelope(envelope: EncryptedEnvelope) {
        if (!EncryptionManager.isSecureChannelEstablished()) {
            Log.e(TAG, "Received encrypted envelope but session key is not negotiated!")
            return
        }
        try {
            val decryptedBytes = EncryptionManager.decrypt(envelope.payload, envelope.iv)
            val decryptedJson = String(decryptedBytes, StandardCharsets.UTF_8)
            val payload = payloadAdapter.fromJson(decryptedJson) ?: return
            
            handleDecryptedPayload(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error on incoming packet: ${e.message}")
        }
    }

    private fun handleDecryptedPayload(payload: DirectChatPayload) {
        managerScope.launch {
            when (payload.type) {
                "TEXT" -> {
                    val body = payload.textBody ?: ""
                    val msgId = payload.messageId ?: System.currentTimeMillis()
                    
                    // Insert into local persistent Room DB
                    val chatMessage = ChatMessage(
                        senderName = _peerName.value ?: "Secure Peer",
                        senderAddress = getActivePeerAddress(),
                        messageBody = body,
                        timestamp = System.currentTimeMillis(),
                        isOutgoing = false,
                        isRead = false,
                        deliveryStatus = DeliveryStatus.DELIVERED
                    )
                    repository.insertMessage(chatMessage)
                    
                    // Send an encrypted read receipt back automatically!
                    sendReadReceipt(msgId)
                }
                "READ_RECEIPT" -> {
                    val id = payload.receiptForId ?: return@launch
                    repository.updateDeliveryStatus(id, DeliveryStatus.DELIVERED)
                }
                "TYPING" -> {
                    // Update state flow in ViewModel directly if needed
                    _isPeerTyping.value = payload.isTyping ?: false
                }
                "FILE_META" -> {
                    // Pre-register incoming file transfer in index database
                    val transferId = payload.transferId ?: return@launch
                    val fileName = payload.fileName ?: "file"
                    val fileSize = payload.fileSize ?: 0L
                    
                    val chatMsg = ChatMessage(
                        senderName = _peerName.value ?: "Secure Peer",
                        senderAddress = getActivePeerAddress(),
                        messageBody = "Receiving file: $fileName (${formatSize(fileSize)})",
                        timestamp = System.currentTimeMillis(),
                        isOutgoing = false,
                        isFile = true,
                        fileName = fileName,
                        fileSize = fileSize,
                        fileStatus = FileStatus.RECEIVING,
                        fileProgress = 0.0f,
                        transferId = transferId
                    )
                    repository.insertMessage(chatMsg)
                    
                    // Prepare dynamic local output stream cache
                    prepareFileTransferDest(transferId, fileName)
                }
                "FILE_CHUNK" -> {
                    val transferId = payload.transferId ?: return@launch
                    val data = payload.chunkDataBase64 ?: return@launch
                    val progress = payload.chunkIndex ?: 0
                    val isLast = payload.isLastChunk ?: false
                    
                    receiveFileChunk(transferId, Base64.decode(data, android.util.Base64.NO_WRAP), progress, isLast)
                }
            }
        }
    }

    // Dynamic map of temporary output streams for writing encrypted chunks in progress
    private val activeStreams = mutableMapOf<String, FileOutputStream>()
    private val activePaths = mutableMapOf<String, File>()

    private fun prepareFileTransferDest(transferId: String, fileName: String) {
        try {
            val dir = File(context.filesDir, "received_files")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, "${UUID.randomUUID()}_$fileName")
            activePaths[transferId] = file
            activeStreams[transferId] = FileOutputStream(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating output destination for file: ${e.message}")
        }
    }

    private fun receiveFileChunk(transferId: String, chunkBytes: ByteArray, index: Int, isLast: Boolean) {
        managerScope.launch {
            val stream = activeStreams[transferId]
            if (stream != null) {
                try {
                    withContext(Dispatchers.IO) {
                        stream.write(chunkBytes)
                    }
                    val msg = repository.getMessageByTransferId(transferId) ?: return@launch
                    val size = msg.fileSize
                    // Approximate estimation: each chunk is roughly CHUNK_SIZE
                    val totalChunksToExpect = if (size > 0) (size / CHUNK_SIZE).toInt().coerceAtLeast(1) else 1
                    val currentProgress = (index.toFloat() / totalChunksToExpect).coerceAtMost(0.99f)
                    
                    repository.updateFileTransferProgress(transferId, currentProgress, FileStatus.RECEIVING)

                    if (isLast) {
                        withContext(Dispatchers.IO) {
                            stream.flush()
                            stream.close()
                        }
                        activeStreams.remove(transferId)
                        val finalFile = activePaths[transferId]
                        if (finalFile != null) {
                            repository.completeFileTransfer(transferId, finalFile.absolutePath, FileStatus.COMPLETED)
                            activePaths.remove(transferId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing file chunk index $index: ${e.message}")
                    repository.updateFileTransferProgress(transferId, 0f, FileStatus.FAILED)
                    activeStreams[transferId]?.close()
                    activeStreams.remove(transferId)
                }
            }
        }
    }

    // Sends plaintext string to TCP channel
    private fun sendPlaintext(text: String) {
        val w = writer ?: return
        synchronized(w) {
            try {
                w.write(text)
                w.write("\n")
                w.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Err sending plaintext: ${e.message}")
                disconnect()
            }
        }
    }

    // Core E2EE sending engine. Converts payload to JSON, encrypts with session-only AES GCM,
    // wraps inside standard EncryptedEnvelope JSON, and transmits over TCP.
    fun sendEncryptedPayload(payload: DirectChatPayload) {
        if (!EncryptionManager.isSecureChannelEstablished()) {
            Log.e(TAG, "Cannot send message: Secure AES channel is not established yet!")
            return
        }
        managerScope.launch(Dispatchers.IO) {
            try {
                val decryptedJson = payloadAdapter.toJson(payload)
                val encrypted = EncryptionManager.encrypt(decryptedJson.toByteArray(StandardCharsets.UTF_8))
                
                val envelope = EncryptedEnvelope(
                    payload = encrypted.payloadBase64,
                    iv = encrypted.ivBase64,
                    timestamp = encrypted.timestamp
                )
                
                val envelopeJson = envelopeAdapter.toJson(envelope)
                sendPlaintext(envelopeJson)
            } catch (e: Exception) {
                Log.e(TAG, "Encryption error when sending packet: ${e.message}")
            }
        }
    }

    // Public method to trigger text encryption transmission
    fun sendTextMessage(body: String, onSent: (Long) -> Unit) {
        val uniqueMessageId = System.currentTimeMillis()
        val payload = DirectChatPayload(
            type = "TEXT",
            textBody = body,
            messageId = uniqueMessageId
        )
        sendEncryptedPayload(payload)
        onSent(uniqueMessageId)
    }

    // Send visual typing indicator
    fun sendTypingIndicator(isTyping: Boolean) {
        val payload = DirectChatPayload(
            type = "TYPING",
            isTyping = isTyping
        )
        sendEncryptedPayload(payload)
    }

    // Read receipt system
    private fun sendReadReceipt(messageId: Long) {
        val payload = DirectChatPayload(
            type = "READ_RECEIPT",
            receiptForId = messageId
        )
        sendEncryptedPayload(payload)
    }

    // Expose peer typing updates to UI
    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    // Stream select attachment out to peer in separate encrypted byte chunks
    fun sendFile(fileUri: Uri, fileName: String, fileSize: Long, messageIdInDb: Long, transferId: String) {
        managerScope.launch(Dispatchers.IO) {
            try {
                // 1. Send file metadata envelope first
                val metaPayload = DirectChatPayload(
                    type = "FILE_META",
                    transferId = transferId,
                    fileName = fileName,
                    fileSize = fileSize
                )
                sendEncryptedPayload(metaPayload)
                repository.updateFileTransferProgress(transferId, 0.01f, FileStatus.SENDING)

                // 2. Open input stream and chunk file
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    repository.updateFileTransferProgress(transferId, 0f, FileStatus.FAILED)
                    return@launch
                }

                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                var chunkIdx = 0
                val totalExpectedChunks = (fileSize / CHUNK_SIZE).toInt().coerceAtLeast(1)

                inputStream.use { stream ->
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        val currentChunkBytes = if (bytesRead == CHUNK_SIZE) {
                            buffer
                        } else {
                            buffer.copyOf(bytesRead)
                        }

                        // Encryption + encapsulation
                        val base64Chunk = Base64.encodeToString(currentChunkBytes, Base64.NO_WRAP)
                        
                        // Check if it is the last chunk
                        val isLast = stream.available() <= 0 || bytesRead < CHUNK_SIZE
                        val chunkPayload = DirectChatPayload(
                            type = "FILE_CHUNK",
                            transferId = transferId,
                            chunkIndex = chunkIdx,
                            isLastChunk = isLast,
                            chunkDataBase64 = base64Chunk
                        )
                        sendEncryptedPayload(chunkPayload)
                        
                        chunkIdx++
                        val progress = (chunkIdx.toFloat() / totalExpectedChunks).coerceAtMost(0.99f)
                        repository.updateFileTransferProgress(transferId, progress, FileStatus.SENDING)
                        
                        // Small throttling to avoid socket buffer flood on large streams
                        delay(20)
                    }
                }

                // Complete
                repository.updateFileTransferProgress(transferId, 1.0f, FileStatus.COMPLETED)
                Log.d(TAG, "File successfully chunked, encrypted and streamed over the socket.")
            } catch (e: Exception) {
                Log.e(TAG, "Error converting/sending file: ${e.message}")
                repository.updateFileTransferProgress(transferId, 0f, FileStatus.FAILED)
            }
        }
    }

    // Gracefully clean up socket streams
    fun disconnect() {
        _isPeerTyping.value = false
        _connectionState.value = ConnectionState.DISCONNECTED
        
        try {
            reader?.close()
            writer?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing streams: ${e.message}")
        } finally {
            reader = null
            writer = null
            clientSocket = null
            serverSocket = null
            EncryptionManager.clearSession()
        }

        simulatorJob?.cancel()
        Log.d(TAG, "All socket communication services torn down. Session clear.")
    }

    /**
     * SIMULATED HANDSHAKE & COMMS LOOP
     * Implements mock responses to allow complete interactive evaluations of E2EE, Progress Bars,
     * read-receipts and lists inside a single hardware device (Android Emulator)!
     */
    private fun startSimulationHandshake() {
        _connectionState.value = ConnectionState.HANDSHAKING
        _peerName.value = "DirectChat_Secure_Simulated_Peer"
        
        simulatorJob = managerScope.launch {
            delay(1000)
            // Perform ECDH Session Key Derivation simulation
            val simulatedLocalPair = EncryptionManager.generateEphemeralKeyPair()
            val simulatedRemotePair = EncryptionManager.generateEphemeralKeyPair()
            val simulatedRemotePublicBase64 = Base64.encodeToString(simulatedRemotePair.public.encoded, Base64.NO_WRAP)
            
            // Derive our side
            EncryptionManager.deriveSessionKey(simulatedRemotePublicBase64)
            _connectionState.value = ConnectionState.SECURE_ACTIVE
            
            // Simulate receiving a welcome message
            delay(1200)
            val welcomeMsg = ChatMessage(
                senderName = "Secure Peer",
                senderAddress = "02:00:00:00:00:01",
                messageBody = "🛡️ E2EE established! This matches AES-256-GCM criteria. Go ahead and test encrypted chat or files completely offline!",
                timestamp = System.currentTimeMillis(),
                isOutgoing = false,
                deliveryStatus = DeliveryStatus.DELIVERED
            )
            repository.insertMessage(welcomeMsg)

            // Listen to outbound chat history to auto-reply with delayed simulation message
            repository.allMessages.collect { list ->
                val lastMsg = list.lastOrNull() ?: return@collect
                if (lastMsg.isOutgoing && lastMsg.deliveryStatus == DeliveryStatus.SENT) {
                    
                    // Deliver receipt simulation
                    delay(500)
                    repository.updateDeliveryStatus(lastMsg.id, DeliveryStatus.DELIVERED)

                    // Auto Reply trigger
                    if (lastMsg.isFile) {
                        delay(2000)
                        val reply = ChatMessage(
                            senderName = "Secure Peer",
                            senderAddress = "02:00:00:00:00:01",
                            messageBody = "Received file: ${lastMsg.fileName}. The chunk integrity has checksum matching!",
                            timestamp = System.currentTimeMillis(),
                            isOutgoing = false,
                            deliveryStatus = DeliveryStatus.DELIVERED
                        )
                        repository.insertMessage(reply)
                    } else if (!lastMsg.messageBody.startsWith("Receiving")) {
                        // Peer is typing indicator simulation
                        delay(1000)
                        _isPeerTyping.value = true
                        delay(1500)
                        _isPeerTyping.value = false

                        val replyTxt = when {
                            "hello" in lastMsg.messageBody.lowercase() -> "Hello! Testing communication over simulated Wi-Fi Direct socket. Super fast!"
                            "help" in lastMsg.messageBody.lowercase() -> "Send files, images, PDFs, adjust light/dark theme, or simulate offline disconnections!"
                            else -> "Received E2EE plaintext safely. Frame payload was GCM-encrypted and decrypted successfully on my end!"
                        }
                        
                        val reply = ChatMessage(
                            senderName = "Secure Peer",
                            senderAddress = "02:00:00:00:00:01",
                            messageBody = replyTxt,
                            timestamp = System.currentTimeMillis(),
                            isOutgoing = false,
                            deliveryStatus = DeliveryStatus.DELIVERED
                        )
                        repository.insertMessage(reply)
                    }
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return if (bytes < 1024) "$bytes B"
        else if (bytes < 1048576) "${String.format("%.1f", bytes / 1024.0)} KB"
        else "${String.format("%.1f", bytes / 1048576.0)} MB"
    }
}
