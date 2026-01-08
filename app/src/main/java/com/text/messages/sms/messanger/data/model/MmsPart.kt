package com.text.messages.sms.messanger.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mms_parts")
data class MmsPart(
    @PrimaryKey
    val id: Long = 0,
    val messageId: Long = 0,
    val type: String = "", // MIME type (e.g., "image/jpeg", "text/x-vCard")
    val seq: Int = -1, // Sequence number (-1 if not set)
    val name: String? = null, // Part name/filename
    val text: String? = null, // Text content (for text parts)
    val contentId: String? = null // Content ID for MMS parts
) {
    // Get URI for part content
    fun getUri(): Uri {
        return Uri.parse("content://mms/part/$id")
    }
    
    // Get URI with cache busting
    fun getUriForCacheBusting(): Uri {
        return getUri().buildUpon()
            .appendQueryParameter("_reload", System.currentTimeMillis().toString())
            .build()
    }
    
    // Get summary text based on type
    fun getSummary(): String? {
        return when {
            type == "text/plain" -> text
            type == "text/x-vCard" -> "Contact card"
            type.startsWith("image/") -> "Photo"
            type.startsWith("video/") -> "Video"
            type.startsWith("audio/") -> "Audio"
            else -> null
        }
    }
}
