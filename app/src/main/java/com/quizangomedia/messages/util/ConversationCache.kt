package com.quizangomedia.messages.util

import android.util.Log
import com.quizangomedia.messages.data.model.Conversation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Cache for conversations to enable fast loading and filter switching.
 * Thread-safe implementation using ConcurrentHashMap.
 */
object ConversationCache {
    
    private const val TAG = "ConversationCache"
    
    // Cache entries with timestamp
    data class CacheEntry(
        val conversations: List<Conversation>,
        val timestamp: Long,
        val category: String? = null,
        val filterId: String? = null,
        val timeFilter: String? = null
    )
    
    // Primary cache: category -> conversations
    private val categoryCache = ConcurrentHashMap<String, CacheEntry>()
    
    // Custom filter cache: filterId -> conversations
    private val filterCache = ConcurrentHashMap<String, CacheEntry>()
    
    // Cache TTL: 5 minutes (300000 ms)
    private const val CACHE_TTL_MS = 300_000L
    
    // Track cache size
    private val cacheSize = AtomicLong(0)
    
    /**
     * Get cached conversations for a category.
     * Returns null if not cached or cache is expired.
     */
    fun getCached(category: String): List<Conversation>? {
        val entry = categoryCache[category]
        return if (entry != null && !isExpired(entry)) {
            Log.d(TAG, "Cache HIT for category: $category")
            entry.conversations
        } else {
            Log.d(TAG, "Cache MISS for category: $category (expired=${entry != null && isExpired(entry)})")
            null
        }
    }
    
    /**
     * Get cached conversations for a custom filter.
     * Returns null if not cached or cache is expired.
     */
    fun getCachedForFilter(filterId: String): List<Conversation>? {
        val entry = filterCache[filterId]
        return if (entry != null && !isExpired(entry)) {
            Log.d(TAG, "Cache HIT for filter: $filterId")
            entry.conversations
        } else {
            Log.d(TAG, "Cache MISS for filter: $filterId (expired=${entry != null && isExpired(entry)})")
            null
        }
    }
    
    /**
     * Cache conversations for a category.
     */
    fun cache(category: String, conversations: List<Conversation>, timeFilter: String? = null) {
        val entry = CacheEntry(
            conversations = conversations.toList(), // Create a copy
            timestamp = System.currentTimeMillis(),
            category = category,
            timeFilter = timeFilter
        )
        categoryCache[category] = entry
        Log.d(TAG, "Cached ${conversations.size} conversations for category: $category")
        updateCacheSize()
    }
    
    /**
     * Cache conversations for a custom filter.
     */
    fun cacheForFilter(filterId: String, conversations: List<Conversation>) {
        val entry = CacheEntry(
            conversations = conversations.toList(), // Create a copy
            timestamp = System.currentTimeMillis(),
            filterId = filterId
        )
        filterCache[filterId] = entry
        Log.d(TAG, "Cached ${conversations.size} conversations for filter: $filterId")
        updateCacheSize()
    }
    
    /**
     * Update a single conversation in the cache.
     * This is used for incremental updates when a conversation changes.
     */
    fun updateConversation(threadId: Long, updatedConversation: Conversation) {
        // Update in all category caches
        categoryCache.forEach { (category, entry) ->
            val conversations = entry.conversations.toMutableList()
            val index = conversations.indexOfFirst { it.threadId == threadId }
            if (index >= 0) {
                conversations[index] = updatedConversation
                // Move to top if date changed
                if (updatedConversation.date > entry.conversations[index].date) {
                    conversations.removeAt(index)
                    conversations.add(0, updatedConversation)
                }
                // Update cache with new entry but same timestamp (to keep it valid)
                categoryCache[category] = entry.copy(conversations = conversations)
                Log.d(TAG, "Updated conversation $threadId in category cache: $category")
            }
        }
        
        // Update in all filter caches
        filterCache.forEach { (filterId, entry) ->
            val conversations = entry.conversations.toMutableList()
            val index = conversations.indexOfFirst { it.threadId == threadId }
            if (index >= 0) {
                conversations[index] = updatedConversation
                // Move to top if date changed
                if (updatedConversation.date > entry.conversations[index].date) {
                    conversations.removeAt(index)
                    conversations.add(0, updatedConversation)
                }
                // Update cache with new entry but same timestamp (to keep it valid)
                filterCache[filterId] = entry.copy(conversations = conversations)
                Log.d(TAG, "Updated conversation $threadId in filter cache: $filterId")
            }
        }
    }
    
