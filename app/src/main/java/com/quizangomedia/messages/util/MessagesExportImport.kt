package com.quizangomedia.messages.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object MessagesExportImport {
    private const val TAG = "MessagesExportImport"
    private const val MESSAGES_JSON_FILE = "messages.json"
    
    data class MessageExport(
        val id: Long,
        val threadId: Long,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int,
        val status: Int,
        val read: Boolean,
        val starred: Boolean,
        val mimeType: String?,
        val attachmentPath: String?,
        val messagePartCount: Int,
        val otp: String?
    )
    
    /**
     * Export all messages from Android SMS database to a zip file
     */
    suspend fun exportMessages(context: Context, outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting message export to: $outputUri")
            
            // Export from Android SMS database (source of truth)
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                Telephony.Sms.TYPE
            )
            
            val exportMessages = mutableListOf<MessageExport>()
            var messageIdCounter = 1L
            
            context.contentResolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val smsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    val smsType = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    
                    // Map Android SMS type to our MessageType
                    val type = when (smsType) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageType.INBOX
                        Telephony.Sms.MESSAGE_TYPE_SENT -> MessageType.SENT
                        Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageType.DRAFT
                        Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageType.FAILED
                        else -> MessageType.INBOX
                    }
                    
                    exportMessages.add(
                        MessageExport(
                            id = messageIdCounter++,
                            threadId = threadId,
                            address = address,
                            body = body,
                            date = date,
                            type = type,
                            status = if (type == MessageType.SENT) 1 else 2, // SENT or RECEIVED
                            read = read,
                            starred = false, // Not available in SMS database
                            mimeType = null,
                            attachmentPath = null,
                            messagePartCount = 1,
                            otp = null
                        )
                    )
                }
            }
            
            Log.d(TAG, "Found ${exportMessages.size} messages to export")
            
            // Convert to JSON
            val gson = Gson()
            val json = gson.toJson(exportMessages)
            
            // Write to zip file
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                    // Add messages.json to zip
                    val entry = ZipEntry(MESSAGES_JSON_FILE)
                    zipOut.putNextEntry(entry)
                    zipOut.write(json.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            }
            
            Log.d(TAG, "Export completed successfully. Exported ${exportMessages.size} messages")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting messages", e)
            false
        }
    }
    
    /**
     * Import messages from a zip file and add them to Realm database
     */
    suspend fun importMessages(context: Context, inputUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting message import from: $inputUri")
            
            // Read zip file
            val jsonContent = context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == MESSAGES_JSON_FILE) {
                            // Read JSON content
                            val buffer = ByteArray(8192)
                            val output = ByteArrayOutputStream()
                            var bytesRead: Int
                            while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                            return@use String(output.toByteArray(), Charsets.UTF_8)
                        }
                        entry = zipIn.nextEntry
                    }
                    null
                }
            } ?: throw IOException("Could not find messages.json in zip file")
            
            // Parse JSON
            val gson = Gson()
            val type = object : TypeToken<List<MessageExport>>() {}.type
            val importMessages: List<MessageExport> = gson.fromJson(jsonContent, type)
            
            Log.d(TAG, "Parsed ${importMessages.size} messages from JSON")
            
            // Import to Realm database and Android SMS database
            val realm = MessagesApp.realm
            var importedCount = 0
            var skippedCount = 0
            var smsImportedCount = 0
            
            realm.writeBlocking {
                importMessages.forEach { exportMsg ->
                    // Check if message already exists (by address, body, and date to avoid duplicates)
                    // Use a time window of ±1 second to account for timestamp differences
                    val existing = query(
                        Message::class, 
                        "threadId == ${exportMsg.threadId} AND address == '${exportMsg.address}' AND date >= ${exportMsg.date - 1000} AND date <= ${exportMsg.date + 1000}"
                    ).first().find()
                    
                    if (existing == null) {
                        // Create new message in Realm
                        val message = Message()
                        message.id = exportMsg.id
                        message.threadId = exportMsg.threadId
                        message.address = exportMsg.address
                        message.body = exportMsg.body
                        message.date = exportMsg.date
                        message.type = exportMsg.type
                        message.status = exportMsg.status
                        message.read = exportMsg.read
                        message.starred = exportMsg.starred
                        message.mimeType = exportMsg.mimeType
                        message.attachmentPath = exportMsg.attachmentPath
                        message.messagePartCount = exportMsg.messagePartCount
                        message.otp = exportMsg.otp
                        copyToRealm(message)
                        importedCount++
                        
                        // Also write to Android SMS database so it appears in recycler view
                        try {
                            val values = ContentValues().apply {
                                put(Telephony.Sms.ADDRESS, exportMsg.address)
                                put(Telephony.Sms.BODY, exportMsg.body)
                                put(Telephony.Sms.DATE, exportMsg.date)
                                put(Telephony.Sms.READ, if (exportMsg.read) 1 else 0)
                                // Map message type to Android SMS type
                                val smsType = when (exportMsg.type) {
                                    MessageType.INBOX -> Telephony.Sms.MESSAGE_TYPE_INBOX
                                    MessageType.SENT -> Telephony.Sms.MESSAGE_TYPE_SENT
                                    MessageType.DRAFT -> Telephony.Sms.MESSAGE_TYPE_DRAFT
                                    MessageType.FAILED -> Telephony.Sms.MESSAGE_TYPE_FAILED
                                    else -> Telephony.Sms.MESSAGE_TYPE_INBOX
                                }
                                put(Telephony.Sms.TYPE, smsType)
                                // Thread ID will be set automatically by Android, but we can set it explicitly
                                put(Telephony.Sms.THREAD_ID, exportMsg.threadId)
                            }
                            
                            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                            if (uri != null) {
                                smsImportedCount++
                                Log.d(TAG, "Inserted message to SMS database: $uri")
                            } else {
                                Log.w(TAG, "Failed to insert message to SMS database (may require default SMS app)")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not insert message to SMS database (may require default SMS app): ${e.message}")
                            // Continue even if SMS database insert fails
                        }
                    } else {
                        skippedCount++
                    }
                }
            }
            
            Log.d(TAG, "Import completed. Realm imported: $importedCount, SMS imported: $smsImportedCount, Skipped (duplicates): $skippedCount")
            importedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error importing messages", e)
            -1
        }
    }
}

