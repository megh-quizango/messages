package com.text.messages.sms.messanger.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.text.messages.sms.messanger.service.MmsReceiverService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MmsUpdatedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MmsUpdatedReceiver"
        const val ACTION_MMS_UPDATED = "com.text.messages.sms.messanger.MMS_UPDATED"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val uriStr = intent.getStringExtra("uri")
        
        Log.d(TAG, "onReceive - uri: $uriStr")
        
        if (uriStr.isNullOrEmpty()) {
            Log.w(TAG, "No URI provided")
            return
        }
        
        val messageUri = Uri.parse(uriStr)
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sync message from Telephony Provider
                MmsReceiverService.syncMessage(context, messageUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing MMS message", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

