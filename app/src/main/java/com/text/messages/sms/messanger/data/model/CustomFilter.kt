package com.text.messages.sms.messanger.data.model

data class CustomFilter(
    val id: String,
    val name: String,
    val threadIds: MutableList<Long> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

