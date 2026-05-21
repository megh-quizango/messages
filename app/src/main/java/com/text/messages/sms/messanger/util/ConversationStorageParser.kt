package com.text.messages.sms.messanger.util

import com.google.gson.Gson
import com.text.messages.sms.messanger.ui.archive.ArchivedMessageData
import com.text.messages.sms.messanger.ui.main.DeletedConversationData

object ConversationStorageParser {

    fun parseArchivedMessages(
        json: String?,
        gson: Gson = Gson()
    ): MutableList<ArchivedMessageData> {
        if (json.isNullOrBlank()) return mutableListOf()

        return runCatching {
            gson.fromJson(json, Array<ArchivedMessageData>::class.java)
                ?.toMutableList()
                ?: mutableListOf()
        }.getOrElse { mutableListOf() }
    }

    fun parseDeletedConversations(
        json: String?,
        gson: Gson = Gson()
    ): MutableList<DeletedConversationData> {
        if (json.isNullOrBlank()) return mutableListOf()

        return runCatching {
            gson.fromJson(json, Array<DeletedConversationData>::class.java)
                ?.toMutableList()
                ?: mutableListOf()
        }.getOrElse { mutableListOf() }
    }
}
