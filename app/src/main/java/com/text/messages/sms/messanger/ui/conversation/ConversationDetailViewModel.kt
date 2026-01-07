package com.text.messages.sms.messanger.ui.conversation

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
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.receiver.SmsDeliveredReceiver
import com.text.messages.sms.messanger.receiver.SmsSentReceiver
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
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private var currentThreadId: Long = -1
    private var currentAddress: String = ""
    
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    fun loadMessages(threadId: Long, address: String? = null) {
        Log.d(TAG, "loadMessages called - threadId: $threadId, address: $address")
        currentThreadId = threadId
        currentAddress = address ?: ""
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
                    
                    // Update old PENDING messages to FAILED before loading
                    updateOldPendingMessagesToFailed(actualThreadId, address)
                    
                    val smsMessages = loadMessagesFromDevice(actualThreadId, address)
                    Log.d(TAG, "Loaded ${smsMessages.size} SMS messages from device")
                    
                    // Also load MMS messages from Realm (they have attachments)
                    val mmsMessages = loadMmsMessagesFromRealm(actualThreadId, address)
                    Log.d(TAG, "Loaded ${mmsMessages.size} MMS messages from Realm")
                    
                    // Merge and sort by date
                    val allMessages = (smsMessages + mmsMessages).sortedBy { it.date }
                    Log.d(TAG, "Total messages: ${allMessages.size}")
                    allMessages
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
    
    private fun loadMmsMessagesFromRealm(threadId: Long, address: String?): List<Message> {
        val messagesList = mutableListOf<Message>()
        try {
            val realm = MessagesApp.realm
            realm.writeBlocking {
                val query = if (threadId > 0) {
                    query(Message::class, "threadId == $threadId AND (mimeType != null OR attachmentPath != null)")
                } else if (!address.isNullOrEmpty()) {
                    val normalizedAddress = normalizePhoneNumber(address)
                    query(Message::class, "(mimeType != null OR attachmentPath != null)")
                } else {
                    query(Message::class, "(mimeType != null OR attachmentPath != null)")
                }
                
                val results = query.find()
                Log.d(TAG, "Found ${results.size} MMS messages in Realm")
                
                results.forEach { message ->
                    val msg = findLatest(message)
                    if (msg != null) {
                        // Filter by address if threadId is not available
                        if (threadId <= 0 && !address.isNullOrEmpty()) {
                            val normalizedMsgAddress = normalizePhoneNumber(msg.address)
                            val normalizedTargetAddress = normalizePhoneNumber(address)
                            val addressesMatch = normalizedMsgAddress == normalizedTargetAddress ||
                                normalizedMsgAddress.endsWith(normalizedTargetAddress) ||
                                normalizedTargetAddress.endsWith(normalizedMsgAddress) ||
                                (normalizedMsgAddress.length >= 10 && normalizedTargetAddress.length >= 10 &&
                                 normalizedMsgAddress.takeLast(10) == normalizedTargetAddress.takeLast(10))
                            
                            if (!addressesMatch) {
                                return@forEach
                            }
                        }
                        
                        messagesList.add(Message().apply {
                            this.id = msg.id
                            this.threadId = msg.threadId
                            this.address = msg.address
                            this.body = msg.body
                            this.date = msg.date
                            this.type = msg.type
                            this.status = msg.status
                            this.read = msg.read
                            this.starred = msg.starred
                            this.mimeType = msg.mimeType
                            this.attachmentPath = msg.attachmentPath
                            this.messagePartCount = msg.messagePartCount
                            this.otp = msg.otp
                        })
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MMS messages from Realm", e)
            e.printStackTrace()
        }
        return messagesList
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
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS
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
                    val status = try {
                        cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS))
                    } catch (e: Exception) {
                        // STATUS column might not exist on all devices/versions
                        -1
                    }
                    
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
                    
                    // Use STATUS field from SMS database if available, otherwise infer from type
                    val messageStatus = if (status != -1 && type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                        when (status) {
                            Telephony.Sms.STATUS_FAILED -> MessageStatus.FAILED
                            Telephony.Sms.STATUS_PENDING -> MessageStatus.PENDING
                            Telephony.Sms.STATUS_COMPLETE -> MessageStatus.SENT
                            else -> MessageStatus.PENDING // Default to PENDING for unknown status
                        }
                    } else {
                        when (type) {
                            Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                            Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
                            else -> MessageStatus.DELIVERED
                        }
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
    
    private fun updateOldPendingMessagesToFailed(threadId: Long, address: String?) {
        try {
            val realm = MessagesApp.realm
            val currentTime = System.currentTimeMillis()
            val twoMinutesAgo = currentTime - 120000 // 2 minutes in milliseconds
            
            realm.writeBlocking {
                val query = if (threadId > 0) {
                    query(Message::class, "threadId == $threadId AND type == ${MessageType.SENT} AND status == ${MessageStatus.PENDING} AND date < $twoMinutesAgo")
                } else if (!address.isNullOrEmpty()) {
                    val normalizedAddress = normalizePhoneNumber(address)
                    query(Message::class, "type == ${MessageType.SENT} AND status == ${MessageStatus.PENDING} AND date < $twoMinutesAgo")
                } else {
                    query(Message::class, "type == ${MessageType.SENT} AND status == ${MessageStatus.PENDING} AND date < $twoMinutesAgo")
                }
                
                val pendingMessages = query.find()
                Log.d(TAG, "Found ${pendingMessages.size} old PENDING messages to update to FAILED")
                
                pendingMessages.forEach { message ->
                    val msg = findLatest(message)
                    if (msg != null) {
                        // Filter by address if threadId is not available
                        if (threadId <= 0 && !address.isNullOrEmpty()) {
                            val normalizedMsgAddress = normalizePhoneNumber(msg.address)
                            val normalizedTargetAddress = normalizePhoneNumber(address)
                            val addressesMatch = normalizedMsgAddress == normalizedTargetAddress ||
                                normalizedMsgAddress.endsWith(normalizedTargetAddress) ||
                                normalizedTargetAddress.endsWith(normalizedMsgAddress) ||
                                (normalizedMsgAddress.length >= 10 && normalizedTargetAddress.length >= 10 &&
                                 normalizedMsgAddress.takeLast(10) == normalizedTargetAddress.takeLast(10))
                            
                            if (!addressesMatch) {
                                return@forEach
                            }
                        }
                        
                        msg.status = MessageStatus.FAILED
                        Log.d(TAG, "Updated message ${msg.id} from PENDING to FAILED (age: ${currentTime - msg.date}ms)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating old PENDING messages to FAILED", e)
            e.printStackTrace()
        }
    }
    
    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove common formatting characters for better matching
        return phoneNumber.replace(Regex("[\\s\\-\\(\\)\\+]"), "")
    }
    
    private fun copyImageToPermanentStorage(context: Context, sourceUri: android.net.Uri, messageId: Long): android.net.Uri? {
        return try {
            // Use app's private external files directory (persists across app restarts)
            val imagesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            if (imagesDir == null || !imagesDir.exists()) {
                imagesDir?.mkdirs()
                if (imagesDir == null || !imagesDir.exists()) {
                    Log.e(TAG, "Failed to create images directory")
                    return null
                }
            }
            
            val fileName = "mms_image_${messageId}.jpg"
            val destFile = java.io.File(imagesDir, fileName)
            
            // Copy the image
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                java.io.FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream from source URI")
                return null
            }
            
            // Return FileProvider URI for secure access
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error copying image to permanent storage", e)
            e.printStackTrace()
            null
        }
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
                    Intent("com.text.messages.sms.messanger.SMS_SENT").apply {
                        putExtra("message_id", messageId)
                        putExtra("thread_id", actualThreadId)
                        putExtra("address", address)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    messageId.toInt(),
                    Intent("com.text.messages.sms.messanger.SMS_DELIVERED").apply {
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
                                Intent("com.text.messages.sms.messanger.SMS_SENT").apply {
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
                                Intent("com.text.messages.sms.messanger.SMS_DELIVERED").apply {
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
    
    fun sendMMS(threadId: Long, address: String, body: String, imageUri: android.net.Uri) {
        Log.d(TAG, "sendMMS called - threadId: $threadId, address: $address, body length: ${body.length}")
        
        viewModelScope.launch {
            val messageId = System.currentTimeMillis()
            
            try {
                val context = MessagesApp.instance
                val actualThreadId = if (threadId > 0) {
                    threadId
                } else {
                    Telephony.Threads.getOrCreateThreadId(context, address)
                }
                
                // Check MMS availability
                val mmsHelper = com.text.messages.sms.messanger.util.MmsHelper
                if (!mmsHelper.isMmsServiceAvailable(context)) {
                    Log.e(TAG, "MMS service is not available")
                    _errorMessage.postValue("MMS service is not available. Please check your device settings and ensure MMS is enabled.")
                    return@launch
                }
                
                // Copy image to permanent storage location
                val permanentImageUri = copyImageToPermanentStorage(context, imageUri, messageId)
                if (permanentImageUri == null) {
                    Log.e(TAG, "Failed to copy image to permanent storage")
                    _errorMessage.postValue("Failed to save image. Please try again.")
                    return@launch
                }
                
                // Get signature (only if body is not empty)
                val signature = getSignature()
                val messageBody = if (body.isNotEmpty()) {
                    if (signature.isNotEmpty()) {
                        "$body\n$signature"
                    } else {
                        body
                    }
                } else {
                    // Allow empty body for attachment-only MMS
                    if (signature.isNotEmpty()) {
                        signature
                    } else {
                        ""
                    }
                }
                
                // Store message in Realm with PENDING status
                val realm = MessagesApp.realm
                val timestamp = System.currentTimeMillis()
                
                realm.writeBlocking {
                    // Create or update conversation
                    val existingConversation = query(Conversation::class, "threadId == $actualThreadId").first().find()
                    
                    if (existingConversation == null) {
                        copyToRealm(Conversation().apply {
                            this.threadId = actualThreadId
                            this.address = address
                            this.snippet = messageBody.ifEmpty { "Photo" }
                            this.date = timestamp
                            this.unreadCount = 0
                        })
                    } else {
                        findLatest(existingConversation)?.apply {
                            this.snippet = messageBody.ifEmpty { "Photo" }
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
                        this.mimeType = "image/*"
                        this.attachmentPath = permanentImageUri.toString()
                        this.messagePartCount = 1
                    })
                    Log.d(TAG, "Message stored in Realm with PENDING status - MessageId: $messageId")
                }
                
                // Send MMS using MmsSender service (use original URI for sending, permanent URI is stored in DB)
                val result = com.text.messages.sms.messanger.service.MmsSender.sendMms(
                    subscriptionId = -1,
                    messageId = messageId,
                    addresses = listOf(address),
                    attachments = listOf(imageUri), // Use original URI for sending
                    subject = null,
                    body = messageBody
                )
                
                result.fold(
                    onSuccess = { mmsUri ->
                        Log.d(TAG, "MMS send request submitted successfully - URI: $mmsUri")
                        // Notify main activity to refresh conversation list
                        val refreshIntent = Intent("com.text.messages.sms.messanger.MMS_SENT_REFRESH")
                        context.sendBroadcast(refreshIntent)
                        // Reload messages after delay
                        delay(500)
                        loadMessages(actualThreadId, address)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error sending MMS", error)
                        // Update message status to FAILED
                        realm.writeBlocking {
                            val message = query(Message::class, "id == $messageId").first().find()
                            if (message != null) {
                                findLatest(message)?.status = MessageStatus.FAILED
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendMMS", e)
                e.printStackTrace()
                // Update message status to FAILED
                try {
                    val realm = MessagesApp.realm
                    realm.writeBlocking {
                        val message = query(Message::class, "id == $messageId").first().find()
                        if (message != null) {
                            findLatest(message)?.status = MessageStatus.FAILED
                        }
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error updating message status to FAILED", ex)
                }
            }
        }
    }
    
    fun sendMMSWithContact(threadId: Long, address: String, body: String, vCardUri: android.net.Uri) {
        Log.d(TAG, "sendMMSWithContact called - threadId: $threadId, address: $address, body length: ${body.length}")
        
        viewModelScope.launch {
            val messageId = System.currentTimeMillis()
            
            try {
                val context = MessagesApp.instance
                val actualThreadId = if (threadId > 0) {
                    threadId
                } else {
                    Telephony.Threads.getOrCreateThreadId(context, address)
                }
                
                // Check MMS availability
                val mmsHelper = com.text.messages.sms.messanger.util.MmsHelper
                if (!mmsHelper.isMmsServiceAvailable(context)) {
                    Log.e(TAG, "MMS service is not available")
                    _errorMessage.postValue("MMS service is not available. Please check your device settings and ensure MMS is enabled.")
                    return@launch
                }
                
                // Read contact info from vCard file to include in message body
                var contactName = ""
                var contactNumber = ""
                try {
                    val vCardContent = context.contentResolver.openInputStream(vCardUri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                    if (vCardContent != null) {
                        val nameMatch = Regex("FN:([^\\r\\n]+)").find(vCardContent)
                        val telMatch = Regex("TEL:([^\\r\\n]+)").find(vCardContent)
                        contactName = nameMatch?.groupValues?.get(1) ?: ""
                        contactNumber = telMatch?.groupValues?.get(1) ?: ""
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading vCard file", e)
                }
                
                // Get signature (only if body is not empty)
                val signature = getSignature()
                val messageBody = if (body.isNotEmpty()) {
                    if (signature.isNotEmpty()) {
                        "$body\n$signature"
                    } else {
                        body
                    }
                } else {
                    // For contact card-only MMS, include vCard content in body for display
                    val vCardText = if (contactName.isNotEmpty() || contactNumber.isNotEmpty()) {
                        "BEGIN:VCARD\nVERSION:3.0\nFN:$contactName\nTEL:$contactNumber\nEND:VCARD"
                    } else {
                        ""
                    }
                    if (signature.isNotEmpty() && vCardText.isNotEmpty()) {
                        "$vCardText\n$signature"
                    } else if (vCardText.isNotEmpty()) {
                        vCardText
                    } else if (signature.isNotEmpty()) {
                        signature
                    } else {
                        ""
                    }
                }
                
                // Store message in Realm with PENDING status
                val realm = MessagesApp.realm
                val timestamp = System.currentTimeMillis()
                
                realm.writeBlocking {
                    // Create or update conversation
                    val existingConversation = query(Conversation::class, "threadId == $actualThreadId").first().find()
                    
                    if (existingConversation == null) {
                        copyToRealm(Conversation().apply {
                            this.threadId = actualThreadId
                            this.address = address
                            this.snippet = messageBody.ifEmpty { "Contact card" }
                            this.date = timestamp
                            this.unreadCount = 0
                        })
                    } else {
                        findLatest(existingConversation)?.apply {
                            this.snippet = messageBody.ifEmpty { "Contact card" }
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
                        this.mimeType = "text/x-vCard"
                        this.attachmentPath = vCardUri.toString()
                        this.messagePartCount = 1
                    })
                    Log.d(TAG, "Message stored in Realm with PENDING status - MessageId: $messageId")
                }
                
                // Send MMS using MmsSender service
                val result = com.text.messages.sms.messanger.service.MmsSender.sendMms(
                    subscriptionId = -1,
                    messageId = messageId,
                    addresses = listOf(address),
                    attachments = listOf(vCardUri),
                    subject = null,
                    body = messageBody
                )
                
                result.fold(
                    onSuccess = { mmsUri ->
                        Log.d(TAG, "MMS send request submitted successfully - URI: $mmsUri")
                        // Notify main activity to refresh conversation list
                        val refreshIntent = Intent("com.text.messages.sms.messanger.MMS_SENT_REFRESH")
                        context.sendBroadcast(refreshIntent)
                        // Reload messages after delay
                        delay(500)
                        loadMessages(actualThreadId, address)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error sending MMS", error)
                        // Update message status to FAILED
                        realm.writeBlocking {
                            val message = query(Message::class, "id == $messageId").first().find()
                            if (message != null) {
                                findLatest(message)?.status = MessageStatus.FAILED
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendMMSWithContact", e)
                e.printStackTrace()
                // Update message status to FAILED
                try {
                    val realm = MessagesApp.realm
                    realm.writeBlocking {
                        val message = query(Message::class, "id == $messageId").first().find()
                        if (message != null) {
                            findLatest(message)?.status = MessageStatus.FAILED
                        }
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error updating message status to FAILED", ex)
                }
            }
        }
    }
    
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    val message = query(Message::class, "id == $messageId").first().find()
                    if (message != null) {
                        findLatest(message)?.let {
                            delete(it)
                            Log.d(TAG, "Message deleted - messageId: $messageId")
                        }
                    }
                }
                // Reload messages after deletion
                if (currentThreadId > 0) {
                    loadMessages(currentThreadId, currentAddress.takeIf { it.isNotEmpty() })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting message", e)
            }
        }
    }
}

