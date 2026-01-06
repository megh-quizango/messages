package com.quizangomedia.messages.util

import android.content.Context
import android.content.SharedPreferences

object BlockedConversationStorage {
    private const val PREFS_NAME = "MessagesPrefs"
    private const val KEY_BLOCKED_CONVERSATIONS = "blocked_conversations"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun addThreadId(context: Context, threadId: Long) {
        val threadIds = getThreadIds(context).toMutableSet()
        threadIds.add(threadId)
        saveThreadIds(context, threadIds)
    }
    
    fun removeThreadId(context: Context, threadId: Long) {
        val threadIds = getThreadIds(context).toMutableSet()
        threadIds.remove(threadId)
        saveThreadIds(context, threadIds)
    }
    
    fun getThreadIds(context: Context): Set<Long> {
        val prefs = getSharedPreferences(context)
        val blockedSet = prefs.getStringSet(KEY_BLOCKED_CONVERSATIONS, emptySet()) ?: emptySet()
        return blockedSet.mapNotNull { it.toLongOrNull() }.toSet()
    }
    
    fun isBlocked(context: Context, threadId: Long): Boolean {
        return getThreadIds(context).contains(threadId)
    }
    
    private fun saveThreadIds(context: Context, threadIds: Set<Long>) {
        val prefs = getSharedPreferences(context)
        val stringSet = threadIds.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_BLOCKED_CONVERSATIONS, stringSet).apply()
    }
}

