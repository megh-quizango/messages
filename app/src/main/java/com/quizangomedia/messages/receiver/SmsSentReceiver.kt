package com.quizangomedia.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsSentReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("message_id", -1)
        val resultCode = resultCode
        
        if (messageId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                updateMessageStatus(messageId, resultCode == android.app.Activity.RESULT_OK)
            }
        }
    }
    
    private suspend fun updateMessageStatus(messageId: Long, success: Boolean) {
        try {
            val realm = MessagesApp.realm
            realm.writeBlocking {
                val message = query(Message::class, "id == $messageId").first().find()
                message?.let {
                    findLatest(it)?.status = if (success) {
                        MessageStatus.SENT
                    } else {
                        MessageStatus.FAILED
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

