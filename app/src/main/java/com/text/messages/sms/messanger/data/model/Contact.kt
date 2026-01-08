package com.text.messages.sms.messanger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val phoneNumber: String = "",
    val displayName: String = "",
    val photoUri: String? = null,
    val lastContacted: Long = 0
)
