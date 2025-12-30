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
    
    fun loadConversations(category: String = "All") {
        viewModelScope.launch {
            try {
                val conversations = withContext(Dispatchers.IO) {
                    loadConversationsFromDevice(category)
                }
                _conversations.postValue(conversations)
            } catch (e: Exception) {
                e.printStackTrace()
                _conversations.postValue(emptyList())
            }
        }
    }
    
    private fun loadConversationsFromDevice(category: String): List<Conversation> {
        val context = MessagesApp.instance
        val conversationsMap = mutableMapOf<Long, Conversation>()
        
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
        
        val selection = when (category) {
            "OTPs" -> "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.BODY} LIKE ?"
            "Offers" -> "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.BODY} LIKE ?"
            "Transactions" -> "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.BODY} LIKE ?"
            else -> null
        }
        
        val selectionArgs = when (category) {
            "OTPs" -> arrayOf("%OTP%", "%code%", "%verification%")
            "Offers" -> arrayOf("%offer%", "%discount%")
            "Transactions" -> arrayOf("%transaction%", "%payment%")
            else -> null
        }
        
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
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
        
        // Get contact names
        conversationsMap.values.forEach { conversation ->
            conversation.contactName = getContactName(context, conversation.address)
        }
        
        return conversationsMap.values.sortedByDescending { it.date }
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
}
