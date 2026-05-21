package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FileStatus {
    NONE,
    PENDING,
    SENDING,
    RECEIVING,
    COMPLETED,
    FAILED
}

enum class DeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderName: String,
    val senderAddress: String, // MAC address or unique device name
    val messageBody: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean,
    val isRead: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    
    // File attachment metadata
    val isFile: Boolean = false,
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val fileStatus: FileStatus = FileStatus.NONE,
    val fileProgress: Float = 0.0f,
    val transferId: String? = null
)
