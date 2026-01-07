package com.text.messages.sms.messanger.ui.caller

data class CallHistoryItem(
    val id: Long,
    val number: String,
    val date: Long,
    val duration: Long,
    val type: Int, // CallLog.Calls.INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE
    val name: String? = null
)

