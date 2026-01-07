package com.text.messages.sms.messanger.service

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.receiver.MmsSentReceiver
import com.text.messages.sms.messanger.util.MmsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object MmsSender {
    private const val TAG = "MmsSender"
    
    /**
     * Send MMS with attachments (images, contact cards, etc.)
     * @param subscriptionId Subscription ID (-1 for default)
     * @param messageId Message ID for tracking
     * @param addresses List of recipient addresses
     * @param attachments List of attachment URIs
     * @param subject Optional subject line
     * @param body Optional text body (can be empty for attachment-only MMS)
     */
    suspend fun sendMms(
        subscriptionId: Int = -1,
        messageId: Long,
        addresses: List<String>,
        attachments: List<Uri>,
        subject: String? = null,
        body: String = ""
    ): Result<Uri?> {
        return withContext(Dispatchers.IO) {
            try {
                val context = MessagesApp.instance
                
                // Check MMS availability
                if (!MmsHelper.isMmsServiceAvailable(context)) {
                    return@withContext Result.failure(
                        Exception("MMS service is not available or not enabled")
                    )
                }
                
                // Check mobile data availability
                if (!MmsHelper.isMobileDataAvailable(context)) {
                    Log.w(TAG, "Mobile data may not be available for MMS - this may cause send failure")
                    // Continue anyway - the system will handle the error, but log a warning
                }
                
                if (addresses.isEmpty()) {
                    return@withContext Result.failure(Exception("No recipients specified"))
                }
                
                if (attachments.isEmpty()) {
                    return@withContext Result.failure(Exception("No attachments specified"))
                }
                
                Log.d(TAG, "Sending MMS - messageId: $messageId, recipients: ${addresses.size}, attachments: ${attachments.size}")
                
                // Get or create thread ID
                val threadId = Telephony.Threads.getOrCreateThreadId(context, addresses[0])
                
                // Write MMS to Telephony Provider
                val mmsUri = writeMmsToProvider(
                    context,
                    threadId,
                    addresses,
                    attachments,
                    subject,
                    body
                ) ?: return@withContext Result.failure(Exception("Failed to write MMS to provider"))
                
                Log.d(TAG, "MMS written to provider: $mmsUri")
                
                // Prepare Bundle with MMS configuration
                val config = android.os.Bundle().apply {
                    putBoolean("enableGroupMms", true)
                    putInt("maxMessageSize", MmsHelper.getMaxMessageSize(context))
                    
                    // Add carrier-specific HTTP parameters if available
                    val httpParams = MmsHelper.getMmsConfigString(context, "httpParams", "")
                    if (httpParams.isNotEmpty()) {
                        putString("httpParams", httpParams)
                    }
                    
                    // Additional MMS settings
                    putBoolean("enableMultipartSMS", true)
                    val userAgent = MmsHelper.getMmsConfigString(context, "userAgent", "Android Messaging")
                    if (userAgent.isNotEmpty()) {
                        putString("userAgent", userAgent)
                    }
                }
                
                Log.d(TAG, "MMS config - maxMessageSize: ${config.getInt("maxMessageSize")}, enableGroupMms: ${config.getBoolean("enableGroupMms")}")
                
                // Get SmsManager (subscription-aware)
                val smsManager = if (subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    try {
                        SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get SmsManager for subscription $subscriptionId, using default", e)
                        SmsManager.getDefault()
                    }
                } else {
                    SmsManager.getDefault()
                }
                
                // Create PendingIntent for send status
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    messageId.toInt(),
                    Intent(context, MmsSentReceiver::class.java).apply {
                        action = "com.text.messages.sms.messanger.MMS_SENT"
                        putExtra("id", messageId)
                        putExtra("content_uri", mmsUri.toString())
                        putExtra("thread_id", threadId)
                        putExtra("address", addresses[0])
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Send MMS
                smsManager.sendMultimediaMessage(
                    context,
                    mmsUri,
                    null, // Location URL (null for sending)
                    config,
                    sentIntent
                )
                
                Log.d(TAG, "MMS send request submitted successfully")
                Result.success(mmsUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending MMS", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Write MMS to Telephony Provider
     */
    private fun writeMmsToProvider(
        context: Context,
        threadId: Long,
        addresses: List<String>,
        attachments: List<Uri>,
        subject: String?,
        body: String
    ): Uri? {
        return try {
            val resolver = context.contentResolver
            
            // 1. Create MMS entry
            val currentTime = System.currentTimeMillis() / 1000
            val mmsValues = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, currentTime)
                put(Telephony.Mms.DATE_SENT, 0) // Will be set when sent
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
                put(Telephony.Mms.MESSAGE_TYPE, 128) // MESSAGE_TYPE_SEND_REQ
                put(Telephony.Mms.MESSAGE_CLASS, 1) // MESSAGE_CLASS_PERSONAL
                put(Telephony.Mms.PRIORITY, 130) // PRIORITY_NORMAL
                put(Telephony.Mms.DELIVERY_REPORT, 0) // No delivery report
                put(Telephony.Mms.READ_REPORT, 0) // No read report
                put(Telephony.Mms.EXPIRY, currentTime + 604800) // 7 days expiry
                put(Telephony.Mms.TRANSACTION_ID, "T${System.currentTimeMillis()}")
                
                // Subject is required for MMS
                put(Telephony.Mms.SUBJECT, subject ?: if (body.isNotEmpty()) body.take(40) else "MMS")
            }
            
            val mmsUri = resolver.insert(Telephony.Mms.CONTENT_URI, mmsValues)
                ?: return null
            
            val mmsId = ContentUris.parseId(mmsUri)
            Log.d(TAG, "Created MMS entry with ID: $mmsId")
            
            // 2. Write addresses (TO)
            addresses.forEach { address ->
                val addrValues = ContentValues().apply {
                    put(Telephony.Mms.Addr.MSG_ID, mmsId)
                    put(Telephony.Mms.Addr.ADDRESS, address)
                    put(Telephony.Mms.Addr.TYPE, 151) // TO
                    put(Telephony.Mms.Addr.CHARSET, 106) // UTF-8
                }
                
                val addrUri = Uri.parse("content://mms/$mmsId/addr")
                resolver.insert(addrUri, addrValues)
            }
            
            // 3. Write text part if body is not empty
            var seq = 0
            if (body.isNotEmpty()) {
                val textPartValues = ContentValues().apply {
                    put(Telephony.Mms.Part.MSG_ID, mmsId)
                    put(Telephony.Mms.Part.SEQ, seq++)
                    put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                    put(Telephony.Mms.Part.CHARSET, 106) // UTF-8
                }
                
                val textPartUri = Uri.parse("content://mms/$mmsId/part")
                val insertedTextPartUri = resolver.insert(textPartUri, textPartValues)
                
                // Write text data - skip if it fails (text can be in subject or skipped for attachment-only MMS)
                insertedTextPartUri?.let { uri ->
                    try {
                        val textData = body.toByteArray(Charsets.UTF_8)
                        val partId = ContentUris.parseId(uri)
                        
                        // Try writing to part data URI
                        val partDataUri = Uri.parse("content://mms/$mmsId/part/$partId")
                        try {
                            resolver.openOutputStream(partDataUri)?.use { os ->
                                os.write(textData)
                                Log.d(TAG, "Successfully wrote text part data")
                            } ?: throw IOException("Failed to open output stream")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to write text part, text will be in subject or skipped", e)
                            // Text part writing failed, but we can continue without it
                            // The text can be in the subject or the MMS can be attachment-only
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error writing text part, continuing without text part", e)
                        // Continue - text is optional for MMS with attachments
                    }
                }
            }
            
            // 4. Write attachment parts
            attachments.forEach { attachmentUri ->
                try {
                    val mimeType = getMimeType(context, attachmentUri)
                    val fileName = getFileName(context, attachmentUri)
                    
                    val partValues = ContentValues().apply {
                        put(Telephony.Mms.Part.MSG_ID, mmsId)
                        put(Telephony.Mms.Part.SEQ, seq++)
                        put(Telephony.Mms.Part.CONTENT_TYPE, mimeType)
                        if (!fileName.isNullOrEmpty()) {
                            put(Telephony.Mms.Part.NAME, fileName)
                        }
                        // CID (Content-ID) is optional and not a standard Telephony constant
                        // Using string key directly
                        put("cid", "<part_$seq>")
                    }
                    
                    val partUri = Uri.parse("content://mms/$mmsId/part")
                    val insertedPartUri = resolver.insert(partUri, partValues)
                    
                    // Write binary data
                    insertedPartUri?.let { uri ->
                        try {
                            val partId = ContentUris.parseId(uri)
                            
                            // Read attachment data into byte array
                            val attachmentData = resolver.openInputStream(attachmentUri)?.use { inputStream ->
                                inputStream.readBytes()
                            } ?: throw IOException("Failed to read attachment data")
                            
                            // Try multiple approaches to write the data
                            var success = false
                            
                            // Approach 1: Use part data URI with mmsId in path
                            try {
                                val partDataUri = Uri.parse("content://mms/$mmsId/part/$partId")
                                resolver.openOutputStream(partDataUri)?.use { outputStream ->
                                    outputStream.write(attachmentData)
                                    success = true
                                    Log.d(TAG, "Successfully wrote part data using openOutputStream with mmsId path")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "openOutputStream with mmsId path failed, trying part-only path", e)
                            }
                            
                            // Approach 2: Use part data URI without mmsId
                            if (!success) {
                                try {
                                    val partDataUri = Uri.parse("content://mms/part/$partId")
                                    resolver.openOutputStream(partDataUri)?.use { outputStream ->
                                        outputStream.write(attachmentData)
                                        success = true
                                        Log.d(TAG, "Successfully wrote part data using openOutputStream with part-only path")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "openOutputStream with part-only path failed, trying ParcelFileDescriptor", e)
                                }
                            }
                            
                            // Approach 3: Use ParcelFileDescriptor
                            if (!success) {
                                try {
                                    val partDataUri = Uri.parse("content://mms/$mmsId/part/$partId")
                                    resolver.openFileDescriptor(partDataUri, "w")?.use { pfd ->
                                        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { outputStream ->
                                            outputStream.write(attachmentData)
                                            success = true
                                            Log.d(TAG, "Successfully wrote part data using ParcelFileDescriptor")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "ParcelFileDescriptor approach failed, trying file-based approach", e)
                                }
                            }
                            
                            // Approach 4: Write to external storage MMS directory (device-specific)
                            if (!success) {
                                try {
                                    val mmsDir = File(context.getExternalFilesDir(null), "mms")
                                    mmsDir.mkdirs()
                                    val tempFile = File(mmsDir, "part_${partId}_${System.currentTimeMillis()}.tmp")
                                    tempFile.writeBytes(attachmentData)
                                    
                                    // Try updating with file path
                                    val updateValues = ContentValues().apply {
                                        put("_data", tempFile.absolutePath)
                                    }
                                    val updated = resolver.update(uri, updateValues, null, null)
                                    
                                    if (updated > 0) {
                                        success = true
                                        Log.d(TAG, "Successfully wrote part data using external storage file")
                                        
                                        // Clean up temp file after a delay
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            if (tempFile.exists()) {
                                                tempFile.delete()
                                            }
                                        }, 10000)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "External storage file approach failed", e)
                                }
                            }
                            
                            if (!success) {
                                throw IOException("All approaches to write MMS part data failed for part $partId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing attachment data to MMS part", e)
                            throw e
                        }
                    }
                    
                    Log.d(TAG, "Wrote attachment part: $mimeType, fileName: $fileName")
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing attachment part: $attachmentUri", e)
                }
            }
            
            mmsUri
        } catch (e: Exception) {
            Log.e(TAG, "Error writing MMS to provider", e)
            null
        }
    }
    
    private fun getMimeType(context: Context, uri: Uri): String {
        return when {
            uri.scheme == "content" -> {
                context.contentResolver.getType(uri) ?: "application/octet-stream"
            }
            uri.path?.endsWith(".vcf", ignoreCase = true) == true -> "text/x-vCard"
            uri.path?.endsWith(".jpg", ignoreCase = true) == true -> "image/jpeg"
            uri.path?.endsWith(".jpeg", ignoreCase = true) == true -> "image/jpeg"
            uri.path?.endsWith(".png", ignoreCase = true) == true -> "image/png"
            uri.path?.endsWith(".gif", ignoreCase = true) == true -> "image/gif"
            uri.path?.endsWith(".mp4", ignoreCase = true) == true -> "video/mp4"
            uri.path?.endsWith(".3gp", ignoreCase = true) == true -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> {
                    val cursor = context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                it.getString(nameIndex)
                            } else null
                        } else null
                    }
                }
                "file" -> {
                    File(uri.path ?: "").name
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file name for URI: $uri", e)
            null
        }
    }
}

