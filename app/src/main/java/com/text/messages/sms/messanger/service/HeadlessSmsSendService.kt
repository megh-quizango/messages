package com.text.messages.sms.messanger.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

/**
 * Service required for an app to be eligible as a default SMS app.
 * Handles "Respond via SMS" functionality from the phone dialer.
 */
class HeadlessSmsSendService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.action == "android.intent.action.RESPOND_VIA_MESSAGE") {
                val uri = it.data
                val message = it.getStringExtra(Intent.EXTRA_TEXT)
                
                if (uri != null && message != null) {
                    val phoneNumber = uri.schemeSpecificPart
                    sendSms(phoneNumber, message)
                }
            }
        }
        
        stopSelf(startId)
        return START_NOT_STICKY
    }
    
    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                val sentIntents = ArrayList<android.app.PendingIntent>()
                val deliveredIntents = ArrayList<android.app.PendingIntent>()
                
                for (i in parts.indices) {
                    sentIntents.add(
                        android.app.PendingIntent.getBroadcast(
                            this,
                            0,
                            Intent("com.text.messages.sms.messanger.SMS_SENT"),
                            android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    deliveredIntents.add(
                        android.app.PendingIntent.getBroadcast(
                            this,
                            0,
                            Intent("com.text.messages.sms.messanger.SMS_DELIVERED"),
                            android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents)
            }
        } catch (e: Exception) {
            Log.e("HeadlessSmsSendService", "Error sending SMS", e)
        }
    }
}

