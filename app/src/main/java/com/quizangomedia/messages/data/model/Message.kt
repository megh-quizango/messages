package com.quizangomedia.messages.data.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class Message : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var threadId: Long = 0
    var address: String = ""
    var body: String = ""
    var date: Long = 0
    var type: Int = 0 // INBOX = 1, SENT = 2, DRAFT = 3, FAILED = 5
    var status: Int = 0 // PENDING = 0, SENT = 1, RECEIVED = 2, FAILED = 3, DELIVERED = 4
    var read: Boolean = false
    var starred: Boolean = false
    var mimeType: String? = null
    var attachmentPath: String? = null
    var messagePartCount: Int = 1
}

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

