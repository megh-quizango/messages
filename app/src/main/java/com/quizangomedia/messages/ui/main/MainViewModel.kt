package com.quizangomedia.messages.ui.main

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
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.util.CustomFilterStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    private val _conversations = MutableLiveData<List<Conversation>>()
    val conversations: LiveData<List<Conversation>> = _conversations
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    fun loadConversations(
        category: String = "All",
        showLoading: Boolean = true,
        timeFilter: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ) {
        viewModelScope.launch {
            try {
                if (showLoading) {
                    _isLoading.postValue(true)
                }
                val conversations = withContext(Dispatchers.IO) {
                    loadConversationsFromDevice(category, timeFilter, startDate, endDate)
                }
                _conversations.postValue(conversations)
            } catch (e: Exception) {
                e.printStackTrace()
                _conversations.postValue(emptyList())
            } finally {
                if (showLoading) {
                    _isLoading.postValue(false)
                }
            }
        }
    }
    
    fun loadConversationsForCustomFilter(context: android.content.Context, filterId: String) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                val conversations = withContext(Dispatchers.IO) {
                    loadConversationsForCustomFilterFromDevice(context, filterId)
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
                    Conversation().apply {
                        this.threadId = threadId
                        this.address = address
                        this.snippet = body
                        this.date = date
                        this.unreadCount = 0
                    }
                }
                
                // Update with latest message
                if (date > conversation.date) {
                    conversation.snippet = body
                    conversation.date = date
                }
                
                // Count unread messages
                if (!read && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    conversation.unreadCount++
                }
            }
        }
        
        // Get contact names in batch
        loadContactNamesBatch(context, conversationsMap.values)
        
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
    
    private fun loadConversationsFromDevice(
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
                    Conversation().apply {
                        this.threadId = threadId
                        this.address = address
                        this.snippet = body
                        this.date = date
                        this.unreadCount = 0
                    }
                }
                
                // Update with latest message
                if (date > conversation.date) {
                    Log.d(TAG, "loadConversationsFromDevice: Updating conversation threadId: $threadId, oldDate: ${conversation.date}, newDate: $date")
                    conversation.snippet = body
                    conversation.date = date
                }
                
                // Count unread messages
                if (!read && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    conversation.unreadCount++
                }
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
        
        // Get contact names in batch for better performance
        loadContactNamesBatch(context, conversationsMap.values)
        
        val sortedConversations = conversationsMap.values.sortedByDescending { it.date }
        Log.d(TAG, "loadConversationsFromDevice: Returning ${sortedConversations.size} conversations")
        return sortedConversations
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
    
    private fun loadContactNamesBatch(context: Context, conversations: Collection<Conversation>) {
        // Build a map of phone numbers to contact info (name and photo) for batch lookup
        val phoneNumbers = conversations.map { normalizePhoneNumber(it.address) }.distinct()
        val contactNameMap = mutableMapOf<String, String?>()
        val contactPhotoMap = mutableMapOf<String, String?>()
        
        // Query contacts for all phone numbers at once
        phoneNumbers.forEach { normalizedNumber ->
            val contactInfo = getContactInfo(context, normalizedNumber)
            contactNameMap[normalizedNumber] = contactInfo.first
            contactPhotoMap[normalizedNumber] = contactInfo.second
        }
        
        // Assign contact names and photos to conversations
        conversations.forEach { conversation ->
            val normalizedAddress = normalizePhoneNumber(conversation.address)
            conversation.contactName = contactNameMap[normalizedAddress]
            conversation.photoUri = contactPhotoMap[normalizedAddress]
        }
    }
    
    private fun isOTPMessage(bodyLower: String): Boolean {
        // OTP keywords - case insensitive
        return bodyLower.contains("otp") ||
                bodyLower.contains("one time password") ||
                bodyLower.contains("verification code") ||
//                bodyLower.contains("verification") ||
//                bodyLower.contains("verify") ||
//                bodyLower.contains("code") ||
//                bodyLower.contains("pin") ||
//                bodyLower.contains("password") ||
//                bodyLower.contains("authenticate") ||
//                bodyLower.contains("activation") ||
                bodyLower.matches(Regex(".*\\b\\d{4,8}\\b.*")) // Contains 4-8 digit number (common OTP pattern)
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
            val type = object : com.google.gson.reflect.TypeToken<List<com.quizangomedia.messages.ui.main.DeletedConversationData>>() {}.type
            val deletedConversations = gson.fromJson<List<com.quizangomedia.messages.ui.main.DeletedConversationData>>(deletedJson, type)
            return deletedConversations.map { it.threadId }.toSet()
        }
        return emptySet()
    }
    
    private fun getArchivedThreadIds(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences("archived_messages", Context.MODE_PRIVATE)
        val archivedJson = prefs.getString("archived_messages_list", null)
        if (archivedJson != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<com.quizangomedia.messages.ui.archive.ArchivedMessageData>>() {}.type
            val archivedMessages = gson.fromJson<List<com.quizangomedia.messages.ui.archive.ArchivedMessageData>>(archivedJson, type)
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
}
