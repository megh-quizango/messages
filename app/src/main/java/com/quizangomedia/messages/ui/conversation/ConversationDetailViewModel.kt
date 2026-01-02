package com.quizangomedia.messages.ui.conversation

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.provider.Telephony
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageStatus
import com.quizangomedia.messages.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ConversationDetailViewModel : ViewModel() {
    
    private val _messages = MutableLiveData<List<MessageListItem>>()
    val messages: LiveData<List<MessageListItem>> = _messages
    
    fun loadMessages(threadId: Long, address: String? = null) {
        viewModelScope.launch {
            try {
                val messageList = withContext(Dispatchers.IO) {
                    loadMessagesFromDevice(threadId, address)
                }
                val itemsWithDates = addDateHeaders(messageList)
                _messages.postValue(itemsWithDates)
            } catch (e: Exception) {
                e.printStackTrace()
                _messages.postValue(emptyList())
            }
        }
    }
    
    private fun addDateHeaders(messages: List<Message>): List<MessageListItem> {
        if (messages.isEmpty()) return emptyList()
        
        val items = mutableListOf<MessageListItem>()
        var currentDate: Long? = null
        
        messages.forEach { message ->
            val messageDate = getDateOnly(message.date)
            
            // Add date header if this is a new date
            if (currentDate == null || messageDate != currentDate) {
                items.add(MessageListItem.DateHeader(message.date))
                currentDate = messageDate
            }
            
            items.add(MessageListItem.MessageItem(message))
        }
        
        return items
    }
    
    private fun getDateOnly(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun loadMessagesFromDevice(threadId: Long, address: String?): List<Message> {
        val context = MessagesApp.instance
        val messagesList = mutableListOf<Message>()
        
        // Query SMS from device for this thread/address
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
        
        // Filter by threadId if valid, otherwise by address
        val selection = if (threadId > 0) {
            "${Telephony.Sms.THREAD_ID} = ?"
        } else if (!address.isNullOrEmpty()) {
            "${Telephony.Sms.ADDRESS} = ?"
        } else {
            null
        }
        
        val selectionArgs = if (threadId > 0) {
            arrayOf(threadId.toString())
        } else if (!address.isNullOrEmpty()) {
            arrayOf(address)
        } else {
            null
        }
        
        val sortOrder = "${Telephony.Sms.DATE} ASC"
        
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val msgThreadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                val msgAddress = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                
                val messageType = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageType.INBOX
                    Telephony.Sms.MESSAGE_TYPE_SENT -> MessageType.SENT
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageType.DRAFT
                    else -> MessageType.INBOX
                }
                
                val messageStatus = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
                    else -> MessageStatus.DELIVERED
                }
                
                messagesList.add(Message().apply {
                    this.id = id
                    this.threadId = msgThreadId
                    this.address = msgAddress
                    this.body = body
                    this.date = date
                    this.type = messageType
                    this.status = messageStatus
                    this.read = read
                })
            }
        }
        
        return messagesList
    }
    
    fun sendMessage(threadId: Long, address: String, body: String) {
        // Get signature from SharedPreferences and append to message
        val signature = getSignature()
        val messageBody = if (signature.isNotEmpty()) {
            "$body\n$signature"
        } else {
            body
        }
        viewModelScope.launch {
            try {
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    copyToRealm(Message().apply {
                        this.id = System.currentTimeMillis()
                        this.threadId = threadId
                        this.address = address
                        this.body = messageBody
                        this.date = System.currentTimeMillis()
                        this.type = MessageType.SENT
                        this.status = MessageStatus.PENDING
                        this.read = true
                    })
                }
                
                // TODO: Actually send SMS via SmsManager
                loadMessages(threadId, address)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun getSignature(): String {
        val prefs = MessagesApp.instance.getSharedPreferences("signature", Context.MODE_PRIVATE)
        return prefs.getString("signature_text", "") ?: ""
    }
}

