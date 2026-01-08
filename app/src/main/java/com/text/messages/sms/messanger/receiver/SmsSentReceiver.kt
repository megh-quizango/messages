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

class SmsSentReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsSentReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("message_id", -1)
        val resultCode = resultCode
        val success = resultCode == android.app.Activity.RESULT_OK
        
        Log.d(TAG, "onReceive - messageId: $messageId, resultCode: $resultCode, success: $success")
        
        if (messageId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                updateMessageStatus(messageId, success)
            }
        } else {
            Log.w(TAG, "Received SMS_SENT intent without message_id")
        }
    }
    
    private suspend fun updateMessageStatus(messageId: Long, success: Boolean) {
        try {
            Log.d(TAG, "updateMessageStatus - messageId: $messageId, success: $success")
            val database = MessagesApp.database
            val messageDao = database.messageDao()
            
            val message = messageDao.getMessageById(messageId)
            if (message != null) {
                val newStatus = if (success) {
                    MessageStatus.SENT
                } else {
                    MessageStatus.FAILED
                }
                val updatedDate = if (success) {
                    System.currentTimeMillis()
                } else {
                    message.date
                }
                
                messageDao.updateMessage(
                    message.copy(
                        status = newStatus,
                        date = updatedDate
                    )
                )
                Log.d(TAG, "Message status updated - messageId: $messageId, new status: ${if (success) "SENT" else "FAILED"}")
            } else {
                Log.w(TAG, "Message not found in database - messageId: $messageId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message status", e)
            e.printStackTrace()
        }
    }
}

