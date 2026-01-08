package com.text.messages.sms.messanger.util

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Utility class for SIM card related operations.
 * 
 * IMPORTANT: This class is for UI-only operations (badges, display).
 * NEVER use these methods during SMS sending - use explicit subscription selection instead.
 */
object SimHelper {
    private const val TAG = "SimHelper"
    
    // Cache for SIM subscription IDs to avoid repeated queries
    private val simCache = mutableMapOf<Long, Int>()
    
    /**
     * Gets a cached subscription ID for a thread, if available.
     */
    fun getCachedSubId(threadId: Long): Int? = simCache[threadId]
    
    /**
     * Caches a subscription ID for a thread.
     */
    fun cacheSubId(threadId: Long, subId: Int) {
        simCache[threadId] = subId
    }
    
    /**
     * Clears the cache. Call this when conversations are updated.
     */
    fun clearCache() {
        simCache.clear()
    }
    
    /**
     * Gets the subscription ID of the last message in a conversation thread.
     * 
     * This method is UI-ONLY and should be used for:
     * - Conversation list badges
     * - Display purposes
     * 
     * NEVER use this for:
     * - SMS sending
     * - Reply actions
     * - Notification actions
     * 
     * @param context The context
     * @param threadId The thread ID
     * @return The subscription ID, or -1 if not found or error
     */
    fun getLastMessageSubId(context: Context, threadId: Long): Int {
        if (threadId <= 0) {
            return -1
        }
        
        // Check cache first to avoid repeated queries
        simCache[threadId]?.let { cachedSubId ->
            Log.d(TAG, "getLastMessageSubId: Using cached subId=$cachedSubId for threadId=$threadId")
            return cachedSubId
        }
        
        return try {
            // Use the safe conversation URI that works for both SMS and MMS
            val uri = Uri.parse("content://mms-sms/conversations/$threadId")
            
            // Must include normalized_date in projection for ORDER BY to work
            // Android normalizes SMS (date * 1) and MMS (date * 1000) timestamps
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("sub_id", "normalized_date"),
                null,
                null,
                "normalized_date DESC"  // Use normalized_date, not date
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("sub_id")
                    if (idx != -1) {
                        val subId = it.getInt(idx)
                        // Cache the result
                        cacheSubId(threadId, subId)
                        Log.d(TAG, "getLastMessageSubId: threadId=$threadId, subId=$subId")
                        return subId
                    }
                }
            }
            
            Log.d(TAG, "getLastMessageSubId: threadId=$threadId, no sub_id found")
            -1
        } catch (e: Exception) {
            Log.e(TAG, "getLastMessageSubId: Error getting sub_id for threadId=$threadId", e)
            -1
        }
    }
    
    /**
     * Gets a color for a subscription ID to display SIM badges.
     * Returns different colors for different SIM cards.
     * 
     * @param subId The subscription ID
     * @return A color hex string
     */
    fun getSimColor(subId: Int): String {
        // Return different colors for different SIM cards
        // SIM 1: Blue, SIM 2: Green, etc.
        return when (subId) {
            0 -> "#0C56CF"  // Blue for SIM 1
            1 -> "#4CAF50"  // Green for SIM 2
            2 -> "#FF9800"  // Orange for SIM 3
            3 -> "#9C27B0"  // Purple for SIM 4
            else -> "#757575"  // Gray for unknown/default
        }
    }
}

