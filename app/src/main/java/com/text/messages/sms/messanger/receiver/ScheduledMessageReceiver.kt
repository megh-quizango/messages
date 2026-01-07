package com.text.messages.sms.messanger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledMessageReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledMessageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address") ?: return
        val messageBody = intent.getStringExtra("message") ?: return
        val threadId = intent.getLongExtra("thread_id", -1)

        try {
            // Message body already includes signature (added in ConversationDetailActivity)
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(
                address,
                null,
                messageBody,
                null,
                null
            )
            
            // Save message to SMS database so it appears in conversation detail
            // This will trigger the ContentObserver to reload messages
            CoroutineScope(Dispatchers.IO).launch {
                saveMessageToSmsDatabase(context, threadId, address, messageBody)
            }
            
            // Show notification or toast
            Toast.makeText(context, "Scheduled message sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending scheduled message", e)
            e.printStackTrace()
            Toast.makeText(context, "Failed to send scheduled message", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveMessageToSmsDatabase(context: Context, threadId: Long, address: String, body: String) {
        try {
            Log.d(TAG, "saveMessageToSmsDatabase - threadId: $threadId, address: $address")
            
            // Get or create thread ID if not provided
            val actualThreadId = if (threadId > 0) {
                threadId
            } else {
                Telephony.Threads.getOrCreateThreadId(context, address)
            }
            
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.THREAD_ID, actualThreadId)
            }
            
            val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            if (uri != null) {
                Log.d(TAG, "Scheduled message saved to SMS database successfully - URI: $uri")
            } else {
                Log.e(TAG, "Failed to save scheduled message to SMS database - insert returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scheduled message to SMS database", e)
            e.printStackTrace()
        }
    }
}

