package com.quizangomedia.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.quizangomedia.messages.service.MmsReceiverService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MmsReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive - action: ${intent.action}")
        
        if (Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION != intent.action) {
            Log.w(TAG, "Received intent with unexpected action: ${intent.action}")
            return
        }
        
        val mimeType = intent.type
        if (mimeType != "application/vnd.wap.mms-message") {
            Log.w(TAG, "Received WAP_PUSH with unexpected MIME type: $mimeType")
            return
        }
        
        // Extract MMS URI from intent
        val messageUri = intent.getParcelableExtra<Uri>("uri")
        
        if (messageUri == null) {
            Log.w(TAG, "No message URI in WAP_PUSH intent")
            // Try to get location URL and download
            val locationUrl = intent.getStringExtra("location")
            if (!locationUrl.isNullOrEmpty()) {
                Log.d(TAG, "Location URL found: $locationUrl, will download MMS")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        MmsReceiverService.downloadMms(context, locationUrl)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error downloading MMS", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            return
        }
        
        Log.d(TAG, "Received MMS notification - messageUri: $messageUri")
        
        // Process received MMS
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MmsReceiverService.processReceivedMms(context, messageUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing received MMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

