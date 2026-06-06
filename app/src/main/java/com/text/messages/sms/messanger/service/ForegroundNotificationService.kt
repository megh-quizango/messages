package com.text.messages.sms.messanger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.ui.compose.ComposeActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.ui.manageapps.ManageAppsActivity
import com.text.messages.sms.messanger.ui.personalize.ThemesActivity
import com.text.messages.sms.messanger.util.RingtoneSoundResolver

class ForegroundNotificationService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val collapsed = RemoteViews(packageName, R.layout.noti_collapse)
        val expanded = RemoteViews(packageName, R.layout.noti_expand)
        bindActions(collapsed)
        bindActions(expanded)

        val ringtonePrefs = getSharedPreferences(RingtoneSoundResolver.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedRingtone = ringtonePrefs.getString(
            RingtoneSoundResolver.KEY_SELECTED_RINGTONE,
            RingtoneSoundResolver.DEFAULT
        )
        val pickedRingtoneUri = ringtonePrefs.getString(RingtoneSoundResolver.KEY_SELECTED_RINGTONE_URI, null)
        val notificationSound = RingtoneSoundResolver.getNotificationSoundUri(
            this,
            selectedRingtone,
            pickedRingtoneUri
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColorized(true)
            .setColor(Color.parseColor("#2569F1"))
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setSound(notificationSound)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun bindActions(remoteViews: RemoteViews) {
        remoteViews.setOnClickPendingIntent(R.id.btn1, activityIntent(MainActivity::class.java, 101))
        remoteViews.setOnClickPendingIntent(R.id.btn2, activityIntent(ComposeActivity::class.java, 102))
        remoteViews.setOnClickPendingIntent(R.id.btn3, activityIntent(ManageAppsActivity::class.java, 103))
        remoteViews.setOnClickPendingIntent(R.id.btn4, activityIntent(ThemesActivity::class.java, 105))
    }

    private fun activityIntent(activityClass: Class<*>, requestCode: Int): PendingIntent {
        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ForegroundNotification"
        private const val CHANNEL_ID = "Insta"
        private const val CHANNEL_NAME = "Forground"
        private const val SERVICE_ID = 404

        fun start(context: Context) {
            val intent = Intent(context, ForegroundNotificationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start foreground notification service", e)
            }
        }
    }
}
