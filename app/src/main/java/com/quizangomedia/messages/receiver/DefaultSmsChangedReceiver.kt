package com.quizangomedia.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DefaultSmsChangedReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // Handle default SMS app change
        // Notify user if app is no longer default
    }
}

