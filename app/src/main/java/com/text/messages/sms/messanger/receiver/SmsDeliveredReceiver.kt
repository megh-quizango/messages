package com.text.messages.sms.messanger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliveredReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsDeliveredReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("message_id", -1)
        
        Log.d(TAG, "onReceive - messageId: $messageId")
        
        if (messageId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                updateMessageStatus(messageId)
            }
        } else {
            Log.w(TAG, "Received SMS_DELIVERED intent without message_id")
        }
    }
    
    private suspend fun updateMessageStatus(messageId: Long) {
        try {
            Log.d(TAG, "updateMessageStatus - messageId: $messageId")
            val database = MessagesApp.database
            val messageDao = database.messageDao()
            
            val message = messageDao.getMessageById(messageId)
            if (message != null) {
                messageDao.updateMessage(message.copy(status = MessageStatus.DELIVERED))
                Log.d(TAG, "Message status updated to DELIVERED - messageId: $messageId")
            } else {
                Log.w(TAG, "Message not found in database - messageId: $messageId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message status to DELIVERED", e)
            e.printStackTrace()
        }
    }
}

