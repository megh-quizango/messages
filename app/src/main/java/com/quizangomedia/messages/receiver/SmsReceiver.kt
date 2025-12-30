package com.quizangomedia.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageStatus
import com.quizangomedia.messages.data.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION == intent.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            CoroutineScope(Dispatchers.IO).launch {
                processSmsMessages(context, messages)
            }
        }
    }
    
    private suspend fun processSmsMessages(context: Context, messages: Array<SmsMessage>) {
        try {
            val realm = MessagesApp.realm
            
            messages.forEach { smsMessage ->
                val address = smsMessage.originatingAddress ?: return@forEach
                val body = smsMessage.messageBody ?: ""
                val timestamp = smsMessage.timestampMillis
                
                realm.writeBlocking {
                    // Get or create conversation
                    val threadId = getOrCreateThreadId(address)
                    val existingConversation = query(Conversation::class, "threadId == $threadId")
                        .first()
                        .find()
                    
                    if (existingConversation == null) {
                        copyToRealm(Conversation().apply {
                            this.threadId = threadId
                            this.address = address
                            this.snippet = body
                            this.date = timestamp
                            this.unreadCount = 1
                        })
                    } else {
                        findLatest(existingConversation)?.apply {
                            this.snippet = body
                            this.date = timestamp
                            this.unreadCount = this.unreadCount + 1
                        }
                    }
                    
                    // Create message
                    copyToRealm(Message().apply {
                        this.id = System.currentTimeMillis()
                        this.threadId = threadId
                        this.address = address
                        this.body = body
                        this.date = timestamp
                        this.type = MessageType.INBOX
                        this.status = MessageStatus.RECEIVED
                        this.read = false
                    })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getOrCreateThreadId(address: String): Long {
        // Simple thread ID generation based on address
        // In production, use Telephony.Threads.getOrCreateThreadId
        return address.hashCode().toLong()
    }
}

