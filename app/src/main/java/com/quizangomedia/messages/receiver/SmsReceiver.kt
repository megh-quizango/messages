package com.quizangomedia.messages.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageStatus
import com.quizangomedia.messages.data.model.MessageType
import com.quizangomedia.messages.util.OtpHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION == intent.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Log.d(TAG, "SMS_DELIVER_ACTION received, message count: ${messages.size}")
            
            CoroutineScope(Dispatchers.IO).launch {
                processSmsMessages(context, messages)
            }
        } else {
            Log.w(TAG, "Received intent with unexpected action: ${intent.action}")
        }
    }
    
    private suspend fun processSmsMessages(context: Context, messages: Array<SmsMessage>) {
        Log.d(TAG, "processSmsMessages started, processing ${messages.size} messages")
        try {
            val realm = MessagesApp.realm
            
            messages.forEach { smsMessage ->
                val address = smsMessage.originatingAddress ?: run {
                    Log.w(TAG, "SMS message has no originating address, skipping")
                    return@forEach
                }
                val body = smsMessage.messageBody ?: ""
                val timestamp = smsMessage.timestampMillis
                
                Log.d(TAG, "Processing SMS - Address: $address, Body length: ${body.length}, Timestamp: $timestamp")
                
                // Get or create thread ID using Telephony API
                val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
                Log.d(TAG, "Thread ID for address $address: $threadId")
                
                // Check if message already exists (avoid duplicates) - check before writeBlocking
                var messageExists = false
                realm.writeBlocking {
                    val existingMessage = query(Message::class, "threadId == $threadId AND address == '$address' AND date >= ${timestamp - 1000} AND date <= ${timestamp + 1000}").first().find()
                    messageExists = existingMessage != null
                }
                
                // Skip if message already exists
                if (messageExists) {
                    Log.d(TAG, "Message already exists in Realm, skipping duplicate. Address: $address, Timestamp: $timestamp")
                    return@forEach
                }
                
                // Store message in Realm database
                realm.writeBlocking {
                    // Generate unique message ID
                    val messageId = System.currentTimeMillis()
                    Log.d(TAG, "Storing message in Realm - MessageId: $messageId, ThreadId: $threadId")
                    
                    // Create or update conversation
                    val existingConversation = query(Conversation::class, "threadId == $threadId").first().find()
                    
                    if (existingConversation == null) {
                        // Create new conversation
                        Log.d(TAG, "Creating new conversation for threadId: $threadId")
                        copyToRealm(Conversation().apply {
                            this.threadId = threadId
                            this.address = address
                            this.snippet = body
                            this.date = timestamp
                            this.unreadCount = 1
                        })
                    } else {
                        // Update existing conversation
                        Log.d(TAG, "Updating existing conversation for threadId: $threadId")
                        findLatest(existingConversation)?.apply {
                            this.snippet = body
                            this.date = timestamp
                            this.unreadCount = this.unreadCount + 1
                        }
                    }
                    
                    // Detect and extract OTP from message
                    val detectedOtp = if (OtpHelper.isOTPMessage(body)) {
                        OtpHelper.extractOTP(body)
                    } else {
                        null
                    }
                    
                    // Create message in Realm
                    copyToRealm(Message().apply {
                        this.id = messageId
                        this.threadId = threadId
                        this.address = address
                        this.body = body
                        this.date = timestamp
                        this.type = MessageType.INBOX
                        this.status = MessageStatus.RECEIVED
                        this.read = false
                        this.starred = false
                        this.messagePartCount = 1
                        this.otp = detectedOtp
                    })
                    Log.d(TAG, "Message stored successfully in Realm - MessageId: $messageId")
                }
                
                // Save message to system SMS database so it appears in queries
                saveMessageToSmsDatabase(context, threadId, address, body, timestamp)
                
                // Show notification for new message
                com.quizangomedia.messages.util.NotificationHelper.showNotification(
                    context = context,
                    threadId = threadId,
                    address = address,
                    messageBody = body,
                    timestamp = timestamp
                )
                
                Log.d(TAG, "SMS processing completed for address: $address")
            }
            Log.d(TAG, "All SMS messages processed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS messages", e)
            e.printStackTrace()
        }
    }
    
    private fun saveMessageToSmsDatabase(context: Context, threadId: Long, address: String, body: String, timestamp: Long) {
        try {
            Log.d(TAG, "saveMessageToSmsDatabase - threadId: $threadId, address: $address, timestamp: $timestamp")
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0) // Unread by default
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.THREAD_ID, threadId)
            }
            
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            if (uri != null) {
                Log.d(TAG, "Incoming message saved to SMS database successfully - URI: $uri")
            } else {
                Log.e(TAG, "Failed to save incoming message to SMS database - insert returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving incoming message to SMS database", e)
            e.printStackTrace()
        }
    }
}

