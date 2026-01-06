package com.quizangomedia.messages.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.quizangomedia.messages.service.MmsReceiverService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MmsReceivedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MmsReceivedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageUri = intent.getParcelableExtra<Uri>("uri")
        
        Log.d(TAG, "onReceive - messageUri: $messageUri")
        
        if (messageUri == null) {
            Log.w(TAG, "No message URI provided")
            return
        }
        
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Process received MMS
                MmsReceiverService.processReceivedMms(context, messageUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing received MMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

