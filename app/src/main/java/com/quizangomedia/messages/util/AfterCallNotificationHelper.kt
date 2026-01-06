package com.quizangomedia.messages.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quizangomedia.messages.R
import com.quizangomedia.messages.ui.caller.CallAfterActivity

object AfterCallNotificationHelper {
    
    private const val TAG = "AfterCallNotificationHelper"
    private const val CHANNEL_ID = "after_call_notifications"
    private const val CHANNEL_NAME = "After Call Notifications"
    
    fun initialize(context: Context) {
        createNotificationChannel(context)
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications shown when overlay permission is not granted"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showNotification(
        context: Context,
        phoneNumber: String?,
        callType: String,
        callDuration: Long
    ) {
        try {
            val title = when (callType) {
                "missed" -> "Missed Call"
                "no_answer" -> "No Answer"
                else -> "Call Ended"
            }
            
            val text = if (phoneNumber.isNullOrEmpty()) {
                "Unknown number"
            } else {
                // Try to get contact name
                val contactName = getContactName(context, phoneNumber)
                contactName ?: phoneNumber
            }
            
            // Create intent to open CallAfterActivity
            val intent = Intent(context, CallAfterActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("CALLER_NUMBER", phoneNumber)
                putExtra("CALL_END_TIME", System.currentTimeMillis())
                putExtra("CALL_TYPE", callType)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
            
            // Add action to request overlay permission
            if (!Settings.canDrawOverlays(context)) {
                val permissionIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                val permissionPendingIntent = PendingIntent.getActivity(
                    context,
                    System.currentTimeMillis().toInt() + 1,
                    permissionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                builder.addAction(
                    R.drawable.ic_info,
                    "Enable Overlay",
                    permissionPendingIntent
                )
            }
            
            // Show notification
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            
            Log.d(TAG, "Notification shown for call: $callType, number: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
    
    private fun getContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact name", e)
            null
        }
    }
}

