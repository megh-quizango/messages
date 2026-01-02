package com.quizangomedia.messages.ui.main

data class DeletedConversationData(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val deletedAt: Long
)

