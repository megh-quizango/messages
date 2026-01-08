package com.text.messages.sms.messanger.service

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.receiver.MmsReceivedReceiver
import com.text.messages.sms.messanger.util.MmsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MmsReceiverService {
    private const val TAG = "MmsReceiverService"
    
    /**
     * Process received MMS message
     */
    suspend fun processReceivedMms(context: Context, messageUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing received MMS: $messageUri")
                
                // Sync message from Telephony Provider
                val message = syncMessage(context, messageUri)
                if (message == null) {
                    Log.w(TAG, "Failed to sync message from Telephony Provider")
                    return@withContext
                }
                
                // Get or create conversation
                val conversation = getOrCreateConversation(context, message.threadId, message.address)
                
                // Update conversation metadata
                updateConversation(context, conversation, message)
                
                // Mark conversation unarchived if needed
                if (conversation.archived) {
                    markConversationUnarchived(context, conversation.threadId)
                }
                
                Log.d(TAG, "MMS processed successfully - messageId: ${message.id}, threadId: ${message.threadId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing received MMS", e)
            }
        }
    }
    
    /**
     * Sync message from Telephony Provider to Room database
     */
    suspend fun syncMessage(context: Context, messageUri: Uri): Message? {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                
                // Query MMS message
                val cursor = resolver.query(
                    messageUri,
                    arrayOf(
                        Telephony.Mms._ID,
                        Telephony.Mms.THREAD_ID,
                        Telephony.Mms.DATE,
                        Telephony.Mms.READ,
                        Telephony.Mms.MESSAGE_BOX,
                        Telephony.Mms.SUBJECT
                    ),
                    null,
                    null,
                    null
                ) ?: return@withContext null
                
                cursor.use {
                    if (!it.moveToFirst()) {
                        Log.w(TAG, "No MMS message found at URI: $messageUri")
                        return@withContext null
                    }
                    
                    val mmsId = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms._ID))
                    val threadId = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID))
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000 // Convert to milliseconds
                    val read = it.getInt(it.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1
                    val msgBox = it.getInt(it.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                    val subject = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.SUBJECT))
                    
                    // Get address
                    val address = getMmsAddress(resolver, mmsId) ?: "Unknown"
                    
                    // Get body text from parts
                    val body = getMmsBody(resolver, mmsId)
                    
                    // Create or update message in database
                    val database = MessagesApp.database
                    val messageDao = database.messageDao()
                    val messageId = mmsId
                    
                    val existingMessage = messageDao.getMessageById(messageId)
                    
                    if (existingMessage == null) {
                        // Create new message
                        messageDao.insertMessage(
                            Message(
                                id = messageId,
                                threadId = threadId,
                                address = address,
                                body = body ?: subject ?: "",
                                date = date,
                                type = MessageType.INBOX,
                                status = MessageStatus.RECEIVED,
                                read = read,
                                mimeType = "application/vnd.wap.mms-message",
                                messagePartCount = getMmsPartCount(resolver, mmsId)
                            )
                        )
                        Log.d(TAG, "Created new MMS message in database - messageId: $messageId")
                    } else {
                        // Update existing message
                        messageDao.updateMessage(
                            existingMessage.copy(
                                body = body ?: subject ?: "",
                                read = read,
                                messagePartCount = getMmsPartCount(resolver, mmsId)
                            )
                        )
                        Log.d(TAG, "Updated existing MMS message in database - messageId: $messageId")
                    }
                    
                    return@withContext messageDao.getMessageById(messageId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing MMS message", e)
                null
            }
        }
    }
    
    /**
     * Download MMS from location URL
     */
    suspend fun downloadMms(context: Context, locationUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Downloading MMS from: $locationUrl")
                
                // Create MMS entry in OUTBOX
                val resolver = context.contentResolver
                val mmsValues = ContentValues().apply {
                    put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                    put(Telephony.Mms.READ, 0)
                    put(Telephony.Mms.MESSAGE_TYPE, 132) // MESSAGE_TYPE_NOTIFICATION_IND
                }
                
                val mmsUri = resolver.insert(Telephony.Mms.CONTENT_URI, mmsValues)
                    ?: run {
                        Log.e(TAG, "Failed to create MMS entry for download")
                        return@withContext
                    }
                
                // Prepare Bundle
                val config = android.os.Bundle().apply {
                    val httpParams = MmsHelper.getMmsConfigString(context, "httpParams", "")
                    if (httpParams.isNotEmpty()) {
                        putString("httpParams", httpParams)
                    }
                }
                
                // Create PendingIntent for download completion
                val downloadIntent = PendingIntent.getBroadcast(
                    context,
                    System.currentTimeMillis().toInt(),
                    Intent(context, MmsReceivedReceiver::class.java).apply {
                        putExtra("uri", mmsUri)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Grant URI permission
                context.grantUriPermission(
                    context.packageName,
                    mmsUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Download MMS
                val smsManager = SmsManager.getDefault()
                smsManager.downloadMultimediaMessage(
                    context,
                    locationUrl,
                    mmsUri,
                    config,
                    downloadIntent
                )
                
                Log.d(TAG, "MMS download request submitted")
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading MMS", e)
            }
        }
    }
    
    private fun getMmsAddress(resolver: ContentResolver, mmsId: Long): String? {
        return try {
            val addrUri = Uri.parse("content://mms/$mmsId/addr")
            val cursor = resolver.query(
                addrUri,
                arrayOf(Telephony.Mms.Addr.ADDRESS),
                "${Telephony.Mms.Addr.TYPE} = ?",
                arrayOf("137"), // FROM
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getString(it.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS))
                    return address
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS address", e)
            null
        }
    }
    
    private fun getMmsBody(resolver: ContentResolver, mmsId: Long): String? {
        return try {
            val partUri = Uri.parse("content://mms/$mmsId/part")
            val cursor = resolver.query(
                partUri,
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE),
                "${Telephony.Mms.Part.CONTENT_TYPE} = ?",
                arrayOf("text/plain"),
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val partId = it.getLong(it.getColumnIndexOrThrow(Telephony.Mms.Part._ID))
                    val partDataUri = Uri.parse("content://mms/part/$partId")
                    
                    resolver.openInputStream(partDataUri)?.use { inputStream ->
                        inputStream.bufferedReader().use { reader ->
                            return reader.readText()
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS body", e)
            null
        }
    }
    
    private fun getMmsPartCount(resolver: ContentResolver, mmsId: Long): Int {
        return try {
            val partUri = Uri.parse("content://mms/$mmsId/part")
            val cursor = resolver.query(partUri, null, null, null, null)
            cursor?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS part count", e)
            0
        }
    }
    
    private suspend fun getOrCreateConversation(context: Context, threadId: Long, address: String): Conversation {
        val database = MessagesApp.database
        val conversationDao = database.conversationDao()
        
        val existing = conversationDao.getConversationByThreadId(threadId)
        return if (existing != null) {
            existing
        } else {
            val newConversation = Conversation(
                threadId = threadId,
                address = address,
                snippet = "",
                date = System.currentTimeMillis(),
                unreadCount = 0
            )
            conversationDao.insertConversation(newConversation)
            newConversation
        }
    }
    
    private suspend fun updateConversation(context: Context, conversation: Conversation, message: Message) {
        val database = MessagesApp.database
        val conversationDao = database.conversationDao()
        
        val updatedUnreadCount = if (!message.read) {
            conversation.unreadCount + 1
        } else {
            conversation.unreadCount
        }
        
        conversationDao.updateConversation(
            conversation.copy(
                snippet = message.body,
                date = message.date,
                unreadCount = updatedUnreadCount
            )
        )
    }
    
    private suspend fun markConversationUnarchived(context: Context, threadId: Long) {
        val database = MessagesApp.database
        val conversationDao = database.conversationDao()
        conversationDao.updateArchivedStatus(threadId, false)
    }
}

