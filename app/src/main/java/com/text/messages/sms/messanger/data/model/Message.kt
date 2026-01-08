package com.text.messages.sms.messanger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val id: Long = 0,
    val threadId: Long = 0,
    val address: String = "",
    val body: String = "",
    val date: Long = 0,
    val type: Int = 0, // INBOX = 1, SENT = 2, DRAFT = 3, FAILED = 5
    val status: Int = 0, // PENDING = 0, SENT = 1, RECEIVED = 2, FAILED = 3, DELIVERED = 4
    val read: Boolean = false,
    val starred: Boolean = false,
    val mimeType: String? = null,
    val attachmentPath: String? = null,
    val messagePartCount: Int = 1,
    val otp: String? = null // Detected OTP from message body
)

object MessageType {
    const val INBOX = 1
    const val SENT = 2
    const val DRAFT = 3
    const val FAILED = 5
}

object MessageStatus {
    const val PENDING = 0
    const val SENT = 1
    const val RECEIVED = 2
    const val FAILED = 3
    const val DELIVERED = 4
}
