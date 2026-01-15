package com.text.messages.sms.messanger.data.dao

import androidx.room.*
import com.text.messages.sms.messanger.data.model.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Query("SELECT * FROM conversations ORDER BY date DESC")
    fun getAllConversations(): Flow<List<Conversation>>
    
    @Query("SELECT * FROM conversations ORDER BY date DESC")
    suspend fun getAllConversationsSync(): List<Conversation>
    
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY date DESC")
    suspend fun getActiveConversations(): List<Conversation>
    
    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY date DESC")
    suspend fun getArchivedConversations(): List<Conversation>
    
    @Query("SELECT * FROM conversations WHERE threadId = :threadId LIMIT 1")
    suspend fun getConversationByThreadId(threadId: Long): Conversation?
    
    @Query("SELECT * FROM conversations WHERE threadId = :threadId LIMIT 1")
    fun getConversationByThreadIdFlow(threadId: Long): Flow<Conversation?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<Conversation>)
    
    @Update
    suspend fun updateConversation(conversation: Conversation)
    
    @Query("UPDATE conversations SET snippet = :snippet, date = :date, unreadCount = unreadCount + 1 WHERE threadId = :threadId")
    suspend fun updateConversationSnippet(threadId: Long, snippet: String, date: Long)

    @Query("UPDATE conversations SET snippet = :snippet, date = :date, unreadCount = unreadCount + 1, lastOtp = :lastOtp WHERE threadId = :threadId")
    suspend fun updateConversationSnippetWithOtp(threadId: Long, snippet: String, date: Long, lastOtp: String?)

    @Query("UPDATE conversations SET lastOtp = :lastOtp WHERE threadId = :threadId")
    suspend fun updateLastOtp(threadId: Long, lastOtp: String?)
    
    @Query("UPDATE conversations SET unreadCount = :count WHERE threadId = :threadId")
    suspend fun updateUnreadCount(threadId: Long, count: Int)
    
    @Query("UPDATE conversations SET archived = :archived WHERE threadId = :threadId")
    suspend fun updateArchivedStatus(threadId: Long, archived: Boolean)
    
    @Query("UPDATE conversations SET blocked = :blocked WHERE threadId = :threadId")
    suspend fun updateBlockedStatus(threadId: Long, blocked: Boolean)
    
    @Query("UPDATE conversations SET contactName = :contactName WHERE threadId = :threadId")
    suspend fun updateContactName(threadId: Long, contactName: String?)
    
    @Query("UPDATE conversations SET photoUri = :photoUri WHERE threadId = :threadId")
    suspend fun updatePhotoUri(threadId: Long, photoUri: String?)
    
    @Query("DELETE FROM conversations WHERE threadId = :threadId")
    suspend fun deleteConversation(threadId: Long)
    
    @Query("SELECT COUNT(*) FROM conversations WHERE unreadCount > 0")
    suspend fun getTotalUnreadCount(): Int
}

