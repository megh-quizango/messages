package com.text.messages.sms.messanger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.text.messages.sms.messanger.util.AfterCallAdPreloader
import com.text.messages.sms.messanger.util.AfterCallPolicy
import com.text.messages.sms.messanger.util.CallAfterLauncher
import com.text.messages.sms.messanger.util.CallStateTracker
import com.text.messages.sms.messanger.util.CallerWidgetWindow

/**
 * Primary after-call path when [Settings.canDrawOverlays] is granted.
 * Pairs with [CallEndReceiver] for the no-overlay fallback.
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        private const val IDLE_DELAY_MS = 300L

        private var windowView: CallerWidgetWindow? = null

        fun stopInCallOverlay() {
            try {
                windowView?.hide()
                windowView = null
                Log.d(TAG, "In-call overlay stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop in-call overlay", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (!Settings.canDrawOverlays(context)) return
        if (!AfterCallPolicy.shouldProcessAfterCall(context)) return

        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                CallStateTracker.onOutgoingCall(number)
                AfterCallAdPreloader.preloadIfNeeded(context)
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                handlePhoneState(context, intent)
            }
        }
    }

    private fun handlePhoneState(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "state=$state number=$incomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallStateTracker.onRinging(incomingNumber)
                AfterCallAdPreloader.preloadIfNeeded(context)
                if (shouldShowInCallOverlay(context)) {
                    showInCallOverlay(context)
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallStateTracker.onOffhook(incomingNumber)
                AfterCallAdPreloader.preloadIfNeeded(context)
                if (CallStateTracker.isIncoming && shouldShowInCallOverlay(context)) {
                    showInCallOverlay(context)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallStateTracker.mergeNumberFromIntent(incomingNumber)
                handleCallEnded(context, incomingNumber)
            }
        }
    }

    private fun handleCallEnded(context: Context, incomingNumber: String?) {
        val pendingResult = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                stopInCallOverlay()
                val event = CallStateTracker.consumeCallEndEvent(context, incomingNumber)
                if (event != null && event.shouldShow) {
                    CallAfterLauncher.launchAfterDelay(context, event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on call end", e)
            } finally {
                pendingResult.finish()
            }
        }, IDLE_DELAY_MS)
    }

    private fun showInCallOverlay(context: Context) {
        try {
            if (windowView == null) {
                windowView = CallerWidgetWindow(context.applicationContext)
            }
            windowView?.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show in-call overlay", e)
        }
    }

    private fun shouldShowInCallOverlay(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        return isAggressiveOemDevice()
    }

    private fun isAggressiveOemDevice(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val oems = listOf(
            "xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus", "realme", "huawei", "honor"
        )
        return oems.any { brand.contains(it) || manufacturer.contains(it) }
    }
}
