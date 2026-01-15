package com.text.messages.sms.messanger.ui.main

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.util.CustomFilterStorage
import com.text.messages.sms.messanger.util.PrivateConversationStorage
import com.text.messages.sms.messanger.util.BlockedConversationStorage
import com.text.messages.sms.messanger.util.ConversationCache
import com.text.messages.sms.messanger.util.OtpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

class MainViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    private val _conversations = MutableLiveData<List<Conversation>>()
    val conversations: LiveData<List<Conversation>> = _conversations
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Track loading jobs for cancellation
    private var currentLoadJob: Job? = null
    private var currentFilterLoadJob: Job? = null
    
    fun loadConversations(
        category: String = "All",
        showLoading: Boolean = true,
        timeFilter: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        useCache: Boolean = true,
        forceRefresh: Boolean = false
    ) {
        // Cancel previous loading job
        currentLoadJob?.cancel()
        
        // Try cache first if enabled (even if forcing refresh).
        // If forceRefresh=true, we still post cache immediately for instant UI, then load from device.
        if (useCache && timeFilter == null) {
            val cached = ConversationCache.getCached(category)
            if (cached != null) {
                Log.d(TAG, "Using cached conversations for category: $category (${cached.size} items, forceRefresh=$forceRefresh)")
                if (!forceRefresh) {
                    // If showLoading is true, briefly show loading state for better UX
                    if (showLoading) {
                        viewModelScope.launch {
                            _isLoading.postValue(true)
                            _conversations.postValue(cached!!)
                            // Small delay to ensure loading state is visible briefly
                            kotlinx.coroutines.delay(100)
                            _isLoading.postValue(false)
                        }
                    } else {
                        _conversations.postValue(cached!!)
                        // Still refresh in background to ensure cache is up to date
                        loadConversationsInBackground(category, timeFilter, startDate, endDate)
                    }
                    return
                } else {
                    // Force refresh: show cache now, refresh from device below
                    _conversations.postValue(cached!!)
                }
            }
        }
        
        // Load from device
        currentLoadJob = viewModelScope.launch {
            try {
                if (showLoading) {
                    _isLoading.postValue(true)
                }
                val conversations = withContext(Dispatchers.IO) {
                    // Check if job was cancelled
                    if (!isActive) {
                        return@withContext emptyList<Conversation>()
                    }
                    loadConversationsFromDevice(category, timeFilter, startDate, endDate)
                }
                
                // Only update if job wasn't cancelled
                if (isActive) {
                    _conversations.postValue(conversations)
                    // Cache the result (only if no time filter, as time filters are dynamic)
                    if (timeFilter == null) {
                        ConversationCache.cache(category, conversations)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    e.printStackTrace()
                    _conversations.postValue(emptyList())
                }
            } finally {
                if (showLoading && isActive) {
                    _isLoading.postValue(false)
                }
            }
        }
    }
    
    private fun loadConversationsInBackground(
        category: String,
        timeFilter: String?,
        startDate: Long?,
        endDate: Long?
    ) {
        viewModelScope.launch {
            try {
                val conversations = withContext(Dispatchers.IO) {
                    if (!isActive) return@withContext emptyList<Conversation>()
                    loadConversationsFromDevice(category, timeFilter, startDate, endDate)
                }
                if (isActive && timeFilter == null) {
                    ConversationCache.cache(category, conversations)
                }
            } catch (e: Exception) {
                // Silently fail background refresh
                Log.w(TAG, "Background refresh failed for category: $category", e)
            }
        }
    }
    
    fun loadConversationsForCustomFilter(
        context: android.content.Context, 
        filterId: String,
        useCache: Boolean = true,
        forceRefresh: Boolean = false
    ) {
        // Cancel previous filter loading job
        currentFilterLoadJob?.cancel()
        
        // Try cache first if enabled (even if forcing refresh).
        // If forceRefresh=true, we still post cache immediately for instant UI, then load from device.
        if (useCache) {
            val cached = ConversationCache.getCachedForFilter(filterId)
            if (cached != null) {
                Log.d(TAG, "Using cached conversations for filter: $filterId (${cached.size} items, forceRefresh=$forceRefresh)")
                if (!forceRefresh) {
                    // Briefly show loading state for better UX
                    viewModelScope.launch {
                        _isLoading.postValue(true)
                        _conversations.postValue(cached!!)
                        // Small delay to ensure loading state is visible briefly
                        kotlinx.coroutines.delay(100)
                        _isLoading.postValue(false)
                        // Still refresh in background
                        loadConversationsForCustomFilterInBackground(context, filterId)
                    }
                    return
                } else {
                    _conversations.postValue(cached!!)
                }
            }
        }
        
        // Load from device
        currentFilterLoadJob = viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                val conversations = withContext(Dispatchers.IO) {
                    if (!isActive) {
                        return@withContext emptyList<Conversation>()
                    }
                    loadConversationsForCustomFilterFromDevice(context, filterId)
                }
                
                if (isActive) {
                    _conversations.postValue(conversations)
                    ConversationCache.cacheForFilter(filterId, conversations)
                }
            } catch (e: Exception) {
                if (isActive) {
                    e.printStackTrace()
                    _conversations.postValue(emptyList())
                }
            } finally {
                if (isActive) {
                    _isLoading.postValue(false)
                }
            }
        }
    }
    
    private fun loadConversationsForCustomFilterInBackground(
        context: android.content.Context,
        filterId: String
    ) {
        viewModelScope.launch {
            try {
                val conversations = withContext(Dispatchers.IO) {
                    if (!isActive) return@withContext emptyList<Conversation>()
                    loadConversationsForCustomFilterFromDevice(context, filterId)
                }
                if (isActive) {
                    ConversationCache.cacheForFilter(filterId, conversations)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background refresh failed for filter: $filterId", e)
            }
        }
    }
    
    /**
     * Cancel any ongoing loading operations.
     */
    fun cancelLoading() {
        currentLoadJob?.cancel()
        currentFilterLoadJob?.cancel()
        currentLoadJob = null
        currentFilterLoadJob = null
        // Clear loading state when cancelling
        _isLoading.postValue(false)
        Log.d(TAG, "Cancelled all loading operations")
    }
    
    /**
     * Force clear loading state.
     * Used when conversations are received to ensure loading stops.
     */
    fun clearLoadingState() {
        _isLoading.postValue(false)
    }
    
    /**
     * Pre-load conversations for a category in the background.
     * Used by LandingActivity to pre-load while user is on splash screen.
     */
    fun preloadConversations(category: String = "All") {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Pre-loading conversations for category: $category")
                val conversations = withContext(Dispatchers.IO) {
                    if (!isActive) return@withContext emptyList<Conversation>()
                    loadConversationsFromDevice(category, null, null, null)
                }
                if (isActive) {
                    ConversationCache.cache(category, conversations)
                    Log.d(TAG, "Pre-loaded ${conversations.size} conversations for category: $category")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pre-load failed for category: $category", e)
            }
        }
    }
    
    /**
     * Load a single conversation by threadId quickly.
     * Used when restoring conversations to add them immediately without full refresh.
     */
    fun loadSingleConversation(
        context: Context, 
        threadId: Long, 
        category: String? = null,
        onLoaded: (Conversation?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val conversation = withContext(Dispatchers.IO) {
                    loadSingleConversationFromDevice(context, threadId, category)
                }
                
                if (conversation != null) {
                    // Update cache with the restored conversation
                    if (category != null) {
                        val cached = ConversationCache.getCached(category)
                        if (cached != null) {
                            val updated = (cached + conversation).distinctBy { it.threadId }
                                .sortedByDescending { it.date }
                            ConversationCache.cache(category, updated)
                        }
                    }
                }
                
                onLoaded(conversation)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading single conversation for threadId: $threadId", e)
                onLoaded(null)
            }
        }
    }
    
    private suspend fun loadSingleConversationFromDevice(context: Context, threadId: Long, category: String?): Conversation? {
        val conversationsMap = mutableMapOf<Long, Conversation>()
        
        // Load deleted, archived, private, and blocked lists
        val deletedThreadIds = getDeletedThreadIds(context)
        val archivedThreadIds = getArchivedThreadIds(context)
        val privateThreadIds = PrivateConversationStorage.getThreadIds(context)
        val blockedThreadIds = BlockedConversationStorage.getThreadIds(context)
        
        // Skip if still in any of these lists
        if (deletedThreadIds.contains(threadId) || archivedThreadIds.contains(threadId) || 
            privateThreadIds.contains(threadId) || blockedThreadIds.contains(threadId)) {
            return null
        }
        
        // Pre-load contact phone numbers for Personal filter
        val contactPhoneNumbers = if (category == "Personal") {
            loadContactPhoneNumbers(context)
        } else {
            null
        }
        
        // Query SMS for this specific thread
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                
                // Filter by category if specified
                if (category != null && !matchesCategory(body, category, address, contactPhoneNumbers)) {
                    continue
                }
                
                val conversation = conversationsMap.getOrPut(threadId) {
                    Conversation(
                        threadId = threadId,
                        address = address,
                        snippet = body,
                        date = date,
                        unreadCount = 0
                    )
                }
                
                // Update with latest message
                val updatedConversation = if (date > conversation.date) {
                    conversation.copy(snippet = body, date = date)
                } else {
                    conversation
                }
                
                // Count unread messages
                val finalConversation = if (!read && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    updatedConversation.copy(unreadCount = updatedConversation.unreadCount + 1)
                } else {
                    updatedConversation
                }
                
                conversationsMap[threadId] = finalConversation
            }
        }
        
        // Also check MMS from database
        withContext(Dispatchers.IO) {
            loadMmsConversationsFromRealm(conversationsMap, category ?: "All", contactPhoneNumbers, 
                deletedThreadIds, archivedThreadIds, privateThreadIds, blockedThreadIds)
        }
        
        // Get contact name
        val conversation = conversationsMap[threadId]
        if (conversation != null) {
            loadContactNamesBatch(context, listOf(conversation), conversationsMap)
        }
        
        return conversationsMap[threadId]
    }
    
    fun loadPrivateConversations(context: android.content.Context) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                val conversations = withContext(Dispatchers.IO) {
                    loadPrivateConversationsFromDevice(context)
                }
                _conversations.postValue(conversations)
            } catch (e: Exception) {
                e.printStackTrace()
                _conversations.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    fun loadBlockedConversations(context: android.content.Context) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                val conversations = withContext(Dispatchers.IO) {
                    loadBlockedConversationsFromDevice(context)
                }
                _conversations.postValue(conversations)
            } catch (e: Exception) {
                e.printStackTrace()
                _conversations.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    private fun loadBlockedConversationsFromDevice(context: android.content.Context): List<Conversation> {
        Log.d(TAG, "loadBlockedConversationsFromDevice: Starting")
        val blockedThreadIds = BlockedConversationStorage.getThreadIds(context)
        if (blockedThreadIds.isEmpty()) {
            Log.d(TAG, "loadBlockedConversationsFromDevice: No blocked conversations")
            return emptyList()
        }
        
        val conversationsMap = mutableMapOf<Long, Conversation>()
        val threadIdSet = blockedThreadIds.toSet()
        
        // Query SMS from device for specific thread IDs
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.THREAD_ID} IN (${threadIdSet.joinToString(",") { "?" }})"
        val selectionArgs = threadIdSet.map { it.toString() }.toTypedArray()
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        Log.d(TAG, "loadBlockedConversationsFromDevice: Querying ${threadIdSet.size} thread IDs")
        
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                
                // Only process threads that are in blocked list
                if (!threadIdSet.contains(threadId)) {
                    continue
                }
                
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                
                val conversation = conversationsMap.getOrPut(threadId) {
                    Conversation(
                        threadId = threadId,
                        address = address,
                        snippet = body,
                        date = date,
                        unreadCount = 0
                    )
                }
                
                // Update with latest message
                val updatedConversation = if (date > conversation.date) {
                    conversation.copy(snippet = body, date = date)
                } else {
                    conversation
                }
                
                // Count unread messages
                val finalConversation = if (!read && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    updatedConversation.copy(unreadCount = updatedConversation.unreadCount + 1)
                } else {
                    updatedConversation
                }
                
                conversationsMap[threadId] = finalConversation
            }
        }
        
        // Get contact names in batch
        loadContactNamesBatch(context, conversationsMap.values, conversationsMap)
        
        val sortedConversations = conversationsMap.values.sortedByDescending { it.date }
        Log.d(TAG, "loadBlockedConversationsFromDevice: Returning ${sortedConversations.size} conversations")
        return sortedConversations
    }
    
    private fun loadPrivateConversationsFromDevice(context: android.content.Context): List<Conversation> {
        Log.d(TAG, "loadPrivateConversationsFromDevice: Starting")
        val privateThreadIds = PrivateConversationStorage.getThreadIds(context)
        if (privateThreadIds.isEmpty()) {
            Log.d(TAG, "loadPrivateConversationsFromDevice: No private conversations")
            return emptyList()
        }
        
        val conversationsMap = mutableMapOf<Long, Conversation>()
        val threadIdSet = privateThreadIds.toSet()
        
        // Query SMS from device for specific thread IDs
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.THREAD_ID} IN (${threadIdSet.joinToString(",") { "?" }})"
        val selectionArgs = threadIdSet.map { it.toString() }.toTypedArray()
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        Log.d(TAG, "loadPrivateConversationsFromDevice: Querying ${threadIdSet.size} thread IDs")
        
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                
                // Only process threads that are in private list
                if (!threadIdSet.contains(threadId)) {
                    continue
                }
                
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                
                val conversation = conversationsMap.getOrPut(threadId) {
                    Conversation(
                        threadId = threadId,
                        address = address,
                        snippet = body,
                        date = date,
                        unreadCount = 0
                    )
                }
                
                // Update with latest message
                val updatedConversation = if (date > conversation.date) {
                    conversation.copy(snippet = body, date = date)
                } else {
                    conversation
                }
                
                // Count unread messages
                val finalConversation = if (!read && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    updatedConversation.copy(unreadCount = updatedConversation.unreadCount + 1)
                } else {
                    updatedConversation
                }
                
                conversationsMap[threadId] = finalConversation
            }
        }
        
        // Get contact names in batch
        loadContactNamesBatch(context, conversationsMap.values, conversationsMap)
        
        val sortedConversations = conversationsMap.values.sortedByDescending { it.date }
        Log.d(TAG, "loadPrivateConversationsFromDevice: Returning ${sortedConversations.size} conversations")
        return sortedConversations
    }
    
    private fun loadConversationsForCustomFilterFromDevice(context: android.content.Context, filterId: String): List<Conversation> {
        Log.d(TAG, "loadConversationsForCustomFilterFromDevice: Starting for filterId: $filterId")
        val filter = CustomFilterStorage.getFilter(context, filterId)
        if (filter == null || filter.threadIds.isEmpty()) {
            Log.d(TAG, "loadConversationsForCustomFilterFromDevice: Filter not found or empty")
            return emptyList()
        }
        
        val conversationsMap = mutableMapOf<Long, Conversation>()
        val threadIdSet = filter.threadIds.toSet()
        
        // Query SMS from device for specific thread IDs
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.THREAD_ID} IN (${threadIdSet.joinToString(",") { "?" }})"
        val selectionArgs = threadIdSet.map { it.toString() }.toTypedArray()
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        Log.d(TAG, "loadConversationsForCustomFilterFromDevice: Querying ${threadIdSet.size} thread IDs")
        
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                
                // Only process threads that are in the filter
                if (!threadIdSet.contains(threadId)) {
                    continue
                }
                
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                
                val conversation = conversationsMap.getOrPut(threadId) {
                    Conversation(
                        threadId = threadId,
                        address = address,
                        snippet = body,
                        date = date,
                        unreadCount = 0
                    )
                }
                
                // Update with latest message
                val updatedConversation = if (date > conversation.date) {
                    conversation.copy(snippet = body, date = date)
                } else {
                    conversation
                }
                
                // Count unread messages
                val finalConversation = if (!read && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    updatedConversation.copy(unreadCount = updatedConversation.unreadCount + 1)
                } else {
                    updatedConversation
                }
                
                conversationsMap[threadId] = finalConversation
            }
        }
        
        // Get contact names in batch
        loadContactNamesBatch(context, conversationsMap.values, conversationsMap)
        
        val sortedConversations = conversationsMap.values.sortedByDescending { it.date }
        Log.d(TAG, "loadConversationsForCustomFilterFromDevice: Returning ${sortedConversations.size} conversations")
        return sortedConversations
    }
    
    fun markAllAsRead() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val context = MessagesApp.instance
                    val values = ContentValues().apply {
                        put(Telephony.Sms.READ, 1)
                    }
                    val rowsUpdated = context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.READ} = ?",
                        arrayOf("0")
                    )
                    Log.d(TAG, "markAllAsRead: Updated $rowsUpdated messages to read")
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking all messages as read", e)
                }
            }
        }
    }
    
    private suspend fun loadConversationsFromDevice(
        category: String,
        timeFilter: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): List<Conversation> {
        Log.d(TAG, "loadConversationsFromDevice: Starting for category: $category")
        val context = MessagesApp.instance
        val conversationsMap = mutableMapOf<Long, Conversation>()
        
        // Load deleted conversation IDs from recycle bin
        val deletedThreadIds = getDeletedThreadIds(context)
        Log.d(TAG, "loadConversationsFromDevice: Deleted threadIds: ${deletedThreadIds.size}")
        
        // Load archived conversation IDs
        val archivedThreadIds = getArchivedThreadIds(context)
        Log.d(TAG, "loadConversationsFromDevice: Archived threadIds: ${archivedThreadIds.size}")
        
        // Load private conversation IDs (exclude from main activity)
        val privateThreadIds = PrivateConversationStorage.getThreadIds(context)
        Log.d(TAG, "loadConversationsFromDevice: Private threadIds: ${privateThreadIds.size}")
        
        // Load blocked conversation IDs (exclude from main activity)
        val blockedThreadIds = BlockedConversationStorage.getThreadIds(context)
        Log.d(TAG, "loadConversationsFromDevice: Blocked threadIds: ${blockedThreadIds.size}")
        
        // Pre-load contact phone numbers for Personal filter (only if needed)
        val contactPhoneNumbers = if (category == "Personal") {
            loadContactPhoneNumbers(context)
        } else {
            null
        }
        
        // Query SMS from device
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )
        
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        // Build time filter
        val timeFilterSelection = buildTimeFilterSelection(timeFilter, startDate, endDate)
        
        // Use SQL filtering for simple categories to reduce data loaded
        val categorySelection = when (category) {
            "OTPs" -> buildOTPSelection()
            "Offers" -> buildOfferSelection()
            "Transactions" -> buildTransactionSelection()
            else -> null // Load all for "All" and "Personal" (Personal needs all to check contacts)
        }
        
        // Combine category and time filters
        val selection = when {
            categorySelection != null && timeFilterSelection != null -> {
                "$categorySelection AND $timeFilterSelection"
            }
            categorySelection != null -> categorySelection
            timeFilterSelection != null -> timeFilterSelection
            else -> null
        }
        
        val categorySelectionArgs = when (category) {
            "OTPs" -> buildOTPSelectionArgs()
            "Offers" -> buildOfferSelectionArgs()
            "Transactions" -> buildTransactionSelectionArgs()
            else -> null
        }
        
        val timeFilterArgs = buildTimeFilterArgs(timeFilter, startDate, endDate)
        
        // Combine selection args
        val selectionArgs = when {
            categorySelectionArgs != null && timeFilterArgs != null -> {
                categorySelectionArgs + timeFilterArgs
            }
            categorySelectionArgs != null -> categorySelectionArgs
            timeFilterArgs != null -> timeFilterArgs
            else -> null
        }
        
        Log.d(TAG, "loadConversationsFromDevice: Querying SMS with selection: $selection")
        
        var totalMessagesProcessed = 0
        var messagesForThread132 = 0
        var latestDateForThread132 = 0L
        
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            Log.d(TAG, "loadConversationsFromDevice: Cursor count: ${cursor.count}")
            while (cursor.moveToNext()) {
                totalMessagesProcessed++
                val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                
                // Skip conversations that are in recycle bin
                if (deletedThreadIds.contains(threadId)) {
                    continue
                }
                
                // Skip conversations that are archived
                if (archivedThreadIds.contains(threadId)) {
                    continue
                }
                
                // Skip conversations that are private
                if (privateThreadIds.contains(threadId)) {
                    continue
                }
                
                // Skip conversations that are blocked
                if (blockedThreadIds.contains(threadId)) {
                    continue
                }
                
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                
                // Track thread 132 specifically
                if (threadId == 132L) {
                    messagesForThread132++
                    if (date > latestDateForThread132) {
                        latestDateForThread132 = date
                    }
                    Log.d(TAG, "loadConversationsFromDevice: Thread 132 message - ID: $messageId, Date: $date, Body: ${body.take(20)}..., Read: $read, Type: $type")
                }
                
                // Filter by category - for Personal, check contact set
                if (!matchesCategory(body, category, address, contactPhoneNumbers)) {
                    if (threadId == 132L) {
                        Log.d(TAG, "loadConversationsFromDevice: Thread 132 message filtered out by category")
                    }
                    continue
                }
                
                val conversation = conversationsMap.getOrPut(threadId) {
                    Log.d(TAG, "loadConversationsFromDevice: Creating new conversation for threadId: $threadId, address: $address, date: $date")
                    Conversation(
                        threadId = threadId,
                        address = address,
                        snippet = body,
                        date = date,
                        unreadCount = 0
                    )
                }
                
                // Update with latest message
                val updatedConversation = if (date > conversation.date) {
                    Log.d(TAG, "loadConversationsFromDevice: Updating conversation threadId: $threadId, oldDate: ${conversation.date}, newDate: $date")
                    conversation.copy(snippet = body, date = date)
                } else {
                    conversation
                }
                
                // Count unread messages
                val finalConversation = if (!read && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    updatedConversation.copy(unreadCount = updatedConversation.unreadCount + 1)
                } else {
                    updatedConversation
                }
                
                conversationsMap[threadId] = finalConversation
            }
        }
        
        Log.d(TAG, "loadConversationsFromDevice: Total messages processed: $totalMessagesProcessed")
        Log.d(TAG, "loadConversationsFromDevice: Messages for thread 132: $messagesForThread132, Latest date: $latestDateForThread132")
        if (conversationsMap.containsKey(132L)) {
            val conv132 = conversationsMap[132L]!!
            Log.d(TAG, "loadConversationsFromDevice: Conversation 132 final state - date: ${conv132.date}, snippet: ${conv132.snippet.take(20)}..., unreadCount: ${conv132.unreadCount}")
        } else {
            Log.d(TAG, "loadConversationsFromDevice: Conversation 132 NOT FOUND in conversationsMap!")
        }
        
        // Also load MMS messages from database and merge with SMS conversations
        withContext(Dispatchers.IO) {
            loadMmsConversationsFromRealm(conversationsMap, category, contactPhoneNumbers, deletedThreadIds, archivedThreadIds, privateThreadIds, blockedThreadIds)
        }
        
        // Get contact names in batch for better performance
        loadContactNamesBatch(context, conversationsMap.values, conversationsMap)
        
        val sortedConversations = conversationsMap.values.sortedByDescending { it.date }
        Log.d(TAG, "loadConversationsFromDevice: Returning ${sortedConversations.size} conversations")
        return sortedConversations
    }
    
    private suspend fun loadMmsConversationsFromRealm(
        conversationsMap: MutableMap<Long, Conversation>,
        category: String,
        contactPhoneNumbers: Set<String>?,
        deletedThreadIds: Set<Long>,
        archivedThreadIds: Set<Long>,
        privateThreadIds: Set<Long> = emptySet(),
        blockedThreadIds: Set<Long> = emptySet()
    ) {
        try {
            val database = MessagesApp.database
            val messageDao = database.messageDao()
            
            // Query MMS messages from database (messages with attachments)
            val mmsMessages = messageDao.getAllMmsMessages()
            
            Log.d(TAG, "loadMmsConversationsFromRealm: Found ${mmsMessages.size} MMS messages in database")
            
            mmsMessages.forEach { msg ->
                if (msg == null) return@forEach
                    
                    val threadId = msg.threadId
                    
                    // Skip deleted, archived, private, or blocked conversations
                    if (deletedThreadIds.contains(threadId) || archivedThreadIds.contains(threadId) || privateThreadIds.contains(threadId) || blockedThreadIds.contains(threadId)) {
                        return@forEach
                    }
                    
                    // Filter by category
                    val body = msg.body
                    if (!matchesCategory(body, category, msg.address, contactPhoneNumbers)) {
                        return@forEach
                    }
                    
                    // Determine snippet based on attachment type
                    val snippet = when {
                        msg.mimeType?.startsWith("image/") == true || msg.mimeType == "image/*" -> {
                            if (body.isNotEmpty() && !body.contains("BEGIN:VCARD")) body else "Photo"
                        }
                        msg.mimeType == "text/x-vCard" || msg.attachmentPath?.contains(".vcf", ignoreCase = true) == true -> {
                            // Extract contact name from vCard if available
                            val nameMatch = Regex("FN:([^\\r\\n]+)").find(body)
                            val contactName = nameMatch?.groupValues?.get(1) ?: ""
                            if (contactName.isNotEmpty()) "Contact: $contactName" else "Contact card"
                        }
                        body.isNotEmpty() && !body.contains("BEGIN:VCARD") -> body
                        else -> "MMS"
                    }
                    
                    val conversation = conversationsMap.getOrPut(threadId) {
                        Conversation(
                            threadId = threadId,
                            address = msg.address,
                            snippet = snippet,
                            date = msg.date,
                            unreadCount = 0
                        )
                    }
                    
                    // Update with latest message if this MMS is newer
                    if (msg.date > conversation.date) {
                        conversationsMap[threadId] = conversation.copy(
                            snippet = snippet,
                            date = msg.date
                        )
                    }
                    
                    // Count unread messages
                    if (!msg.read && msg.type == com.text.messages.sms.messanger.data.model.MessageType.INBOX) {
                        conversationsMap[threadId] = conversation.copy(
                            unreadCount = conversation.unreadCount + 1
                        )
                    }
            }
            Log.d(TAG, "loadMmsConversationsFromRealm: Processed MMS messages, total conversations: ${conversationsMap.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MMS conversations from database", e)
            e.printStackTrace()
        }
    }
    
    private fun matchesCategory(messageBody: String, category: String, address: String, contactPhoneNumbers: Set<String>?): Boolean {
        if (category == "All") {
            return true
        }
        
        val bodyLower = messageBody.lowercase()
        
        return when (category) {
            "OTPs" -> isOTPMessage(bodyLower)
            "Offers" -> isOfferMessage(bodyLower)
            "Transactions" -> isTransactionMessage(bodyLower)
            "Personal" -> {
                // Personal messages - must be from saved contacts
                // Use pre-loaded contact set for fast lookup
                if (contactPhoneNumbers == null || contactPhoneNumbers.isEmpty()) {
                    Log.d(TAG, "matchesCategory: Personal - No contacts loaded, returning false")
                    false
                } else {
                    val normalizedAddress = normalizePhoneNumber(address)
                    val isMatch = contactPhoneNumbers.contains(normalizedAddress)
                    if (!isMatch) {
                        // Try alternative matching: check if any contact number matches (last 10 digits)
                        val alternativeMatch = contactPhoneNumbers.any { contactNumber ->
                            normalizedAddress.endsWith(contactNumber) || 
                            contactNumber.endsWith(normalizedAddress) ||
                            (normalizedAddress.length >= 10 && contactNumber.length >= 10 && 
                             normalizedAddress.takeLast(10) == contactNumber.takeLast(10))
                        }
                        Log.d(TAG, "matchesCategory: Personal - Address: $address, Normalized: $normalizedAddress, Match: $isMatch, Alternative: $alternativeMatch, ContactCount: ${contactPhoneNumbers.size}")
                        alternativeMatch
                    } else {
                        Log.d(TAG, "matchesCategory: Personal - Address: $address, Normalized: $normalizedAddress, Match: true")
                        true
                    }
                }
            }
            else -> true
        }
    }
    
    private fun loadContactPhoneNumbers(context: Context): Set<String> {
        // Load all contact phone numbers once for fast lookup
        val phoneNumbers = mutableSetOf<String>()
        val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
        
        Log.d(TAG, "loadContactPhoneNumbers: Starting to load contact phone numbers")
        
        context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            var count = 0
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex) ?: ""
                if (number.isNotEmpty()) {
                    // Normalize phone number for matching
                    val normalized = normalizePhoneNumber(number)
                    phoneNumbers.add(normalized)
                    count++
                    if (count <= 5) {
                        Log.d(TAG, "loadContactPhoneNumbers: Loaded contact - Original: $number, Normalized: $normalized")
                    }
                }
            }
            Log.d(TAG, "loadContactPhoneNumbers: Loaded $count contact phone numbers, normalized set size: ${phoneNumbers.size}")
        }
        
        return phoneNumbers
    }
    
    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove common formatting characters and country codes for better matching
        var normalized = phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), "")
        // Remove leading + if present
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1)
        }
        // For Indian numbers, remove leading 0 or 91 if present
        if (normalized.length > 10) {
            if (normalized.startsWith("91") && normalized.length == 12) {
                normalized = normalized.substring(2)
            } else if (normalized.startsWith("0") && normalized.length == 11) {
                normalized = normalized.substring(1)
            }
        }
        // Keep only last 10 digits for matching (standard mobile number length)
        if (normalized.length > 10) {
            normalized = normalized.takeLast(10)
        }
        return normalized
    }
    
    private fun loadContactNamesBatch(context: Context, conversations: Collection<Conversation>, conversationsMap: MutableMap<Long, Conversation>? = null) {
        val phoneNumbers = conversations.map { normalizePhoneNumber(it.address) }.distinct()
        val contactNameMap = mutableMapOf<String, String?>()
        val contactPhotoMap = mutableMapOf<String, String?>()
        
        // Query contacts for all phone numbers at once
        phoneNumbers.forEach { normalizedNumber ->
            val contactInfo = getContactInfo(context, normalizedNumber)
            contactNameMap[normalizedNumber] = contactInfo.first
            contactPhotoMap[normalizedNumber] = contactInfo.second
        }
        
        // Assign contact names and photos to conversations in the map
        if (conversationsMap != null) {
            conversationsMap.forEach { (threadId, conversation) ->
                val normalizedAddress = normalizePhoneNumber(conversation.address)
                val updatedConversation = conversation.copy(
                    contactName = contactNameMap[normalizedAddress],
                    photoUri = contactPhotoMap[normalizedAddress]
                )
                conversationsMap[threadId] = updatedConversation
            }
        }
    }
    
    private fun isOTPMessage(bodyLower: String): Boolean {
        // Only show messages that have an actual extractable OTP
        // This ensures only messages with copy-able OTP codes appear in the OTP filter
        return OtpHelper.extractOTP(bodyLower) != null
    }
    
    private fun isOfferMessage(bodyLower: String): Boolean {
        // Offer keywords - case insensitive
        return bodyLower.contains("offer") ||
                bodyLower.contains("discount") ||
                bodyLower.contains("deal") ||
                bodyLower.contains("sale") ||
                bodyLower.contains("promo") ||
                bodyLower.contains("promotion") ||
//                bodyLower.contains("coupon") ||
//                bodyLower.contains("voucher") ||
//                bodyLower.contains("cashback") ||
//                bodyLower.contains("reward") ||
//                bodyLower.contains("bonus") ||
//                bodyLower.contains("special") ||
                bodyLower.contains("limited time")
    }
    
    private fun isTransactionMessage(bodyLower: String): Boolean {
        // Transaction keywords - case insensitive
        return bodyLower.contains("transaction") ||
                bodyLower.contains("payment") ||
                bodyLower.contains("paid") ||
                bodyLower.contains("credited") ||
                bodyLower.contains("debited") ||
                bodyLower.contains("balance") ||
//                bodyLower.contains("account") ||
//                bodyLower.contains("bank") ||
                bodyLower.contains("upi") ||
//                bodyLower.contains("transfer") ||
                bodyLower.contains("withdrawal") ||
//                bodyLower.contains("deposit") ||
//                bodyLower.contains("invoice") ||
//                bodyLower.contains("receipt") ||
                bodyLower.contains("refund") ||
//                bodyLower.contains("rs.") ||
//                bodyLower.contains("inr") ||
                bodyLower.contains("₹")
    }
    
    private fun getContactName(context: Context, phoneNumber: String): String? {
        return getContactInfo(context, phoneNumber).first
    }
    
    private fun getContactInfo(context: Context, phoneNumber: String): Pair<String?, String?> {
        // Try multiple formats of the phone number for better matching
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val variations = listOf(
            normalizedNumber,
            phoneNumber,
            if (normalizedNumber.length > 10) normalizedNumber.takeLast(10) else null
        ).filterNotNull().distinct()
        
        for (number in variations) {
            val uri = Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            
            val projection = arrayOf(
                android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
                android.provider.ContactsContract.PhoneLookup.PHOTO_URI
            )
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.PHOTO_URI)
                    
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                    
                    if (name != null || photoUri != null) {
                        return Pair(name, photoUri)
                    }
                }
            }
        }
        
        return Pair(null, null)
    }
    
    // SQL selection builders for better performance with case-insensitive matching
    private fun buildOTPSelection(): String {
        return "LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ?"
    }
    
    private fun buildOTPSelectionArgs(): Array<String> {
        return arrayOf("%otp%", "%one time password%", "%verification code%")
    }
    
    private fun buildOfferSelection(): String {
        return "LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ?"
    }
    
    private fun buildOfferSelectionArgs(): Array<String> {
        return arrayOf("%offer%", "%discount%", "%deal%", "%sale%", "%promo%", "%promotion%")
    }
    
    private fun buildTransactionSelection(): String {
        return "LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ? OR LOWER(${Telephony.Sms.BODY}) LIKE ?"
    }
    
    private fun buildTransactionSelectionArgs(): Array<String> {
        return arrayOf("%transaction%", "%payment%", "%paid%", "%credited%", "%debited%", "%balance%", "%upi%", "%withdrawal%", "%refund%")
    }
    
    private fun getDeletedThreadIds(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences("recycle_bin", Context.MODE_PRIVATE)
        val deletedJson = prefs.getString("deleted_conversations", null)
        if (deletedJson != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<com.text.messages.sms.messanger.ui.main.DeletedConversationData>>() {}.type
            val deletedConversations = gson.fromJson<List<com.text.messages.sms.messanger.ui.main.DeletedConversationData>>(deletedJson, type)
            return deletedConversations.map { it.threadId }.toSet()
        }
        return emptySet()
    }
    
    private fun getArchivedThreadIds(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences("archived_messages", Context.MODE_PRIVATE)
        val archivedJson = prefs.getString("archived_messages_list", null)
        if (archivedJson != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<com.text.messages.sms.messanger.ui.archive.ArchivedMessageData>>() {}.type
            val archivedMessages = gson.fromJson<List<com.text.messages.sms.messanger.ui.archive.ArchivedMessageData>>(archivedJson, type)
            return archivedMessages.map { it.threadId }.toSet()
        }
        return emptySet()
    }
    
    private fun buildTimeFilterSelection(timeFilter: String?, startDate: Long?, endDate: Long?): String? {
        if (timeFilter == null || timeFilter == "Default") {
            return null
        }
        
        return when (timeFilter) {
            "Today" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                "${Telephony.Sms.DATE} >= ?"
            }
            "Month" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis
                "${Telephony.Sms.DATE} >= ?"
            }
            "Year" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startOfYear = calendar.timeInMillis
                "${Telephony.Sms.DATE} >= ?"
            }
            "Custom" -> {
                if (startDate != null && endDate != null) {
                    "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
                } else {
                    null
                }
            }
            else -> null
        }
    }
    
    private fun buildTimeFilterArgs(timeFilter: String?, startDate: Long?, endDate: Long?): Array<String>? {
        if (timeFilter == null || timeFilter == "Default") {
            return null
        }
        
        return when (timeFilter) {
            "Today" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                arrayOf(startOfDay.toString())
            }
            "Month" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis
                arrayOf(startOfMonth.toString())
            }
            "Year" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startOfYear = calendar.timeInMillis
                arrayOf(startOfYear.toString())
            }
            "Custom" -> {
                if (startDate != null && endDate != null) {
                    arrayOf(startDate.toString(), endDate.toString())
                } else {
                    null
                }
            }
            else -> null
        }
    }
    
    /**
     * Pre-cache all categories in the background for instant filter switching.
     * This is called after the initial "All" conversations are loaded.
     */
    fun preCacheAllCategories(context: Context) {
        viewModelScope.launch {
            try {
                val categories = listOf("Personal", "OTPs", "Offers", "Transactions")
                Log.d(TAG, "Starting pre-caching for ${categories.size} categories")
                
                categories.forEach { category ->
                    // Check if already cached
                    val cached = ConversationCache.getCached(category)
                    if (cached == null) {
                        // Cache doesn't exist, load and cache in background
                        try {
                            val conversations = withContext(Dispatchers.IO) {
                                if (!isActive) return@withContext emptyList<Conversation>()
                                loadConversationsFromDevice(category, null, null, null)
                            }
                            if (isActive && conversations.isNotEmpty()) {
                                ConversationCache.cache(category, conversations)
                                Log.d(TAG, "Pre-cached ${conversations.size} conversations for category: $category")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to pre-cache category: $category", e)
                        }
                    } else {
                        Log.d(TAG, "Category '$category' already cached (${cached.size} items), skipping")
                    }
                }
                Log.d(TAG, "Completed pre-caching all categories")
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-caching categories", e)
            }
        }
    }
    
    /**
     * Pre-cache all custom filters in the background for instant filter switching.
     * This is called after custom filters are loaded.
     */
    fun preCacheAllCustomFilters(context: Context) {
        viewModelScope.launch {
            try {
                val filters = CustomFilterStorage.loadFilters(context)
                Log.d(TAG, "Starting pre-caching for ${filters.size} custom filters")
                
                filters.forEach { filter ->
                    // Check if already cached
                    val cached = ConversationCache.getCachedForFilter(filter.id)
                    if (cached == null) {
                        // Cache doesn't exist, load and cache in background
                        try {
                            val conversations = withContext(Dispatchers.IO) {
                                if (!isActive) return@withContext emptyList<Conversation>()
                                loadConversationsForCustomFilterFromDevice(context, filter.id)
                            }
                            if (isActive) {
                                ConversationCache.cacheForFilter(filter.id, conversations)
                                Log.d(TAG, "Pre-cached ${conversations.size} conversations for filter: ${filter.name} (${filter.id})")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to pre-cache filter: ${filter.name}", e)
                        }
                    } else {
                        Log.d(TAG, "Filter '${filter.name}' already cached (${cached.size} items), skipping")
                    }
                }
                Log.d(TAG, "Completed pre-caching all custom filters")
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-caching custom filters", e)
            }
        }
    }
}
