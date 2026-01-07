package com.text.messages.sms.messanger.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.ui.main.DeletedConversationData
import com.text.messages.sms.messanger.ui.notifications.ButtonAction
import com.text.messages.sms.messanger.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras?.keySet()?.joinToString()}")
        
        val actionName = intent.getStringExtra("action")
        if (actionName == null) {
            Log.e(TAG, "No action extra found in intent")
            return
        }
        
        val threadId = intent.getLongExtra("threadId", -1)
        val address = intent.getStringExtra("address")
        if (address == null) {
            Log.e(TAG, "No address extra found in intent")
            return
        }
        
        val messageBody = intent.getStringExtra("messageBody") ?: ""
        val contactName = intent.getStringExtra("contactName")
        
        Log.d(TAG, "Action received: $actionName, threadId: $threadId, address: $address")
        
        val action = try {
            ButtonAction.valueOf(actionName)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid action: $actionName", e)
            return
        }
        
        Log.d(TAG, "Processing action: ${action.name}")
        
        when (action) {
            ButtonAction.ARCHIVE -> handleArchive(context, threadId)
            ButtonAction.DELETE -> handleDelete(context, threadId, address, contactName)
            ButtonAction.BLOCK -> handleBlock(context, threadId, address)
            ButtonAction.CALL -> handleCall(context, address)
            ButtonAction.MARK_AS_READ -> handleMarkAsRead(context, threadId)
            ButtonAction.REPLY -> {
                // Check if this is an inline reply (from RemoteInput)
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                Log.d(TAG, "REPLY action - remoteInput is null: ${remoteInput == null}")
                
                if (remoteInput != null) {
                    val replyText = remoteInput.getCharSequence("reply_text")?.toString()
                    Log.d(TAG, "Inline reply detected - text: '$replyText'")
                    if (!replyText.isNullOrEmpty()) {
                        handleInlineReply(context, threadId, address, replyText)
                    } else {
                        Log.w(TAG, "Inline reply text is empty")
                    }
                } else {
                    // This should not happen if REPLY is only in standard action with RemoteInput
                    // But handle it gracefully by opening conversation
                    Log.w(TAG, "REPLY action without RemoteInput - opening conversation (this shouldn't happen)")
                    handleReply(context, threadId, address)
                }
            }
            ButtonAction.COPY_OTP -> {
                // Only copy OTP if message contains OTP
                if (isOTPMessage(messageBody)) {
                    handleCopyOTP(context, messageBody)
                } else {
                    Toast.makeText(context, "This message does not contain an OTP", Toast.LENGTH_SHORT).show()
                }
            }
            ButtonAction.NONE -> {}
        }
        
        // Cancel notification after action (except for reply which might need to stay open)
        if (action != ButtonAction.REPLY || RemoteInput.getResultsFromIntent(intent) == null) {
            NotificationHelper.cancelNotification(context, threadId)
        }
    }
    
    private fun handleArchive(context: Context, threadId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    val conversation = query(Conversation::class, "threadId == $threadId").first().find()
                    if (conversation != null) {
                        findLatest(conversation)?.apply {
                            this.archived = true
                        }
                        Log.d(TAG, "Conversation archived: threadId=$threadId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error archiving conversation", e)
            }
        }
    }
    
    private fun handleDelete(context: Context, threadId: Long, address: String, contactName: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val realm = MessagesApp.realm
                
                // Get conversation data before deleting
                var conversationData: Conversation? = null
                realm.writeBlocking {
                    conversationData = query(Conversation::class, "threadId == $threadId").first().find()
                }
                
                if (conversationData != null) {
                    // Save to recycle bin
                    val deletedData = DeletedConversationData(
                        threadId = threadId,
                        address = address,
                        contactName = contactName,
                        snippet = conversationData!!.snippet,
                        date = conversationData!!.date,
                        unreadCount = conversationData!!.unreadCount,
                        deletedAt = System.currentTimeMillis()
                    )
                    
                    saveToRecycleBin(context, deletedData)
                    
                    // Delete from system SMS database
                    val deleted = context.contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${Telephony.Sms.THREAD_ID} = ?",
                        arrayOf(threadId.toString())
                    )
                    Log.d(TAG, "Deleted $deleted messages from system database for threadId=$threadId")
                    
                    // Delete from Realm
                    realm.writeBlocking {
                        val conversation = query(Conversation::class, "threadId == $threadId").first().find()
                        if (conversation != null) {
                            delete(conversation)
                        }
                        
                        val messages = query(Message::class, "threadId == $threadId").find()
                        messages.forEach { message ->
                            delete(message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting conversation", e)
            }
        }
    }
    
    private fun saveToRecycleBin(context: Context, deletedData: DeletedConversationData) {
        try {
            val prefs = context.getSharedPreferences("recycle_bin", Context.MODE_PRIVATE)
            val gson = Gson()
            val existingJson = prefs.getString("deleted_conversations", null)
            val type = object : TypeToken<List<DeletedConversationData>>() {}.type
            val deletedList = if (existingJson != null) {
                gson.fromJson<List<DeletedConversationData>>(existingJson, type).toMutableList()
            } else {
                mutableListOf()
            }
            
            // Remove if already exists (update)
            deletedList.removeAll { it.threadId == deletedData.threadId }
            deletedList.add(deletedData)
            
            val updatedJson = gson.toJson(deletedList)
            prefs.edit().putString("deleted_conversations", updatedJson).apply()
            Log.d(TAG, "Saved conversation to recycle bin: threadId=${deletedData.threadId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to recycle bin", e)
        }
    }
    
    private fun handleBlock(context: Context, threadId: Long, address: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Save to blocked conversations storage
                com.text.messages.sms.messanger.util.BlockedConversationStorage.addThreadId(context, threadId)
                
                // Update Realm conversation as blocked
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    val conversation = query(Conversation::class, "threadId == $threadId").first().find()
                    if (conversation != null) {
                        findLatest(conversation)?.apply {
                            this.blocked = true
                        }
                        Log.d(TAG, "Conversation blocked: threadId=$threadId, address=$address")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking conversation", e)
            }
        }
    }
    
    private fun handleCall(context: Context, address: String) {
        try {
            Log.d(TAG, "handleCall called for address: $address")
            
            // Clean and normalize phone number
            val cleanAddress = address.trim().replace(" ", "").replace("-", "")
            Log.d(TAG, "Cleaned phone number: $cleanAddress")
            
            // Check if CALL_PHONE permission is granted
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CALL_PHONE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "CALL_PHONE permission granted: $hasPermission")
            
            // Use Handler to post on main thread - some Android versions require this
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val callUri = android.net.Uri.parse("tel:$cleanAddress")
                    Log.d(TAG, "Call URI: $callUri")
                    
                    // Use ACTION_DIAL to open dialer with pre-filled number
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = callUri
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    
                    // Verify the intent can be resolved
                    val resolveInfo = context.packageManager.resolveActivity(dialIntent, 0)
                    if (resolveInfo != null) {
                        Log.d(TAG, "Dial intent resolved, starting activity")
                        Log.d(TAG, "Resolved activity: ${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}")
                        context.startActivity(dialIntent)
                        Log.d(TAG, "Dial activity started for: $cleanAddress")
                    } else {
                        Log.e(TAG, "No activity found to handle ACTION_DIAL")
                        android.widget.Toast.makeText(context, "No dialer app found", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.e(TAG, "ActivityNotFoundException - no app to handle dial", e)
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "No dialer app found", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in handleCall on main thread", e)
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "Unable to open dialer: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException for call, trying dialer", e)
            e.printStackTrace()
            // Fallback to dialer
            try {
                val cleanAddress = address.trim().replace(" ", "").replace("-", "")
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:$cleanAddress")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened dialer as fallback after SecurityException")
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening dialer", e2)
                e2.printStackTrace()
                Toast.makeText(context, "Unable to make call", Toast.LENGTH_SHORT).show()
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "ActivityNotFoundException - no app to handle call", e)
            e.printStackTrace()
            Toast.makeText(context, "No app found to make calls", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            e.printStackTrace()
            Toast.makeText(context, "Unable to make call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleMarkAsRead(context: Context, threadId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Mark as read in system SMS database
                val values = android.content.ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                }
                val updated = context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString())
                )
                Log.d(TAG, "Marked $updated messages as read in system database for threadId=$threadId")
                
                // Update Realm
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    val conversation = query(Conversation::class, "threadId == $threadId").first().find()
                    if (conversation != null) {
                        findLatest(conversation)?.apply {
                            this.unreadCount = 0
                        }
                    }
                    
                    val messages = query(com.text.messages.sms.messanger.data.model.Message::class, "threadId == $threadId AND read == false").find()
                    messages.forEach { message ->
                        findLatest(message)?.apply {
                            this.read = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking as read", e)
            }
        }
    }
    
    private fun handleReply(context: Context, threadId: Long, address: String) {
        try {
            Log.d(TAG, "handleReply called - threadId: $threadId, address: $address")
            val intent = Intent(context, com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity::class.java).apply {
                putExtra("threadId", threadId)
                putExtra("address", address)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened ConversationDetailActivity for reply")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening reply", e)
            e.printStackTrace()
            Toast.makeText(context, "Unable to open conversation", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleInlineReply(context: Context, threadId: Long, address: String, replyText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "handleInlineReply: Starting - threadId=$threadId, address=$address, text='$replyText'")
                
                val messageId = System.currentTimeMillis()
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(replyText)
                Log.d(TAG, "Message divided into ${parts.size} parts")
                
                val sentIntents = ArrayList(parts.mapIndexed { index, _ ->
                    PendingIntent.getBroadcast(
                        context,
                        (threadId.toInt() * 1000 + index),
                        Intent("com.text.messages.sms.messanger.SMS_SENT").apply {
                            putExtra("message_id", messageId)
                            setPackage(context.packageName)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                })
                
                val deliveredIntents = ArrayList(parts.mapIndexed { index, _ ->
                    PendingIntent.getBroadcast(
                        context,
                        (threadId.toInt() * 2000 + index),
                        Intent("com.text.messages.sms.messanger.SMS_DELIVERED").apply {
                            putExtra("message_id", messageId)
                            setPackage(context.packageName)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                })
                
                if (parts.size == 1) {
                    Log.d(TAG, "Sending single-part SMS")
                    smsManager.sendTextMessage(
                        address,
                        null,
                        replyText,
                        sentIntents[0],
                        deliveredIntents[0]
                    )
                    Log.d(TAG, "sendTextMessage called")
                } else {
                    Log.d(TAG, "Sending multipart SMS (${parts.size} parts)")
                    smsManager.sendMultipartTextMessage(
                        address,
                        null,
                        parts,
                        sentIntents,
                        deliveredIntents
                    )
                    Log.d(TAG, "sendMultipartTextMessage called")
                }
                
                // Save message to Realm and system database
                // Use the same messageId that was used for sending
                val actualThreadId = if (threadId > 0) {
                    threadId
                } else {
                    Telephony.Threads.getOrCreateThreadId(context, address)
                }
                val timestamp = System.currentTimeMillis()
                
                // Save to system SMS database first
                try {
                    val values = android.content.ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, replyText)
                        put(Telephony.Sms.DATE, timestamp)
                        put(Telephony.Sms.READ, 1) // Read by default for sent messages
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        put(Telephony.Sms.THREAD_ID, actualThreadId)
                        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                    }
                    
                    val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    Log.d(TAG, "Reply message saved to system SMS database: $uri")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving reply to system SMS database", e)
                }
                
                // Save to Realm
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    // Update conversation
                    val conversation = query(Conversation::class, "threadId == $actualThreadId").first().find()
                    if (conversation != null) {
                        findLatest(conversation)?.apply {
                            this.snippet = replyText
                            this.date = timestamp
                        }
                    } else {
                        // Create conversation if it doesn't exist
                        copyToRealm(Conversation().apply {
                            this.threadId = actualThreadId
                            this.address = address
                            this.snippet = replyText
                            this.date = timestamp
                            this.unreadCount = 0
                        })
                    }
                    
                    // Save message
                    copyToRealm(Message().apply {
                        this.id = messageId
                        this.threadId = actualThreadId
                        this.address = address
                        this.body = replyText
                        this.date = timestamp
                        this.type = MessageType.SENT
                        this.status = MessageStatus.PENDING
                        this.read = true
                    })
                }
                
                Log.d(TAG, "Inline reply sent: threadId=$threadId, address=$address, messageId=$messageId")
                
                // Show toast to confirm message was sent
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                }
                
                // Cancel notification after sending
                NotificationHelper.cancelNotification(context, threadId)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending inline reply", e)
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleCopyOTP(context: Context, messageBody: String) {
        try {
            // Extract OTP from message
            val otp = extractOTP(messageBody)
            if (otp != null) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "OTP copied: $otp", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "OTP copied: $otp")
            } else {
                Toast.makeText(context, "No OTP found in message", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying OTP", e)
            Toast.makeText(context, "Error copying OTP", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun isOTPMessage(messageBody: String): Boolean {
        val bodyLower = messageBody.lowercase()
        return bodyLower.contains("otp") || 
               bodyLower.contains("one time password") || 
               bodyLower.contains("verification code") ||
               bodyLower.contains("verification") ||
               extractOTP(messageBody) != null
    }
    
    private fun extractOTP(messageBody: String): String? {
        // Common OTP patterns
        val patterns = listOf(
            Pattern.compile("OTP[\\s:]*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("code[\\s:]*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("verification[\\s:]*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9]{4,8})[\\s]*is[\\s]*your", Pattern.CASE_INSENSITIVE),
            Pattern.compile("your[\\s]*code[\\s:]*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([0-9]{4,8})\\b") // 4-8 digit number as last resort
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(messageBody)
            if (matcher.find()) {
                val otp = if (matcher.groupCount() > 0) {
                    matcher.group(1)
                } else {
                    matcher.group(0)
                }
                if (otp != null && otp.length in 4..8) {
                    return otp
                }
            }
        }
        
        return null
    }
}

