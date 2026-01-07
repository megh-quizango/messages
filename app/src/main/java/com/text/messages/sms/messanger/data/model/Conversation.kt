package com.text.messages.sms.messanger.data.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class Conversation : RealmObject {
    @PrimaryKey
    var threadId: Long = 0
    var address: String = ""
    var contactName: String? = null
    var snippet: String = ""
    var date: Long = 0
    var unreadCount: Int = 0
    var archived: Boolean = false
    var blocked: Boolean = false
    var photoUri: String? = null
}

