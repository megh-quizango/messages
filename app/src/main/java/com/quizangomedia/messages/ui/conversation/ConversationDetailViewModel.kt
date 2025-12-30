package com.quizangomedia.messages.ui.conversation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageStatus
import com.quizangomedia.messages.data.model.MessageType
import kotlinx.coroutines.launch

class ConversationDetailViewModel : ViewModel() {
    
    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages
    
    fun loadMessages(threadId: Long) {
        viewModelScope.launch {
            try {
                val realm = MessagesApp.realm
                val messageList = realm.query(Message::class, "threadId == $threadId")
                    .sort("date", sortOrder = io.realm.kotlin.query.Sort.ASCENDING)
                    .find()
                    .toList()
                
                _messages.postValue(messageList)
            } catch (e: Exception) {
                e.printStackTrace()
                _messages.postValue(emptyList())
            }
        }
    }
    
    fun sendMessage(threadId: Long, address: String, body: String) {
        viewModelScope.launch {
            try {
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    copyToRealm(Message().apply {
                        this.id = System.currentTimeMillis()
                        this.threadId = threadId
                        this.address = address
                        this.body = body
                        this.date = System.currentTimeMillis()
                        this.type = MessageType.SENT
                        this.status = MessageStatus.PENDING
                        this.read = true
                    })
                }
                
                // TODO: Actually send SMS via SmsManager
                loadMessages(threadId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

