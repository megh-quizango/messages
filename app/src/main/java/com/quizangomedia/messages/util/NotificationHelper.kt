package com.quizangomedia.messages.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.TextUtils
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.R
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.receiver.NotificationActionReceiver
import com.quizangomedia.messages.ui.conversation.ConversationDetailActivity
import com.quizangomedia.messages.ui.notifications.ButtonAction
import com.quizangomedia.messages.ui.notifications.NotificationPreview
import com.quizangomedia.messages.ui.pin.PinActivity
import com.quizangomedia.messages.util.OtpHelper
import com.quizangomedia.messages.util.PrivateConversationStorage
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

object NotificationHelper {
    
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "sms_notifications"
    private const val CHANNEL_NAME = "SMS Notifications"
    private const val PREFS_NAME = "notifications_settings"
    
    private const val KEY_NOTIFICATION_PREVIEW = "notification_preview"
    private const val KEY_WAKE_SCREEN = "wake_screen"
    private const val KEY_BUTTON_1_ACTION = "button_1_action"
    private const val KEY_BUTTON_2_ACTION = "button_2_action"
    private const val KEY_BUTTON_3_ACTION = "button_3_action"
    
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
                description = "Notifications for incoming SMS messages"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showNotification(
        context: Context,
        threadId: Long,
        address: String,
        messageBody: String,
        timestamp: Long
    ) {
        Log.d(TAG, "showNotification called - threadId: $threadId, address: $address, messageBody length: ${messageBody.length}")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get contact info
                val contactInfo = getContactInfo(context, address)
                val contactName = contactInfo.first ?: address
                val photoUri = contactInfo.second
                Log.d(TAG, "Contact info retrieved - name: $contactName, photoUri: $photoUri")
                
                // Get notification settings
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val previewMode = prefs.getString(KEY_NOTIFICATION_PREVIEW, NotificationPreview.SHOW_NAME_AND_MESSAGE.name)
                val wakeScreen = prefs.getBoolean(KEY_WAKE_SCREEN, false)
                
                // Get button actions
                val button1Action = prefs.getString(KEY_BUTTON_1_ACTION, ButtonAction.NONE.name)
                val button2Action = prefs.getString(KEY_BUTTON_2_ACTION, ButtonAction.NONE.name)
                val button3Action = prefs.getString(KEY_BUTTON_3_ACTION, ButtonAction.NONE.name)
                Log.d(TAG, "Button actions - 1: $button1Action, 2: $button2Action, 3: $button3Action")
                
                // Build notification
                withContext(Dispatchers.Main) {
                    buildAndShowNotification(
                        context = context,
                        threadId = threadId,
                        address = address,
                        contactName = contactName,
                        messageBody = messageBody,
                        photoUri = photoUri,
                        timestamp = timestamp,
                        previewMode = previewMode,
                        wakeScreen = wakeScreen,
                        button1Action = button1Action,
                        button2Action = button2Action,
                        button3Action = button3Action
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun buildAndShowNotification(
        context: Context,
        threadId: Long,
        address: String,
        contactName: String,
        messageBody: String,
        photoUri: String?,
        timestamp: Long,
        previewMode: String?,
        wakeScreen: Boolean,
        button1Action: String?,
        button2Action: String?,
        button3Action: String?
    ) {
        // Check if message contains OTP (do this first so we can use it later)
        val hasOTP = OtpHelper.isOTPMessage(messageBody)
        val otp = if (hasOTP) OtpHelper.extractOTP(messageBody) else null
        
        // Check if this is a private conversation
        val isPrivate = PrivateConversationStorage.isPrivateConversation(context, threadId)
        
        // Determine what to show based on preview mode
        val preview = NotificationPreview.values().find { it.name == previewMode } ?: NotificationPreview.SHOW_NAME_AND_MESSAGE
        val title = contactName
        var text = when {
            // Private conversations always show only name (no message)
            isPrivate -> ""
            // Otherwise use preview mode
            preview == NotificationPreview.SHOW_NAME_AND_MESSAGE -> messageBody
            preview == NotificationPreview.SHOW_NAME -> ""
            preview == NotificationPreview.HIDE_CONTENTS -> ""
            else -> messageBody
        }
        
        // If OTP is detected and preview shows message, enhance the text to highlight OTP
        if (hasOTP && otp != null && preview == NotificationPreview.SHOW_NAME_AND_MESSAGE) {
            // Replace OTP in text with a more prominent version
            text = messageBody.replace(otp, "🔐 $otp 🔐")
        }
        
        // Create intent to open conversation or pin activity for private conversations
        val intent = if (isPrivate) {
            // For private conversations, redirect to PinActivity
            Intent(context, PinActivity::class.java).apply {
                putExtra("from_notification", true)
                putExtra("threadId", threadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            // For regular conversations, open ConversationDetailActivity
            Intent(context, ConversationDetailActivity::class.java).apply {
                putExtra("threadId", threadId)
                putExtra("address", address)
                putExtra("contactName", contactName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Load contact photo as bitmap for large icon
        val largeIcon = try {
            loadContactPhoto(context, photoUri, contactName, address)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contact photo", e)
            generateAvatarBitmap(context, contactName, address)
        }
        
        // Configure action buttons
        var actions = listOf(
            button1Action to 1,
            button2Action to 2,
            button3Action to 3
        ).filter { it.first != null && it.first != ButtonAction.NONE.name }
        
        // If OTP is detected, replace one of the existing action buttons with COPY_OTP
        // Priority: Keep REPLY if present, otherwise replace the last button
        if (hasOTP && otp != null && !actions.any { it.first == ButtonAction.COPY_OTP.name }) {
            if (actions.isEmpty()) {
                // No existing actions, add COPY_OTP as the first action
                actions = listOf(ButtonAction.COPY_OTP.name to 1)
            } else {
                // Replace the last non-REPLY button, or if all are REPLY, replace the last one
                val replyIndex = actions.indexOfFirst { it.first == ButtonAction.REPLY.name }
                val indexToReplace = if (replyIndex >= 0 && actions.size > 1) {
                    // Keep REPLY, replace the last non-REPLY button
                    val nonReplyActions = actions.filterIndexed { index, _ -> index != replyIndex }
                    if (nonReplyActions.isNotEmpty()) {
                        actions.indexOf(nonReplyActions.last())
                    } else {
                        actions.size - 1
                    }
                } else {
                    // No REPLY or only REPLY exists, replace the last button
                    actions.size - 1
                }
                
                // Replace the button at indexToReplace with COPY_OTP
                actions = actions.mapIndexed { index, action ->
                    if (index == indexToReplace) {
                        ButtonAction.COPY_OTP.name to action.second
                    } else {
                        action
                    }
                }
            }
            Log.d(TAG, "Replaced action button with COPY_OTP for OTP: $otp")
        }
        
        // Build standard notification (no custom view)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat_bubble) // REQUIRED - must be white/transparent vector
            .setLargeIcon(largeIcon) // Contact photo
            .setContentTitle(title) // Contact name
            .setContentText(text) // Message text
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)) // Expandable text style
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            .setShowWhen(true)
            .setWhen(timestamp)
            .setOnlyAlertOnce(false) // Always alert for new messages
            .setGroup("sms_messages") // Group notifications
            .setGroupSummary(false)
        
        Log.d(TAG, "Building standard notification with ${actions.size} actions")
        
        // Add all standard action buttons
        actions.forEachIndexed { index, (actionName, buttonNumber) ->
            val action = ButtonAction.values().find { it.name == actionName }
            if (action != null && action != ButtonAction.NONE) {
                // Handle REPLY separately with RemoteInput for inline reply
                if (action == ButtonAction.REPLY) {
                    val replyLabel = "Reply"
                    val remoteInput = RemoteInput.Builder("reply_text")
                        .setLabel("Type a reply...")
                        .build()
                    
                    val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        putExtra("action", ButtonAction.REPLY.name)
                        putExtra("threadId", threadId)
                        putExtra("address", address)
                        putExtra("contactName", contactName)
                    }
                    
                    val replyPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.getBroadcast(
                            context,
                            (threadId.toInt() * 100),
                            replyIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    } else {
                        PendingIntent.getBroadcast(
                            context,
                            (threadId.toInt() * 100),
                            replyIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    }
                    
                    val replyActionCompat = NotificationCompat.Action.Builder(
                        R.drawable.ic_reply,
                        replyLabel,
                        replyPendingIntent
                    )
                        .addRemoteInput(remoteInput)
                        .build()
                    
                    builder.addAction(replyActionCompat)
                    Log.d(TAG, "Added REPLY as standard action with RemoteInput for inline reply")
                } else if (action == ButtonAction.COPY_OTP) {
                    // Handle COPY_OTP action - ensure messageBody is included
                    val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        putExtra("action", ButtonAction.COPY_OTP.name)
                        putExtra("threadId", threadId)
                        putExtra("address", address)
                        putExtra("messageBody", messageBody)
                        putExtra("contactName", contactName)
                    }
                    
                    val actionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.getBroadcast(
                            context,
                            (threadId.toInt() * 10 + buttonNumber),
                            actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    } else {
                        PendingIntent.getBroadcast(
                            context,
                            (threadId.toInt() * 10 + buttonNumber),
                            actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                    
                    val iconRes = getActionIcon(action)
                    val actionTitle = action.displayName
                    
                    builder.addAction(iconRes, actionTitle, actionPendingIntent)
                    Log.d(TAG, "Added COPY_OTP action for OTP: $otp")
                } else {
                    // Add other actions as standard actions
                    val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        putExtra("action", action.name)
                        putExtra("threadId", threadId)
                        putExtra("address", address)
                        putExtra("messageBody", messageBody)
                        putExtra("contactName", contactName)
                    }
                    
                    val actionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.getBroadcast(
                            context,
                            (threadId.toInt() * 10 + buttonNumber),
                            actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    } else {
                        PendingIntent.getBroadcast(
                            context,
                            (threadId.toInt() * 10 + buttonNumber),
                            actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                    
                    val iconRes = getActionIcon(action)
                    val actionTitle = action.displayName
                    
                    builder.addAction(iconRes, actionTitle, actionPendingIntent)
                    Log.d(TAG, "Added standard action: $actionTitle")
                }
            }
        }
        
        // Add wake screen if enabled
        if (wakeScreen) {
            builder.setFullScreenIntent(pendingIntent, true)
        }
        
        // Show notification
        try {
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!notificationManager.areNotificationsEnabled()) {
                    Log.w(TAG, "Notifications are disabled for this app")
                    return
                }
            }
            
            val notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are disabled via NotificationManagerCompat")
                return
            }
            
            val notification = builder.build()
            
            // Log notification details for debugging
            val styleName = notification.extras.getString(NotificationCompat.EXTRA_TEMPLATE) ?: "null"
            val actionCount = notification.actions?.size ?: 0
            Log.d(TAG, "Standard notification built - style: $styleName, " +
                    "standardActionsCount: $actionCount")
            
            if (actionCount > 0) {
                Log.d(TAG, "Notification actions:")
                notification.actions?.forEachIndexed { index, action ->
                    Log.d(TAG, "  Action $index: ${action.title}")
                }
            }
            
            // Ensure notification is actually posted
            try {
                notificationManager.notify(threadId.toInt(), notification)
                Log.d(TAG, "Notification posted successfully for threadId: $threadId, address: $address, title: $title, text: $text")
                
                // Verify it was posted
                val activeNotifications = notificationManager.activeNotifications
                val wasPosted = activeNotifications.any { it.id == threadId.toInt() }
                if (wasPosted) {
                    Log.d(TAG, "Notification confirmed active in system")
                } else {
                    Log.w(TAG, "Notification was not found in active notifications list - this indicates a layout or permission issue")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post notification", e)
                e.printStackTrace()
                throw e
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to show notification", e)
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
            e.printStackTrace()
            // Fallback: try showing a simple notification without custom layout
            try {
                showSimpleNotification(context, threadId, address, contactName, text, largeIcon, pendingIntent, timestamp)
            } catch (e2: Exception) {
                Log.e(TAG, "Error showing fallback notification", e2)
                e2.printStackTrace()
            }
        }
    }
    
    private fun getActionIcon(action: ButtonAction): Int {
        return when (action) {
            ButtonAction.ARCHIVE -> R.drawable.ic_archive
            ButtonAction.DELETE -> R.drawable.ic_delete
            ButtonAction.BLOCK -> R.drawable.ic_block
            ButtonAction.CALL -> R.drawable.ic_call
            ButtonAction.MARK_AS_READ -> R.drawable.ic_mark_as_read
            ButtonAction.REPLY -> R.drawable.ic_reply
            ButtonAction.COPY_OTP -> R.drawable.ic_copy
            ButtonAction.NONE -> R.drawable.ic_chat_bubble
        }
    }
    
    private fun getActionBackgroundRes(action: ButtonAction): Int {
        return when (action) {
            ButtonAction.ARCHIVE -> R.drawable.button_archive_background
            ButtonAction.DELETE -> R.drawable.button_block_background
            ButtonAction.BLOCK -> R.drawable.button_block_background
            ButtonAction.CALL -> R.drawable.button_copy_otp_background
            ButtonAction.MARK_AS_READ -> R.drawable.button_copy_otp_background
            ButtonAction.REPLY -> R.drawable.button_copy_otp_background
            ButtonAction.COPY_OTP -> R.drawable.button_copy_otp_background
            ButtonAction.NONE -> R.drawable.button_action_background
        }
    }
    
    private fun getActionTextColor(action: ButtonAction): Int {
        return when (action) {
            ButtonAction.ARCHIVE -> Color.parseColor("#F57F17") // Dark yellow
            ButtonAction.DELETE -> Color.parseColor("#C62828") // Dark red
            ButtonAction.BLOCK -> Color.parseColor("#C62828") // Dark red
            ButtonAction.CALL -> Color.parseColor("#1976D2") // Dark blue
            ButtonAction.MARK_AS_READ -> Color.parseColor("#1976D2") // Dark blue
            ButtonAction.REPLY -> Color.parseColor("#1976D2") // Dark blue
            ButtonAction.COPY_OTP -> Color.parseColor("#1976D2") // Dark blue
            ButtonAction.NONE -> Color.parseColor("#757575") // Gray
        }
    }
    
    private fun getActionColor(action: ButtonAction): Int {
        return when (action) {
            ButtonAction.ARCHIVE -> Color.parseColor("#FFF9C4") // Light yellow
            ButtonAction.DELETE -> Color.parseColor("#FFEBEE") // Light red
            ButtonAction.BLOCK -> Color.parseColor("#FFEBEE") // Light red
            ButtonAction.CALL -> Color.parseColor("#E3F2FD") // Light blue
            ButtonAction.MARK_AS_READ -> Color.parseColor("#E3F2FD") // Light blue
            ButtonAction.REPLY -> Color.parseColor("#E3F2FD") // Light blue
            ButtonAction.COPY_OTP -> Color.parseColor("#E3F2FD") // Light blue
            ButtonAction.NONE -> Color.parseColor("#F5F5F5") // Light gray
        }
    }
    
    private fun loadContactPhoto(
        context: Context,
        photoUri: String?,
        contactName: String,
        address: String
    ): Bitmap? {
        return try {
            if (!photoUri.isNullOrEmpty()) {
                val uri = Uri.parse(photoUri)
                // Load bitmap synchronously on background thread
                // Use ContentResolver to load the bitmap directly
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                } ?: generateAvatarBitmap(context, contactName, address)
            } else {
                // Generate avatar with first letter
                generateAvatarBitmap(context, contactName, address)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contact photo", e)
            generateAvatarBitmap(context, contactName, address)
        }
    }
    
    private fun generateAvatarBitmap(
        context: Context,
        contactName: String,
        address: String
    ): Bitmap {
        val size = 128 // dp to pixels
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Get background color
        val identifier = if (contactName != address) contactName else address
        val backgroundColor = AvatarHelper.getColorForIdentifier(identifier, context)
        
        // Draw circle background
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = backgroundColor
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // Draw first letter
        val firstLetter = AvatarHelper.getFirstLetter(contactName, address)
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = context.getColor(R.color.gray_dark)
            textSize = size * 0.4f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(firstLetter, size / 2f, textY, textPaint)
        
        return bitmap
    }
    
    private fun getContactInfo(context: Context, phoneNumber: String): Pair<String?, String?> {
        // Try multiple formats of the phone number for better matching
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val variations = listOf(
            normalizedNumber,
            phoneNumber,
            if (normalizedNumber.length > 10) normalizedNumber.takeLast(10) else null
        ).filterNotNull().distinct()
        
        for (number in variations) {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI
            )
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                    
                    if (name != null || photoUri != null) {
                        return Pair(name, photoUri)
                    }
                }
            }
        }
        
        return Pair(null, null)
    }
    
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9]"), "")
    }
    
    fun cancelNotification(context: Context, threadId: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(threadId.toInt())
    }
    
    fun cancelAllNotifications(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()
    }
    
    private fun showSimpleNotification(
        context: Context,
        threadId: Long,
        address: String,
        contactName: String,
        messageText: String,
        largeIcon: Bitmap?,
        pendingIntent: PendingIntent,
        timestamp: Long
    ) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chat_bubble)
                .setLargeIcon(largeIcon)
                .setContentTitle(contactName)
                .setContentText(messageText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(timestamp)
            
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(threadId.toInt(), builder.build())
            Log.d(TAG, "Simple notification shown for threadId: $threadId")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing simple notification", e)
        }
    }
}

