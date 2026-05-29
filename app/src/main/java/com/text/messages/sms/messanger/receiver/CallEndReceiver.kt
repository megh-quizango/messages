package com.text.messages.sms.messanger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.text.messages.sms.messanger.util.AfterCallAdPreloader
import com.text.messages.sms.messanger.util.AfterCallNotificationHelper
import com.text.messages.sms.messanger.util.AfterCallPolicy
import com.text.messages.sms.messanger.util.CallAfterLauncher
import com.text.messages.sms.messanger.util.CallStateTracker

/**
 * Handles after-call when overlay permission is **not** granted.
 * When overlay is available, [CallReceiver] owns the flow instead.
 */
class CallEndReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallEndReceiver"
        private const val IDLE_DELAY_MS = 300L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (Settings.canDrawOverlays(context)) return
        if (!AfterCallPolicy.shouldProcessAfterCall(context)) return

        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                CallStateTracker.onOutgoingCall(number)
                AfterCallAdPreloader.preloadIfNeeded(context)
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        CallStateTracker.onRinging(incomingNumber)
                        AfterCallAdPreloader.preloadIfNeeded(context)
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        CallStateTracker.onOffhook(incomingNumber)
                        AfterCallAdPreloader.preloadIfNeeded(context)
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        handleIdle(context, incomingNumber)
                    }
                }
            }
        }
    }

    private fun handleIdle(context: Context, incomingNumber: String?) {
        val pendingResult = goAsync()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val event = CallStateTracker.consumeCallEndEvent(context, incomingNumber)
                if (event != null && event.shouldShow) {
                    AfterCallNotificationHelper.showFullScreenNotification(context, event)
                    CallAfterLauncher.tryStartActivityIfInteractive(context, event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling call end", e)
            } finally {
                pendingResult.finish()
            }
        }, IDLE_DELAY_MS)
    }
}