    /**
     * Remove a conversation from all caches.
     * Used when a conversation is deleted, archived, or blocked.
     */
    fun removeConversation(threadId: Long) {
        categoryCache.forEach { (category, entry) ->
            val conversations = entry.conversations.filter { it.threadId != threadId }
            if (conversations.size != entry.conversations.size) {
                categoryCache[category] = entry.copy(conversations = conversations)
                Log.d(TAG, "Removed conversation $threadId from category cache: $category")
            }
        }
        
        filterCache.forEach { (filterId, entry) ->
            val conversations = entry.conversations.filter { it.threadId != threadId }
            if (conversations.size != entry.conversations.size) {
                filterCache[filterId] = entry.copy(conversations = conversations)
                Log.d(TAG, "Removed conversation $threadId from filter cache: $filterId")
            }
        }
    }
    
    /**
     * Restore a conversation to the cache.
     * Used when a conversation is unarchived, restored from recycle bin, unblocked, or made non-private.
     * This invalidates the cache so it will be refreshed with the restored conversation.
     */
    fun restoreConversation(threadId: Long) {
        // Invalidate all caches so they will be refreshed with the restored conversation
        val categoriesToInvalidate = categoryCache.keys.toList()
        val filtersToInvalidate = filterCache.keys.toList()
        
        categoriesToInvalidate.forEach { category ->
            invalidate(category)
        }
        
        filtersToInvalidate.forEach { filterId ->
            invalidateFilter(filterId)
        }
        
        Log.d(TAG, "Invalidated all caches to restore conversation $threadId")
    }
    
    /**
     * Invalidate cache for a specific category.
     */
    fun invalidate(category: String) {
        categoryCache.remove(category)
        Log.d(TAG, "Invalidated cache for category: $category")
        updateCacheSize()
    }
    
    /**
     * Invalidate cache for a specific filter.
     */
    fun invalidateFilter(filterId: String) {
        filterCache.remove(filterId)
        Log.d(TAG, "Invalidated cache for filter: $filterId")
        updateCacheSize()
    }
    
    /**
     * Clear all caches.
     */
    fun clear() {
        categoryCache.clear()
        filterCache.clear()
        Log.d(TAG, "Cleared all caches")
        updateCacheSize()
    }
    
    /**
     * Clear expired entries from cache.
     */
    fun clearExpired() {
        val now = System.currentTimeMillis()
        val categoriesToRemove = categoryCache.filter { isExpired(it.value) }.keys
        val filtersToRemove = filterCache.filter { isExpired(it.value) }.keys
        
        categoriesToRemove.forEach { categoryCache.remove(it) }
        filtersToRemove.forEach { filterCache.remove(it) }
        
        if (categoriesToRemove.isNotEmpty() || filtersToRemove.isNotEmpty()) {
            Log.d(TAG, "Cleared ${categoriesToRemove.size} expired category entries and ${filtersToRemove.size} expired filter entries")
        }
        updateCacheSize()
    }
    
    /**
     * Check if a cache entry is expired.
     */
    private fun isExpired(entry: CacheEntry): Boolean {
        return System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS
    }
    
    /**
     * Get cache statistics.
     */
    fun getCacheStats(): String {
        val now = System.currentTimeMillis()
        val expiredCategories = categoryCache.values.count { isExpired(it) }
        val expiredFilters = filterCache.values.count { isExpired(it) }
        
        return "Categories: ${categoryCache.size} (${expiredCategories} expired), " +
               "Filters: ${filterCache.size} (${expiredFilters} expired)"
    }
    
    private fun updateCacheSize() {
        cacheSize.set((categoryCache.size + filterCache.size).toLong())
    }
}
