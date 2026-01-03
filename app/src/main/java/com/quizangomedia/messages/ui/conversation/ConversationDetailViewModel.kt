package com.quizangomedia.messages.ui.conversation

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageStatus
import com.quizangomedia.messages.data.model.MessageType
import com.quizangomedia.messages.receiver.SmsDeliveredReceiver
import com.quizangomedia.messages.receiver.SmsSentReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ConversationDetailViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "ConversationDetailVM"
    }
    
    private val _messages = MutableLiveData<List<MessageListItem>>()
    val messages: LiveData<List<MessageListItem>> = _messages
    
    fun loadMessages(threadId: Long, address: String? = null) {
        Log.d(TAG, "loadMessages called - threadId: $threadId, address: $address")
        viewModelScope.launch {
            try {
                val messageList = withContext(Dispatchers.IO) {
                    // Ensure we have a valid thread ID
                    val actualThreadId = if (threadId > 0) {
                        threadId
                    } else if (!address.isNullOrEmpty()) {
                        // Get thread ID from address
                        Telephony.Threads.getOrCreateThreadId(MessagesApp.instance, address)
                    } else {
                        -1L
                    }
                    Log.d(TAG, "Loading messages - actualThreadId: $actualThreadId")
                    val messages = loadMessagesFromDevice(actualThreadId, address)
                    Log.d(TAG, "Loaded ${messages.size} messages from device")
                    messages
                }
                val itemsWithDates = addDateHeaders(messageList)
                Log.d(TAG, "Posting ${itemsWithDates.size} message items to UI")
                _messages.postValue(itemsWithDates)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
                e.printStackTrace()
                _messages.postValue(emptyList())
            }
        }
    }
    
    private fun addDateHeaders(messages: List<Message>): List<MessageListItem> {
        if (messages.isEmpty()) return emptyList()
        
        val items = mutableListOf<MessageListItem>()
        var currentDate: Long? = null
        
        messages.forEach { message ->
            val messageDate = getDateOnly(message.date)
            
            // Add date header if this is a new date
            if (currentDate == null || messageDate != currentDate) {
                items.add(MessageListItem.DateHeader(message.date))
                currentDate = messageDate
            }
            
            items.add(MessageListItem.MessageItem(message))
        }
        
        return items
    }
    
    private fun getDateOnly(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun loadMessagesFromDevice(threadId: Long, address: String?): List<Message> {
        Log.d(TAG, "loadMessagesFromDevice - threadId: $threadId, address: $address")
        val context = MessagesApp.instance
        val messagesList = mutableListOf<Message>()
        
        // Query SMS from device for this thread/address
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )
        
        // Always prefer threadId if available, as it's more reliable
        val actualThreadId = if (threadId > 0) {
            threadId
        } else if (!address.isNullOrEmpty()) {
            // Get thread ID from address
            Telephony.Threads.getOrCreateThreadId(context, address)
        } else {
            -1L
        }
        
        Log.d(TAG, "loadMessagesFromDevice - actualThreadId: $actualThreadId")
        
        val selection: String?
        val selectionArgs: Array<String>?
        
        if (actualThreadId > 0) {
            // Use thread ID for querying (most reliable)
            selection = "${Telephony.Sms.THREAD_ID} = ?"
            selectionArgs = arrayOf(actualThreadId.toString())
            Log.d(TAG, "Querying by threadId: $actualThreadId")
        } else if (!address.isNullOrEmpty()) {
            // Fallback to address matching - query all and filter in memory
            selection = null
            selectionArgs = null
            Log.d(TAG, "Querying all messages (will filter by address: $address)")
        } else {
            selection = null
            selectionArgs = null
            Log.w(TAG, "No threadId or address provided, querying all messages")
        }
        
        val sortOrder = "${Telephony.Sms.DATE} ASC"
        
        try {
            Log.d(TAG, "Executing query - URI: $uri, selection: $selection, selectionArgs: ${selectionArgs?.contentToString()}")
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            if (cursor == null) {
                Log.e(TAG, "Query returned null cursor!")
                return messagesList
            }
            
            Log.d(TAG, "Query returned cursor with ${cursor.count} rows")
            
            cursor.use {
                var rowCount = 0
                while (cursor.moveToNext()) {
                    rowCount++
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val msgThreadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                    val msgAddress = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    
                    Log.d(TAG, "Row $rowCount - ID: $id, ThreadId: $msgThreadId, Address: $msgAddress, Type: $type, Date: $date")
                    
                    // If we're filtering by address (no threadId), make sure the address matches
                    if (actualThreadId <= 0 && !address.isNullOrEmpty()) {
                        val normalizedMsgAddress = normalizePhoneNumber(msgAddress)
                        val normalizedTargetAddress = normalizePhoneNumber(address)
                        // Check if addresses match (handles different formatting)
                        val addressesMatch = normalizedMsgAddress == normalizedTargetAddress ||
                            normalizedMsgAddress.endsWith(normalizedTargetAddress) ||
                            normalizedTargetAddress.endsWith(normalizedMsgAddress) ||
                            (normalizedMsgAddress.length >= 10 && normalizedTargetAddress.length >= 10 &&
                             normalizedMsgAddress.takeLast(10) == normalizedTargetAddress.takeLast(10))
                        
                        if (!addressesMatch) {
                            continue
                        }
                    }
                    
                    val messageType = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageType.INBOX
                        Telephony.Sms.MESSAGE_TYPE_SENT -> MessageType.SENT
                        Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageType.DRAFT
                        else -> MessageType.INBOX
                    }
                    
                    val messageStatus = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
                        else -> MessageStatus.DELIVERED
                    }
                    
                    messagesList.add(Message().apply {
                        this.id = id
                        this.threadId = msgThreadId
                        this.address = msgAddress
                        this.body = body
                        this.date = date
                        this.type = messageType
                        this.status = messageStatus
                        this.read = read
                    })
                }
                Log.d(TAG, "Processed $rowCount rows, added ${messagesList.size} messages to list")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying messages from device", e)
            e.printStackTrace()
        }
        
        Log.d(TAG, "loadMessagesFromDevice returning ${messagesList.size} messages")
        return messagesList
    }
    
    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove common formatting characters for better matching
        return phoneNumber.replace(Regex("[\\s\\-\\(\\)\\+]"), "")
    }
    
    fun sendMessage(threadId: Long, address: String, body: String) {
        Log.d(TAG, "sendMessage called - threadId: $threadId, address: $address, body length: ${body.length}")
        
        // Get signature from SharedPreferences and append to message
        val signature = getSignature()
        val messageBody = if (signature.isNotEmpty()) {
            "$body\n$signature"
        } else {
            body
        }
        Log.d(TAG, "Message body prepared (with signature): length=${messageBody.length}")
        
        viewModelScope.launch {
            // Generate unique message ID outside try block so it's accessible in catch block
            val messageId = System.currentTimeMillis()
            Log.d(TAG, "Generated messageId: $messageId")
            
            try {
                val context = MessagesApp.instance
                val realm = MessagesApp.realm
                
                // Get or create thread ID
                val actualThreadId = if (threadId > 0) {
                    threadId
                } else {
                    Telephony.Threads.getOrCreateThreadId(context, address)
                }
                Log.d(TAG, "Using threadId: $actualThreadId (original: $threadId)")
                
                val timestamp = System.currentTimeMillis()
                
                // STEP 1: Store message in Realm Database first (status: PENDING)
                Log.d(TAG, "STEP 1: Storing message in Realm database")
                realm.writeBlocking {
                    // Create or update conversation
                    val existingConversation = query(Conversation::class, "threadId == $actualThreadId").first().find()
                    
                    if (existingConversation == null) {
                        // Create new conversation
                        copyToRealm(Conversation().apply {
                            this.threadId = actualThreadId
                            this.address = address
                            this.snippet = messageBody
                            this.date = timestamp
                            this.unreadCount = 0
                        })
                    } else {
                        // Update existing conversation
                        findLatest(existingConversation)?.apply {
                            this.snippet = messageBody
                            this.date = timestamp
                        }
                    }
                    
                    // Create message in Realm with PENDING status
                    copyToRealm(Message().apply {
                        this.id = messageId
                        this.threadId = actualThreadId
                        this.address = address
                        this.body = messageBody
                        this.date = timestamp
                        this.type = MessageType.SENT
                        this.status = MessageStatus.PENDING
                        this.read = true
                        this.starred = false
                        this.messagePartCount = 1
                    })
                    Log.d(TAG, "Message stored in Realm with PENDING status - MessageId: $messageId")
                }
                
                // STEP 2: Send SMS using SmsManager
                Log.d(TAG, "STEP 2: Sending SMS via SmsManager")
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(messageBody)
                Log.d(TAG, "Message divided into ${parts.size} parts")
                
                // Create pending intents for sent and delivered status
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    messageId.toInt(),
                    Intent("com.quizangomedia.messages.SMS_SENT").apply {
                        putExtra("message_id", messageId)
                        putExtra("thread_id", actualThreadId)
                        putExtra("address", address)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    messageId.toInt(),
                    Intent("com.quizangomedia.messages.SMS_DELIVERED").apply {
                        putExtra("message_id", messageId)
                        putExtra("thread_id", actualThreadId)
                        putExtra("address", address)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Send SMS
                if (parts.size == 1) {
                    Log.d(TAG, "Sending single-part SMS to $address")
                    smsManager.sendTextMessage(
                        address,
                        null,
                        messageBody,
                        sentIntent,
                        deliveredIntent
                    )
                    Log.d(TAG, "sendTextMessage called successfully")
                } else {
                    Log.d(TAG, "Sending multipart SMS (${parts.size} parts) to $address")
                    val sentIntents = ArrayList<PendingIntent>()
                    val deliveredIntents = ArrayList<PendingIntent>()
                    
                    for (i in parts.indices) {
                        sentIntents.add(
                            PendingIntent.getBroadcast(
                                context,
                                (messageId + i).toInt(),
                                Intent("com.quizangomedia.messages.SMS_SENT").apply {
                                    putExtra("message_id", messageId)
                                    putExtra("thread_id", actualThreadId)
                                    putExtra("address", address)
                                    putExtra("part_index", i)
                                },
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                        deliveredIntents.add(
                            PendingIntent.getBroadcast(
                                context,
                                (messageId + i).toInt(),
                                Intent("com.quizangomedia.messages.SMS_DELIVERED").apply {
                                    putExtra("message_id", messageId)
                                    putExtra("thread_id", actualThreadId)
                                    putExtra("address", address)
                                    putExtra("part_index", i)
                                },
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                    }
                    
                    smsManager.sendMultipartTextMessage(
                        address,
                        null,
                        parts,
                        sentIntents,
                        deliveredIntents
                    )
                    Log.d(TAG, "sendMultipartTextMessage called successfully")
                }
                
                // STEP 3: Save message to SMS database (required for default SMS app)
                // This ensures the message appears in the system SMS database
                Log.d(TAG, "STEP 3: Saving message to SMS database")
                saveMessageToSmsDatabase(context, actualThreadId, address, messageBody)
                
                // STEP 4: Reload messages after a short delay to ensure database is committed
                Log.d(TAG, "STEP 4: Waiting 300ms before reloading messages")
                delay(300)
                Log.d(TAG, "Reloading messages for threadId: $actualThreadId")
                loadMessages(actualThreadId, address)
                Log.d(TAG, "sendMessage completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendMessage", e)
                e.printStackTrace()
                // On error, update message status to FAILED
                // Only update if message was actually created in Realm
                try {
                    Log.d(TAG, "Attempting to update message status to FAILED - messageId: $messageId")
                    val errorRealm = MessagesApp.realm
                    errorRealm.writeBlocking {
                        // Find the message by ID (messageId is now accessible from outer scope)
                        val message = query(Message::class, "id == $messageId").first().find()
                        if (message != null) {
                            findLatest(message)?.status = MessageStatus.FAILED
                            Log.d(TAG, "Message status updated to FAILED - messageId: $messageId")
                        } else {
                            Log.w(TAG, "Message not found in Realm to update status - messageId: $messageId")
                        }
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error updating message status to FAILED", ex)
                    ex.printStackTrace()
                }
            }
        }
    }
    
    private fun saveMessageToSmsDatabase(context: Context, threadId: Long, address: String, body: String) {
        try {
            Log.d(TAG, "saveMessageToSmsDatabase - threadId: $threadId, address: $address")
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.THREAD_ID, threadId)
            }
            
            val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            if (uri != null) {
                Log.d(TAG, "Message saved to SMS database successfully - URI: $uri")
            } else {
                Log.e(TAG, "Failed to save message to SMS database - insert returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving message to SMS database", e)
            e.printStackTrace()
        }
    }
    
    private fun getSignature(): String {
        val prefs = MessagesApp.instance.getSharedPreferences("signature", Context.MODE_PRIVATE)
        return prefs.getString("signature_text", "") ?: ""
    }
}

