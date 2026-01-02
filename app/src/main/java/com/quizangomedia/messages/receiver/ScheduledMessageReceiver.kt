package com.quizangomedia.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.widget.Toast

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address") ?: return
        val message = intent.getStringExtra("message") ?: return

        try {
            // Get signature from SharedPreferences and append to message
            val prefs = context.getSharedPreferences("signature", Context.MODE_PRIVATE)
            val signature = prefs.getString("signature_text", "") ?: ""
            val messageBody = if (signature.isNotEmpty()) {
                "$message\n$signature"
            } else {
                message
            }
            
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(
                address,
                null,
                messageBody,
                null,
                null
            )
            
            // Show notification or toast
            Toast.makeText(context, "Scheduled message sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to send scheduled message", Toast.LENGTH_SHORT).show()
        }
    }
}

