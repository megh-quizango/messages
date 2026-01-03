package com.quizangomedia.messages.observer

import android.database.ContentObserver
import android.os.Handler
import android.provider.Telephony
import android.util.Log
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsContentObserver(
    handler: Handler,
    private val onSmsChanged: () -> Unit
) : ContentObserver(handler) {
    
    companion object {
        private const val TAG = "SmsContentObserver"
    }
    
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d(TAG, "onChange called - selfChange: $selfChange")
        // Notify that SMS database has changed
        onSmsChanged()
    }
    
    override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "onChange called with URI - selfChange: $selfChange, uri: $uri")
        // Notify that SMS database has changed
        onSmsChanged()
    }
}

