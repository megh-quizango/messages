package com.text.messages.sms.messanger.data.model

import android.net.Uri
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class MmsPart : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var messageId: Long = 0
    var type: String = "" // MIME type (e.g., "image/jpeg", "text/x-vCard")
    var seq: Int = -1 // Sequence number (-1 if not set)
    var name: String? = null // Part name/filename
    var text: String? = null // Text content (for text parts)
    var contentId: String? = null // Content ID for MMS parts
    
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

