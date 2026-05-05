package com.text.messages.sms.messanger.ui.caller

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.data.model.MessageStatus
import com.text.messages.sms.messanger.data.model.MessageType
import com.text.messages.sms.messanger.util.ConversationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallAfterViewModel : ViewModel() {

    companion object {
        private const val TAG = "CallAfterViewModel"
    }

    // UI State
    private val _selectedResponseIndex = MutableLiveData<Int>(-1)
    val selectedResponseIndex: LiveData<Int> = _selectedResponseIndex

    private val _messageText = MutableLiveData<String>("")
    val messageText: LiveData<String> = _messageText

    private val _isSending = MutableLiveData<Boolean>(false)
    val isSending: LiveData<Boolean> = _isSending

    private val _sendResult = MutableLiveData<SendResult?>()
    val sendResult: LiveData<SendResult?> = _sendResult

    private val _contactInfo = MutableLiveData<ContactInfo>()
    val contactInfo: LiveData<ContactInfo> = _contactInfo

    // Quick Responses
    private val _quickResponses = MutableLiveData<List<QuickResponse>>()
    val quickResponses: LiveData<List<QuickResponse>> = _quickResponses

    // Call info
    private var callerNumber: String? = null
    private var callType: String = "completed"
    private var callDuration: Long = 0
    private var callEndTime: Long = 0
    private var isIncoming: Boolean = false

    data class ContactInfo(
        val name: String?,
        val number: String,
        val photoUri: String?,
        val isKnownContact: Boolean
    )

    data class QuickResponse(
        val id: Int,
        val text: String,
        var isSelected: Boolean = false
    )

    sealed class SendResult {
        object Success : SendResult()
        data class Error(val message: String) : SendResult()
    }

    init {
        loadQuickResponses()
    }

    private fun loadQuickResponses() {
        val context = MessagesApp.instance
        val responses = listOf(
            QuickResponse(0, context.getString(com.text.messages.sms.messanger.R.string.quick_response_1)),
            QuickResponse(1, context.getString(com.text.messages.sms.messanger.R.string.quick_response_2)),
            QuickResponse(2, context.getString(com.text.messages.sms.messanger.R.string.quick_response_3))
        )
        _quickResponses.value = responses
    }

    fun setCallInfo(
        number: String?,
        type: String,
        duration: Long,
        endTime: Long,
        incoming: Boolean
    ) {
        callerNumber = number
        callType = type
        callDuration = duration
        callEndTime = endTime
        isIncoming = incoming
    }

    fun setContactInfo(info: ContactInfo) {
        _contactInfo.value = info
    }

    fun selectQuickResponse(index: Int) {
        val currentIndex = _selectedResponseIndex.value ?: -1

        // If same item is clicked, deselect it
        if (currentIndex == index) {
            _selectedResponseIndex.value = -1
            _messageText.value = ""
            updateQuickResponseSelections(-1)
        } else {
            _selectedResponseIndex.value = index
            val responses = _quickResponses.value
            if (responses != null && index in responses.indices) {
                _messageText.value = responses[index].text
            }
            updateQuickResponseSelections(index)
        }
    }

    private fun updateQuickResponseSelections(selectedIndex: Int) {
        val responses = _quickResponses.value?.toMutableList() ?: return
        responses.forEachIndexed { index, response ->
            response.isSelected = index == selectedIndex
        }
        _quickResponses.value = responses
    }

    fun updateMessageText(text: String) {
        _messageText.value = text

        // Deselect quick response if text is manually edited
        val responses = _quickResponses.value
        val currentText = responses?.getOrNull(_selectedResponseIndex.value ?: -1)?.text
        if (currentText != null && text != currentText) {
            _selectedResponseIndex.value = -1
            updateQuickResponseSelections(-1)
        }
    }

    fun sendMessage() {
        val number = callerNumber
        val body = _messageText.value

        if (number.isNullOrEmpty()) {
            _sendResult.value = SendResult.Error("Invalid phone number")
            return
        }

        if (body.isNullOrEmpty()) {
            _sendResult.value = SendResult.Error("Message cannot be empty")
            return
        }

        _isSending.value = true

        viewModelScope.launch {
            try {
                val context = MessagesApp.instance
                val database = MessagesApp.database
                val messageDao = database.messageDao()
                val conversationDao = database.conversationDao()

                // Get or create thread ID
                val threadId = withContext(Dispatchers.IO) {
                    Telephony.Threads.getOrCreateThreadId(context, number)
                }

                val messageId = System.currentTimeMillis()
                val timestamp = System.currentTimeMillis()

                Log.d(TAG, "Sending SMS - threadId: $threadId, address: $number")

                // Create or update conversation in database
                withContext(Dispatchers.IO) {
                    val existingConversation = conversationDao.getConversationByThreadId(threadId)

                    if (existingConversation == null) {
                        conversationDao.insertConversation(
                            Conversation(
                                threadId = threadId,
                                address = number,
                                snippet = body,
                                date = timestamp,
                                unreadCount = 0,
                                contactName = _contactInfo.value?.name
                            )
                        )
                    } else {
                        conversationDao.updateConversationSnippet(threadId, body, timestamp)
                    }

                    // Create message in database with PENDING status
                    messageDao.insertMessage(
                        Message(
                            id = messageId,
                            threadId = threadId,
                            address = number,
                            body = body,
                            date = timestamp,
                            type = MessageType.SENT,
                            status = MessageStatus.PENDING,
                            read = true,
                            starred = false,
                            messagePartCount = 1
                        )
                    )
                }

                Log.d(TAG, "Message stored in database - messageId: $messageId")

                // Send SMS using SmsManager
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val smsManager = SmsManager.getDefault()
                    val parts = smsManager.divideMessage(body)

                    val sentIntent = PendingIntent.getBroadcast(
                        context,
                        messageId.toInt(),
                        Intent("com.text.messages.sms.messanger.SMS_SENT").apply {
                            putExtra("message_id", messageId)
                            putExtra("thread_id", threadId)
                            putExtra("address", number)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val deliveredIntent = PendingIntent.getBroadcast(
                        context,
                        messageId.toInt(),
                        Intent("com.text.messages.sms.messanger.SMS_DELIVERED").apply {
                            putExtra("message_id", messageId)
                            putExtra("thread_id", threadId)
                            putExtra("address", number)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    if (parts.size == 1) {
                        smsManager.sendTextMessage(
                            number,
                            null,
                            body,
                            sentIntent,
                            deliveredIntent
                        )
                    } else {
                        val sentIntents = ArrayList<PendingIntent>()
                        val deliveredIntents = ArrayList<PendingIntent>()

                        for (i in parts.indices) {
                            sentIntents.add(
                                PendingIntent.getBroadcast(
                                    context,
                                    (messageId + i).toInt(),
                                    Intent("com.text.messages.sms.messanger.SMS_SENT").apply {
                                        putExtra("message_id", messageId)
                                        putExtra("thread_id", threadId)
                                        putExtra("address", number)
                                        putExtra("part_index", i)
                                    },
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                            deliveredIntents.add(
                                PendingIntent.getBroadcast(
                                    context,
                                    (messageId + i).toInt(),
                                    Intent("com.text.messages.sms.messanger.SMS_DELIVERED").apply {
                                        putExtra("message_id", messageId)
                                        putExtra("thread_id", threadId)
                                        putExtra("address", number)
                                        putExtra("part_index", i)
                                    },
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }

                        smsManager.sendMultipartTextMessage(
                            number,
                            null,
                            parts,
                            sentIntents,
                            deliveredIntents
                        )
                    }

                    Log.d(TAG, "SMS sent via SmsManager")

                    // Save to system SMS database
                    saveMessageToSmsDatabase(context, threadId, number, body)
                }

                // Invalidate conversation cache so it refreshes
                ConversationCache.clear()

                // Update message status to SENT
                withContext(Dispatchers.IO) {
                    delay(300)
                    val message = messageDao.getMessageById(messageId)
                    if (message != null) {
                        messageDao.updateMessage(message.copy(status = MessageStatus.SENT))
                    }
                }

                Log.d(TAG, "SMS sent successfully")
                _isSending.value = false
                _sendResult.value = SendResult.Success

                // Clear message
                _messageText.value = ""
                _selectedResponseIndex.value = -1
                updateQuickResponseSelections(-1)

            } catch (e: Exception) {
                Log.e(TAG, "Error sending SMS", e)
                _isSending.value = false
                _sendResult.value = SendResult.Error(e.message ?: "Failed to send message")
            }
        }
    }

    private fun saveMessageToSmsDatabase(context: Context, threadId: Long, address: String, body: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.THREAD_ID, threadId)
            }

            val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            Log.d(TAG, "Message saved to SMS database - URI: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving message to SMS database", e)
        }
    }

    fun clearSendResult() {
        _sendResult.value = null
    }

    fun getCallerNumber(): String? = callerNumber
    fun getCallType(): String = callType
    fun getCallDuration(): Long = callDuration
    fun getCallEndTime(): Long = callEndTime
    fun isIncomingCall(): Boolean = isIncoming
}
