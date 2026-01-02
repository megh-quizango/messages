package com.quizangomedia.messages.ui.main

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    
    private val _conversations = MutableLiveData<List<Conversation>>()
    val conversations: LiveData<List<Conversation>> = _conversations
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    fun loadConversations(category: String = "All") {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                val conversations = withContext(Dispatchers.IO) {
                    loadConversationsFromDevice(category)
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
    
    private fun loadConversationsFromDevice(category: String): List<Conversation> {
        val context = MessagesApp.instance
        val conversationsMap = mutableMapOf<Long, Conversation>()
        
        // Load deleted conversation IDs from recycle bin
        val deletedThreadIds = getDeletedThreadIds(context)
        
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
        
        // Load all messages first, then filter by category in memory for better accuracy
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                
                // Skip conversations that are in recycle bin
                if (deletedThreadIds.contains(threadId)) {
                    continue
                }
                
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                
                // Filter by category using case-insensitive matching
                // For Personal category, also check if contact exists in device
                if (!matchesCategory(body, category, address, context)) {
                    continue
                }
                
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
        
        // Get contact names
        conversationsMap.values.forEach { conversation ->
            conversation.contactName = getContactName(context, conversation.address)
        }
        
        return conversationsMap.values.sortedByDescending { it.date }
    }
    
    private fun matchesCategory(messageBody: String, category: String, address: String, context: Context): Boolean {
        if (category == "All") {
            return true
        }
        
        val bodyLower = messageBody.lowercase()
        
        return when (category) {
            "OTPs" -> isOTPMessage(bodyLower)
            "Offers" -> isOfferMessage(bodyLower)
            "Transactions" -> isTransactionMessage(bodyLower)
            "Personal" -> {
                // Personal messages - must be from saved contacts AND exclude OTPs, Offers, and Transactions
                val isFromContact = isContactInDevice(context, address)
                val isNotOTP = !isOTPMessage(bodyLower)
                val isNotOffer = !isOfferMessage(bodyLower)
                val isNotTransaction = !isTransactionMessage(bodyLower)
                
                isFromContact && isNotOTP && isNotOffer && isNotTransaction
            }
            else -> true
        }
    }
    
    private fun isContactInDevice(context: Context, phoneNumber: String): Boolean {
        // Check if the phone number exists in device contacts
        val contactName = getContactName(context, phoneNumber)
        return contactName != null
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
        val uri = Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        
        val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        
        return null
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
}
