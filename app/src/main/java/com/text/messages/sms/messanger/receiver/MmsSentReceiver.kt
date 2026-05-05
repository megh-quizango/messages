package com.text.messages.sms.messanger.receiver

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.util.MmsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MmsSentReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MmsSentReceiver"
        const val ACTION_MMS_SENT = "com.text.messages.sms.messanger.MMS_SENT"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("id", 0)
        val contentUriStr = intent.getStringExtra("content_uri")
        @Suppress("UNUSED_VARIABLE")
        val threadId = intent.getLongExtra("thread_id", -1)
        @Suppress("UNUSED_VARIABLE")
        val address = intent.getStringExtra("address") ?: ""
        val filePath = intent.getStringExtra("file_path")
        
        val resultCode = resultCode
        val pendingResult = goAsync()
        
        Log.d(TAG, "onReceive - messageId: $messageId, resultCode: $resultCode, contentUri: $contentUriStr")
        
        // Log result code meaning
        val resultCodeName = when (resultCode) {
            Activity.RESULT_OK -> "RESULT_OK"
            1 -> "RESULT_ERROR_GENERIC_FAILURE"
            2 -> "RESULT_ERROR_RADIO_OFF"
            3 -> "RESULT_ERROR_NULL_PDU"
            4 -> "RESULT_ERROR_NO_SERVICE"
            5 -> "RESULT_ERROR_NETWORK_FAILURE (or device-specific)"
            else -> "UNKNOWN_ERROR_$resultCode"
        }
        Log.d(TAG, "MMS send result: $resultCodeName ($resultCode)")
        
        if (contentUriStr.isNullOrEmpty()) {
            Log.w(TAG, "No content URI provided")
            pendingResult.finish()
            return
        }
        
        val contentUri = Uri.parse(contentUriStr)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (resultCode == Activity.RESULT_OK) {
                    // MMS sent successfully
                    Log.w(TAG, "MMS has finished sending, marking it as so in the database")
                    
                    // Update message box to SENT (2) in Telephony Provider
                    val values = ContentValues().apply {
                        put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                    }
                    
                    context.contentResolver.update(
                        contentUri,
                        values,
                        null,
                        null
                    )
                    
                    // Mark as sent in database
                    if (messageId > 0) {
                        val database = MessagesApp.database
                        val messageDao = database.messageDao()
                        
                        val message = messageDao.getMessageById(messageId)
                        if (message != null) {
                            messageDao.updateMessage(
                                message.copy(
                                    status = MessageStatus.SENT,
                                    type = com.text.messages.sms.messanger.data.model.MessageType.SENT
                                )
                            )
                            Log.d(TAG, "Message marked as SENT in database - messageId: $messageId")
                        }
                    }
                } else {
                    // MMS failed to send
                    Log.w(TAG, "MMS has failed to send, marking it as so in the database")
                    
                    // Update message box to FAILED (5) in Telephony Provider
                    val mmsId = ContentUris.parseId(contentUri)
                    val values = ContentValues().apply {
                        put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
                    }
                    
                    context.contentResolver.update(
                        Telephony.Mms.CONTENT_URI,
                        values,
                        "${Telephony.Mms._ID} = ?",
                        arrayOf(mmsId.toString())
                    )
                    
                    // Update pending messages error type
                    val pendingValues = ContentValues().apply {
                        put("err_type", 10)
                    }
                    context.contentResolver.update(
                        Telephony.MmsSms.PendingMessages.CONTENT_URI,
                        pendingValues,
                        "msg_id = ?",
                        arrayOf(mmsId.toString())
                    )
                    
                    // Mark as failed in database
                    if (messageId > 0) {
                        val database = MessagesApp.database
                        val messageDao = database.messageDao()
                        
                        val message = messageDao.getMessageById(messageId)
                        if (message != null) {
                            messageDao.updateMessage(message.copy(status = MessageStatus.FAILED))
                            Log.d(TAG, "Message marked as FAILED in database - messageId: $messageId, resultCode: $resultCode")
                        }
                    }
                    
                    // Log detailed error information
                    val errorMessage = MmsHelper.getMmsErrorMessage(resultCode)
                    Log.e(TAG, "MMS send failed: $errorMessage")
                    
                    // Show notification to user about the failure
                    showMmsFailureNotification(context, errorMessage)
                }
                
                // Clean up temporary file if exists
                if (!filePath.isNullOrEmpty()) {
                    try {
                        val tempFile = File(filePath)
                        if (tempFile.exists()) {
                            tempFile.delete()
                            Log.d(TAG, "Cleaned up temporary file: $filePath")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cleaning up temporary file: $filePath", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing MMS sent status", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun showMmsFailureNotification(context: Context, errorMessage: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "mms_errors",
                    "MMS Errors",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for MMS send failures"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(context, "mms_errors")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("MMS Send Failed")
                .setContentText(errorMessage)
                .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing MMS failure notification", e)
        }
    }
}

