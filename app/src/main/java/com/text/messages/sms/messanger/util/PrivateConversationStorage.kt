package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PrivateConversationStorage {
    private const val PREFS_NAME = "private_conversations"
    private const val KEY_THREAD_IDS = "private_thread_ids"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun addThreadId(context: Context, threadId: Long) {
        val threadIds = getThreadIds(context).toMutableSet()
        threadIds.add(threadId)
        saveThreadIds(context, threadIds.toList())
    }
    
    fun removeThreadId(context: Context, threadId: Long) {
        val threadIds = getThreadIds(context).toMutableSet()
        threadIds.remove(threadId)
        saveThreadIds(context, threadIds.toList())
    }
    
    fun getThreadIds(context: Context): Set<Long> {
        val prefs = getSharedPreferences(context)
        val json = prefs.getString(KEY_THREAD_IDS, null)
        if (json == null) {
            return emptySet()
        }
        val gson = Gson()
        val type = object : TypeToken<List<Long>>() {}.type
        val list = gson.fromJson<List<Long>>(json, type) ?: emptyList()
        return list.toSet()
    }
    
    fun isPrivateConversation(context: Context, threadId: Long): Boolean {
        return getThreadIds(context).contains(threadId)
    }
    
    private fun saveThreadIds(context: Context, threadIds: List<Long>) {
        val prefs = getSharedPreferences(context)
        val gson = Gson()
        val json = gson.toJson(threadIds)
        prefs.edit().putString(KEY_THREAD_IDS, json).apply()
    }
}

