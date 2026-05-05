package com.text.messages.sms.messanger.util

object LastCallCache {
    var phoneNumber: String? = null
    var callStartTime: Long = 0L
    var isIncoming: Boolean = false

    fun clear() {
        phoneNumber = null
        callStartTime = 0L
        isIncoming = false
    }
}
