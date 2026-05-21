package com.example.data.database

import androidx.room.*
import com.example.data.model.ChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.FileStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE senderAddress = :deviceAddress ORDER BY timestamp ASC")
    fun getMessagesForDevice(deviceAddress: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: Long, status: DeliveryStatus)

    @Query("UPDATE chat_messages SET isRead = true WHERE senderAddress = :senderAddress AND isOutgoing = false")
    suspend fun markMessagesAsRead(senderAddress: String)

    @Query("UPDATE chat_messages SET fileProgress = :progress, fileStatus = :status WHERE transferId = :transferId")
    suspend fun updateFileTransferProgress(transferId: String, progress: Float, status: FileStatus)

    @Query("UPDATE chat_messages SET filePath = :localPath, fileStatus = :status WHERE transferId = :transferId")
    suspend fun completeFileTransfer(transferId: String, localPath: String, status: FileStatus)

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): ChatMessage?

    @Query("SELECT * FROM chat_messages WHERE transferId = :transferId")
    suspend fun getMessageByTransferId(transferId: String): ChatMessage?

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
