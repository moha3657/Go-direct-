package com.example.network

import com.squareup.moshi.JsonClass

/**
 * The standard envelope required by the security mandates.
 * Every encrypted socket payload is sent as this JSON blob.
 */
@JsonClass(generateAdapter = true)
data class EncryptedEnvelope(
    val type: String = "encrypted_message",
    val payload: String,     // Base64 encrypted JSON data
    val iv: String,          // Base64 serialization of Initialization Vector
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * This handshake packet is sent in PLAINTEXT once as the first socket payload
 * to initiate and agree on the Diffie-Hellman / ECDH shared secret.
 */
@JsonClass(generateAdapter = true)
data class HandshakePacket(
    val type: String = "handshake",
    val state: String,         // "INIT" or "CONFIRM"
    val publicKeyBase64: String,
    val deviceName: String,
    val deviceAddress: String // Wi-Fi P2P address if available, or generated UUID
)

/**
 * Inner payload type. Once the secure channel is established, we encrypt this inner payload,
 * convert it to JSON, encrypt it with AES-256-GCM, and wrap it inside the EncryptedEnvelope.
 */
@JsonClass(generateAdapter = true)
data class DirectChatPayload(
    val type: String, // "TEXT", "TYPING", "READ_RECEIPT", "FILE_META", "FILE_CHUNK"
    
    // TEXT details
    val textBody: String? = null,
    val messageId: Long? = null,
    
    // TYPING details
    val isTyping: Boolean? = null,
    
    // READ_RECEIPT details
    val receiptForId: Long? = null,
    
    // FILE_META details
    val transferId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    
    // FILE_CHUNK details
    val chunkIndex: Int? = null,
    val isLastChunk: Boolean? = null,
    val chunkDataBase64: String? = null
)
