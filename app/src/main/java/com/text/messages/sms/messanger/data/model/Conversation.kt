package com.text.messages.sms.messanger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val threadId: Long = 0,
    val address: String = "",
    val contactName: String? = null,
    val snippet: String = "",
    val date: Long = 0,
    val unreadCount: Int = 0,
    val archived: Boolean = false,
    val blocked: Boolean = false,
    val photoUri: String? = null
)
