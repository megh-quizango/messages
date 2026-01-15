package com.text.messages.sms.messanger.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.util.OtpHelper
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
            val database = MessagesApp.database
            val messageDao = database.messageDao()
            val conversationDao = database.conversationDao()
            
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
                
                // Check if message already exists (avoid duplicates)
                val existingMessage = messageDao.findMessageByThreadAndTime(
                    threadId = threadId,
                    address = address,
                    startTime = timestamp - 1000,
                    endTime = timestamp + 1000
                )
                
                // Skip if message already exists
                if (existingMessage != null) {
                    Log.d(TAG, "Message already exists in database, skipping duplicate. Address: $address, Timestamp: $timestamp")
                    return@forEach
                }
                
                // Generate unique message ID
                val messageId = System.currentTimeMillis()
                Log.d(TAG, "Storing message in database - MessageId: $messageId, ThreadId: $threadId")
                
                // Create or update conversation
                val existingConversation = conversationDao.getConversationByThreadId(threadId)

                // Detect and extract OTP from message FIRST
                val detectedOtp = if (OtpHelper.isOTPMessage(body)) {
                    OtpHelper.extractOTP(body)
                } else {
                    null
                }

                if (existingConversation == null) {
                    // Create new conversation with OTP if detected
                    Log.d(TAG, "Creating new conversation for threadId: $threadId, OTP: $detectedOtp")
                    conversationDao.insertConversation(
                        Conversation(
                            threadId = threadId,
                            address = address,
                            snippet = body,
                            date = timestamp,
                            unreadCount = 1,
                            lastOtp = detectedOtp
                        )
                    )
                } else {
                    // Update existing conversation with snippet and OTP if detected
                    Log.d(TAG, "Updating existing conversation for threadId: $threadId, OTP: $detectedOtp")
                    if (detectedOtp != null) {
                        // Update with new OTP
                        conversationDao.updateConversationSnippetWithOtp(threadId, body, timestamp, detectedOtp)
                    } else {
                        // Keep existing OTP (don't overwrite with null)
                        conversationDao.updateConversationSnippet(threadId, body, timestamp)
                    }
                    val currentUnread = existingConversation.unreadCount
                    conversationDao.updateUnreadCount(threadId, currentUnread + 1)
                }
                
                // Create message in database
                messageDao.insertMessage(
                    Message(
                        id = messageId,
                        threadId = threadId,
                        address = address,
                        body = body,
                        date = timestamp,
                        type = MessageType.INBOX,
                        status = MessageStatus.RECEIVED,
                        read = false,
                        starred = false,
                        messagePartCount = 1,
                        otp = detectedOtp
                    )
                )
                Log.d(TAG, "Message stored successfully in database - MessageId: $messageId")
                
                // Save message to system SMS database so it appears in queries
                saveMessageToSmsDatabase(context, threadId, address, body, timestamp)
                
                // Show notification for new message
                com.text.messages.sms.messanger.util.NotificationHelper.showNotification(
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

