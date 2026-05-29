package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.text.messages.sms.messanger.ui.caller.CallAfterActivity

object CallAfterLauncher {

    private const val TAG = "CallAfterLauncher"

    const val EXTRA_CALLER_NUMBER = "CALLER_NUMBER"
    const val EXTRA_CALL_END_TIME = "CALL_END_TIME"
    const val EXTRA_CALL_START_TIME = "CALL_START_TIME"
    const val EXTRA_CALL_TYPE = "CALL_TYPE"
    const val EXTRA_IS_INCOMING = "IS_INCOMING"
    const val EXTRA_FROM_CALL_END = "from_call_end"

    private const val OVERLAY_LAUNCH_DELAY_MS = 300L
    private const val NOTIFICATION_ACTIVITY_DELAY_MS = 500L

    fun createIntent(context: Context, event: CallEndEvent): Intent {
        return Intent(context, CallAfterActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                addFlags(0x00080000 or 0x00200000) // SHOW_WHEN_LOCKED | TURN_SCREEN_ON
            }
            putExtra(EXTRA_CALLER_NUMBER, event.number)
            putExtra(EXTRA_CALL_END_TIME, event.endTimeMs)
            putExtra(EXTRA_CALL_START_TIME, event.startTimeMs)
            putExtra(EXTRA_CALL_TYPE, event.callType)
            putExtra(EXTRA_IS_INCOMING, event.isIncoming)
            putExtra(EXTRA_FROM_CALL_END, true)
        }
    }

    /** Overlay-granted path: short delay then start activity (reduces telephony races). */
    fun launchAfterDelay(context: Context, event: CallEndEvent, delayMs: Long = OVERLAY_LAUNCH_DELAY_MS) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                appContext.startActivity(createIntent(appContext, event))
                Log.d(TAG, "CallAfterActivity launched (overlay path)")
            } catch (e: Exception) {
                Log.e(TAG, "Overlay launch failed, falling back to notification", e)
                AfterCallNotificationHelper.showFullScreenNotification(appContext, event)
            }
        }, delayMs)
    }

    /**
     * Secondary attempt after full-screen notification is posted (reference app pattern).
     */
    fun tryStartActivityIfInteractive(context: Context, event: CallEndEvent) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val powerManager =
                    appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isInteractive) {
                    Log.d(TAG, "Screen not interactive, skipping direct activity start")
                    return@postDelayed
                }
                appContext.startActivity(createIntent(appContext, event))
                Log.d(TAG, "CallAfterActivity launched from notification fallback")
            } catch (e: Exception) {
                Log.e(TAG, "Delayed activity start failed", e)
            }
        }, NOTIFICATION_ACTIVITY_DELAY_MS)
    }
}
