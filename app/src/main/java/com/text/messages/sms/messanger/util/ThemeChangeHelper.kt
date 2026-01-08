package com.text.messages.sms.messanger.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Helper utility to register theme change receivers for activities and fragments
 */
object ThemeChangeHelper {
    
    /**
     * Register a theme change receiver for an activity
     * Returns the receiver so it can be unregistered in onDestroy
     */
    fun registerThemeChangeReceiver(
        activity: AppCompatActivity,
        rootView: View
    ): BroadcastReceiver {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Apply theme IMMEDIATELY
                ThemeManager.applyThemeImmediate(activity, rootView)
                rootView.invalidate()
                rootView.requestLayout()
            }
        }
        
        activity.registerReceiver(
            receiver,
            IntentFilter("com.text.messages.sms.messanger.THEME_CHANGED"),
            receiverFlags
        )
        
        return receiver
    }
    
    /**
     * Register a theme change receiver for a fragment
     * Returns the receiver so it can be unregistered in onDestroyView
     */
    fun registerThemeChangeReceiver(
        fragment: Fragment,
        rootView: View
    ): BroadcastReceiver {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Apply theme IMMEDIATELY
                ThemeManager.applyThemeImmediate(fragment.requireContext(), rootView)
                rootView.invalidate()
                rootView.requestLayout()
            }
        }
        
        fragment.requireContext().registerReceiver(
            receiver,
            IntentFilter("com.text.messages.sms.messanger.THEME_CHANGED"),
            receiverFlags
        )
        
        return receiver
    }
}

