package com.text.messages.sms.messanger.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.text.messages.sms.messanger.R

object AfterCallNotificationHelper {

    private const val TAG = "AfterCallNotificationHelper"
    private const val CHANNEL_ID = "post_call_channel"
    const val NOTIFICATION_ID = 1001
    private const val FULL_SCREEN_REQUEST_CODE = 1001
    private const val CONTENT_REQUEST_CODE = 1002

    fun initialize(context: Context) {
        createNotificationChannel(context)
    }

    fun cancelPostCallNotification(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.after_call_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.after_call_channel_description)
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBypassDnd(true)
            }
            val soundAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, soundAttrs)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * No-overlay path: high-priority notification with full-screen intent (reference app pattern).
     */
    fun showFullScreenNotification(context: Context, event: CallEndEvent) {
        try {
            val appContext = context.applicationContext
            createNotificationChannel(appContext)

            val activityIntent = CallAfterLauncher.createIntent(appContext, event)
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val fullScreenPendingIntent = PendingIntent.getActivity(
                appContext,
                FULL_SCREEN_REQUEST_CODE,
                activityIntent,
                pendingFlags
            )
            val contentPendingIntent = PendingIntent.getActivity(
                appContext,
                CONTENT_REQUEST_CODE,
                activityIntent,
                pendingFlags
            )

            val title = notificationTitle(appContext, event.callType)
            val text = notificationText(appContext, event.number)

            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                .setWhen(event.endTimeMs)
                .setShowWhen(true)
                .setContentIntent(contentPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)

            if (!Settings.canDrawOverlays(appContext)) {
                val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${appContext.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val overlayPending = PendingIntent.getActivity(
                    appContext,
                    CONTENT_REQUEST_CODE + 1,
                    overlayIntent,
                    pendingFlags
                )
                builder.addAction(
                    R.drawable.ic_info,
                    appContext.getString(R.string.after_call_enable_overlay),
                    overlayPending
                )
            }

            val notificationManager = NotificationManagerCompat.from(appContext)
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications disabled; attempting direct activity start")
                CallAfterLauncher.tryStartActivityIfInteractive(appContext, event)
                return
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Post-call full-screen notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing full-screen notification", e)
            CallAfterLauncher.tryStartActivityIfInteractive(context, event)
        }
    }

    /** @deprecated Use [showFullScreenNotification] with [CallEndEvent]. */
    fun showNotification(
        context: Context,
        phoneNumber: String?,
        callType: String,
        callDuration: Long
    ) {
        val event = CallEndEvent(
            number = phoneNumber,
            callType = callType,
            durationMs = callDuration,
            endTimeMs = System.currentTimeMillis(),
            startTimeMs = System.currentTimeMillis() - callDuration,
            isIncoming = true,
            isUnknownCaller = phoneNumber.isNullOrEmpty(),
            shouldShow = true
        )
        showFullScreenNotification(context, event)
    }

    private fun notificationTitle(context: Context, callType: String): String {
        return when (callType) {
            "missed" -> context.getString(R.string.missed_call)
            "no_answer" -> context.getString(R.string.no_answer)
            else -> context.getString(R.string.after_call_notification_title)
        }
    }

    private fun notificationText(context: Context, phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty()) {
            return context.getString(R.string.unknown_number)
        }
        return getContactName(context, phoneNumber) ?: phoneNumber
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex =
                        cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact name", e)
            null
        }
    }
}
