package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.ChatDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.FileStatus
import com.example.data.repository.ChatRepository
import com.example.network.ConnectionManager
import com.example.network.ConnectionState
import com.example.network.WifiDirectManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ChatDatabase.getDatabase(application)
    private val repository = ChatRepository(db.chatMessageDao())

    val wifiDirectManager = WifiDirectManager(application)
    val connectionManager = ConnectionManager(application, repository)

    // UI States
    val isWifiP2pEnabled: StateFlow<Boolean> = wifiDirectManager.isWifiP2pEnabled
    val peers: StateFlow<List<WifiP2pDevice>> = wifiDirectManager.peers
    val connectionInfo: StateFlow<WifiP2pInfo?> = wifiDirectManager.connectionInfo
    val thisDevice: StateFlow<WifiP2pDevice?> = wifiDirectManager.thisDevice
    val isDiscovering: StateFlow<Boolean> = wifiDirectManager.isDiscovering
    val isSimulationMode: StateFlow<Boolean> = wifiDirectManager.isSimulationMode

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val peerName: StateFlow<String?> = connectionManager.peerName
    val peerAddress: StateFlow<String?> = connectionManager.peerAddress
    val isPeerTyping: StateFlow<Boolean> = connectionManager.isPeerTyping

    // Filter messages for current active secure peer
    val activeChatMessages: StateFlow<List<ChatMessage>> = peerAddress
        .flatMapLatest { addr ->
            if (addr == null) {
                flowOf(emptyList())
            } else {
                repository.getMessagesForDevice(addr)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Typing debounce control
    private var typingJob: Job? = null
    private val _isUserTyping = MutableStateFlow(false)

    init {
        // Automatically start listening on connection updates or register receiver
        wifiDirectManager.registerReceiver()

        // Monitor P2P Connection Info to launch Socket Server/Client automatically!
        viewModelScope.launch {
            wifiDirectManager.connectionInfo.collect { info ->
                if (info != null && info.groupFormed) {
                    if (info.isGroupOwner) {
                        // We are Group Owner (Server) -> Open Server socket!
                        connectionManager.startListening(isSimulation = wifiDirectManager.isSimulationMode.value)
                    } else {
                        // We are Client -> Connect to Group Owner's host IP!
                        connectionManager.connectToHost(
                            info.groupOwnerAddress.hostAddress, 
                            isSimulation = wifiDirectManager.isSimulationMode.value
                        )
                    }
                } else {
                    // Reset socket session on disconnect
                    connectionManager.disconnect()
                }
            }
        }
    }

    fun toggleSimulationMode(enabled: Boolean) {
        wifiDirectManager.setSimulationMode(enabled)
        if (enabled) {
            // Emulate WifiP2P connected event: Group Formed, we are Group Owner
            // This triggers connectionManager.startListening(isSimulation = true) => simulated loop launches!
            viewModelScope.launch {
                delay(300)
                wifiDirectManager.connect(peers.value.first())
            }
        } else {
            disconnectPeer()
        }
    }

    fun startPeerDiscovery() {
        wifiDirectManager.discoverPeers(
            onSuccess = {
                // Success trigger
            },
            onFailure = { reason ->
                // Handle discovery fails
            }
        )
    }

    fun connectToDevice(device: WifiP2pDevice) {
        wifiDirectManager.connect(device,
            onSuccess = {
                // Initiated connection
            },
            onFailure = { reason ->
                // Handle connection fails
            }
        )
    }

    fun disconnectPeer() {
        viewModelScope.launch {
            wifiDirectManager.disconnect()
            connectionManager.disconnect()
        }
    }

    fun sendText(body: String) {
        if (body.isBlank()) return
        
        viewModelScope.launch {
            val peerAddr = peerAddress.value ?: "unknown_peer"
            val peerNm = peerName.value ?: "Peer"

            // Local insertion immediately with SENDING status
            val outgoingMsg = ChatMessage(
                senderName = Build.MODEL,
                senderAddress = peerAddr,
                messageBody = body,
                isOutgoing = true,
                deliveryStatus = DeliveryStatus.SENDING,
                timestamp = System.currentTimeMillis()
            )
            val insertedId = repository.insertMessage(outgoingMsg)

            // Send encrypted via ConnectionManager
            connectionManager.sendTextMessage(body) { uniqueMsgId ->
                viewModelScope.launch {
                    // Once encrypted and sent successfully over TCP socket, update state to SENT
                    repository.updateDeliveryStatus(insertedId, DeliveryStatus.SENT)
                }
            }
        }
    }

    fun onUserTyping() {
        if (!_isUserTyping.value) {
            _isUserTyping.value = true
            connectionManager.sendTypingIndicator(true)
        }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(1500)
            _isUserTyping.value = false
            connectionManager.sendTypingIndicator(false)
        }
    }

    fun attachFile(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val resolver = context.contentResolver
            
            // Try resolving file details smoothly
            var name = "attachment"
            var size = 0L
            
            try {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex) ?: "attachment"
                        size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                // Fallback standard naming
                name = uri.lastPathSegment ?: "file"
            }

            // Cap file limit to 50MB
            val maxBytes = 50 * 1024 * 1024L
            if (size > maxBytes) {
                // Prevent over-large file transfers
                val errorMsg = ChatMessage(
                    senderName = "System",
                    senderAddress = peerAddress.value ?: "unknown",
                    messageBody = "⚠️ File limit exceeded. Max 50MB transfers are supported on P2P sockets.",
                    isOutgoing = false,
                    deliveryStatus = DeliveryStatus.FAILED,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertMessage(errorMsg)
                return@launch
            }

            val transferId = UUID.randomUUID().toString()
            val peerAddr = peerAddress.value ?: "unknown_peer"

            // Insert placeholder message with local status PENDING
            val attachmentMsg = ChatMessage(
                senderName = Build.MODEL,
                senderAddress = peerAddr,
                messageBody = "Sending file: $name (${formatSize(size)})",
                isOutgoing = true,
                isFile = true,
                filePath = uri.toString(),
                fileName = name,
                fileSize = size,
                fileStatus = FileStatus.PENDING,
                fileProgress = 0f,
                transferId = transferId,
                timestamp = System.currentTimeMillis(),
                deliveryStatus = DeliveryStatus.SENDING
            )
            
            val rowId = repository.insertMessage(attachmentMsg)

            // Stream chunked in background
            connectionManager.sendFile(
                fileUri = uri,
                fileName = name,
                fileSize = size,
                messageIdInDb = rowId,
                transferId = transferId
            )
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirectManager.unregisterReceiver()
        connectionManager.disconnect()
    }

    private fun formatSize(bytes: Long): String {
        return if (bytes < 1024) "$bytes B"
        else if (bytes < 1048576) "${String.format("%.1f", bytes / 1024.0)} KB"
        else "${String.format("%.1f", bytes / 1048576.0)} MB"
    }
}
