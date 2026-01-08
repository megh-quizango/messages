package com.text.messages.sms.messanger.data.dao

import androidx.room.*
import com.text.messages.sms.messanger.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date ASC")
    fun getMessagesByThread(threadId: Long): Flow<List<Message>>
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date ASC")
    suspend fun getMessagesByThreadSync(threadId: Long): List<Message>
    
    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: Long): Message?
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId AND (mimeType IS NOT NULL OR attachmentPath IS NOT NULL) ORDER BY date ASC")
    suspend fun getMmsMessagesByThread(threadId: Long): List<Message>
    
    @Query("SELECT * FROM messages WHERE (mimeType IS NOT NULL OR attachmentPath IS NOT NULL) ORDER BY date ASC")
    suspend fun getAllMmsMessages(): List<Message>
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId AND address = :address AND date >= :startTime AND date <= :endTime LIMIT 1")
    suspend fun findMessageByThreadAndTime(threadId: Long, address: String, startTime: Long, endTime: Long): Message?
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId AND status = :status")
    suspend fun getMessagesByThreadAndStatus(threadId: Long, status: Int): List<Message>
    
    @Query("SELECT * FROM messages WHERE status = :status")
    suspend fun getMessagesByStatus(status: Int): List<Message>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)
    
    @Update
    suspend fun updateMessage(message: Message)
    
    @Update
    suspend fun updateMessages(messages: List<Message>)
    
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: Int)
    
    @Query("UPDATE messages SET read = :read WHERE id = :messageId")
    suspend fun updateMessageReadStatus(messageId: Long, read: Boolean)
    
    @Query("UPDATE messages SET starred = :starred WHERE id = :messageId")
    suspend fun updateMessageStarredStatus(messageId: Long, starred: Boolean)
    
    @Query("UPDATE messages SET status = :newStatus WHERE threadId = :threadId AND status = :oldStatus AND date < :cutoffTime")
    suspend fun updateOldPendingMessages(threadId: Long, oldStatus: Int, newStatus: Int, cutoffTime: Long)
    
    @Query("UPDATE messages SET status = :newStatus WHERE status = :oldStatus AND date < :cutoffTime")
    suspend fun updateAllOldPendingMessages(oldStatus: Int, newStatus: Int, cutoffTime: Long)
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)
    
    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteMessagesByThread(threadId: Long)
    
    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId AND read = 0")
    suspend fun getUnreadCount(threadId: Long): Int
}

