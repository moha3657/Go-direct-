package com.example.data.repository

import com.example.data.database.ChatMessageDao
import com.example.data.model.ChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.FileStatus
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    val allMessages: Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()

    fun getMessagesForDevice(deviceAddress: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForDevice(deviceAddress)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        return chatMessageDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ChatMessage) {
        chatMessageDao.updateMessage(message)
    }

    suspend fun updateDeliveryStatus(messageId: Long, status: DeliveryStatus) {
        chatMessageDao.updateDeliveryStatus(messageId, status)
    }

    suspend fun markMessagesAsRead(senderAddress: String) {
        chatMessageDao.markMessagesAsRead(senderAddress)
    }

    suspend fun updateFileTransferProgress(transferId: String, progress: Float, status: FileStatus) {
        chatMessageDao.updateFileTransferProgress(transferId, progress, status)
    }

    suspend fun completeFileTransfer(transferId: String, localPath: String, status: FileStatus = FileStatus.COMPLETED) {
        chatMessageDao.completeFileTransfer(transferId, localPath, status)
    }

    suspend fun getMessageById(id: Long): ChatMessage? {
        return chatMessageDao.getMessageById(id)
    }

    suspend fun getMessageByTransferId(transferId: String): ChatMessage? {
        return chatMessageDao.getMessageByTransferId(transferId)
    }

    suspend fun clearHistory() {
        chatMessageDao.clearAllMessages()
    }
}
