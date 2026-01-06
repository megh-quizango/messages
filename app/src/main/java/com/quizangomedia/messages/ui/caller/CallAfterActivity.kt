package com.quizangomedia.messages.ui.caller

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityCallAfterBinding
import com.quizangomedia.messages.ui.conversation.ConversationDetailActivity
import com.quizangomedia.messages.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallAfterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallAfterBinding
    private var callerNumber: String? = null
    private var callType: String? = null
    private var callEndTime: Long = 0
    private var callStartTime: Long = 0
    private var isIncoming: Boolean = false
    private lateinit var callHistoryAdapter: CallHistoryAdapter

    companion object {
        private const val TAG = "CallAfterActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityCallAfterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Get intent extras
        callerNumber = intent.getStringExtra("CALLER_NUMBER")
        callType = intent.getStringExtra("CALL_TYPE") ?: "completed"
        callEndTime = intent.getLongExtra("CALL_END_TIME", System.currentTimeMillis())
        callStartTime = intent.getLongExtra("CALL_START_TIME", callEndTime)
        isIncoming = intent.getBooleanExtra("IS_INCOMING", false)
        
        Log.d(TAG, "CallAfterActivity: number=$callerNumber, type=$callType, incoming=$isIncoming")
        
        setupUI()
        setupCallHistory()
        loadContactInfo()
        displayCallDetails()
        loadCallHistory()
    }
    
    private fun setupUI() {
        // Set call type title
        val title = when (callType) {
            "missed" -> getString(R.string.missed_call)
            "no_answer" -> getString(R.string.no_answer)
            else -> getString(R.string.call_ended)
        }
        binding.textTitle.text = title
        
        // Display caller number (will be updated if contact found)
        binding.textNumber.text = callerNumber ?: getString(R.string.unknown_number)
        
        // Setup buttons
        binding.buttonCall.setOnClickListener {
            makeCall()
        }
        
        binding.buttonMessage.setOnClickListener {
            openMessage()
        }
        
        binding.buttonAddContact.setOnClickListener {
            addToContacts()
        }
        
        binding.buttonClose.setOnClickListener {
            finish()
        }
        
        // Always show buttons - they will handle empty number cases
        binding.buttonCall.visibility = View.VISIBLE
        binding.buttonMessage.visibility = View.VISIBLE
        binding.buttonAddContact.visibility = View.VISIBLE
        
        // Apply theme to close button
        applyThemeToCloseButton()
    }
    
    private fun setupCallHistory() {
        callHistoryAdapter = CallHistoryAdapter()
        binding.recyclerViewCallHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewCallHistory.adapter = callHistoryAdapter
    }
    
    private fun loadCallHistory() {
        val number = callerNumber
        if (number.isNullOrEmpty()) {
            binding.textCallHistoryHeader.visibility = View.GONE
            binding.recyclerViewCallHistory.visibility = View.GONE
            return
        }
        
        try {
            val callHistory = mutableListOf<CallHistoryItem>()
            val normalizedNumber = normalizePhoneNumber(number)
            
            // Try multiple variations of the phone number
            val variations = mutableListOf<String>().apply {
                add(normalizedNumber)
                add(number)
                if (normalizedNumber.length > 10) {
                    add(normalizedNumber.takeLast(10))
                }
                if (!normalizedNumber.startsWith("+")) {
                    add("+$normalizedNumber")
                }
            }.distinct()
            
            // Query CallLog for each variation
            for (phoneNumber in variations) {
                val projection = arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_NAME
                )
                
                val selection = "${CallLog.Calls.NUMBER} = ?"
                val selectionArgs = arrayOf(phoneNumber)
                val sortOrder = "${CallLog.Calls.DATE} DESC"
                
                contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                        val callNumber = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                        val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                        val type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                        val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                        val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            cursor.getString(nameIndex)
                        } else null
                        
                        // Avoid duplicates
                        if (!callHistory.any { it.id == id }) {
                            callHistory.add(CallHistoryItem(id, callNumber, date, duration, type, name))
                        }
                    }
                }
            }
            
            // Sort by date descending and limit to recent calls
            val sortedHistory = callHistory.sortedByDescending { it.date }.take(20)
            
            if (sortedHistory.isNotEmpty()) {
                callHistoryAdapter.submitList(sortedHistory)
                binding.textCallHistoryHeader.visibility = View.VISIBLE
                binding.recyclerViewCallHistory.visibility = View.VISIBLE
            } else {
                binding.textCallHistoryHeader.visibility = View.GONE
                binding.recyclerViewCallHistory.visibility = View.GONE
            }
            
            Log.d(TAG, "Loaded ${sortedHistory.size} call history entries")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to read CallLog", e)
            binding.textCallHistoryHeader.visibility = View.GONE
            binding.recyclerViewCallHistory.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Error loading call history", e)
            binding.textCallHistoryHeader.visibility = View.GONE
            binding.recyclerViewCallHistory.visibility = View.GONE
        }
    }
    
    private fun applyThemeToCloseButton() {
        val themeColor = ThemeManager.getThemeColor(this)
        binding.buttonClose.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.buttonClose.setTextColor(android.graphics.Color.WHITE)
    }
    
    private fun loadContactInfo() {
        val number = callerNumber
        if (number.isNullOrEmpty()) return
        
        try {
            // Normalize phone number for better matching
            val normalizedNumber = normalizePhoneNumber(number)
            
            // Try multiple variations of the phone number
            val variations = mutableListOf<String>().apply {
                add(normalizedNumber)
                add(number)
                // Try without country code (last 10 digits)
                if (normalizedNumber.length > 10) {
                    add(normalizedNumber.takeLast(10))
                }
                // Try with + prefix
                if (!normalizedNumber.startsWith("+")) {
                    add("+$normalizedNumber")
                }
            }.distinct()
            
            var contactFound = false
            
            for (phoneNumber in variations) {
                if (contactFound) break
                
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber)
                )
                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                )
                
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                        
                        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        val photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                        
                        if (!name.isNullOrEmpty()) {
                            binding.textNumber.text = name
                            binding.textNumberSecondary.text = number
                            binding.textNumberSecondary.visibility = View.VISIBLE
                            contactFound = true
                            Log.d(TAG, "Contact found: $name for number: $number")
                        }
                    }
                }
            }
            
            if (!contactFound) {
                Log.d(TAG, "No contact found for number: $number")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contact info", e)
        }
    }
    
    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters except +
        var normalized = phoneNumber.replace(Regex("[^+\\d]"), "")
        
        // Remove leading zeros
        normalized = normalized.trimStart('0')
        
        return normalized
    }
    
    private fun makeCall() {
        if (callerNumber.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_number), Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$callerNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            Toast.makeText(this, getString(R.string.call_failed), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openMessage() {
        val number = callerNumber
        if (number.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_number), Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Get thread ID for the phone number
            val threadId = Telephony.Threads.getOrCreateThreadId(this, number)
            
            // Get contact name if available
            val contactName = binding.textNumber.text.toString()
            val finalContactName = if (contactName != number && contactName != getString(R.string.unknown_number)) {
                contactName
            } else {
                number
            }
            
            val intent = Intent(this, ConversationDetailActivity::class.java).apply {
                putExtra("thread_id", threadId)
                putExtra("address", number)
                putExtra("contact_name", finalContactName)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening conversation", e)
            // Fallback to SMS intent
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$number")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening SMS", e2)
                Toast.makeText(this, getString(R.string.message_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun displayCallDetails() {
        val details = mutableListOf<String>()
        
        // Add incoming/outgoing info
        val callDirection = if (isIncoming) "Incoming" else "Outgoing"
        details.add(callDirection)
        
        // Add call time
        val callTime = if (callStartTime > 0 && callStartTime < callEndTime) {
            val duration = (callEndTime - callStartTime) / 1000 // Duration in seconds
            formatCallDuration(duration)
        } else {
            formatCallTime(callEndTime)
        }
        details.add(callTime)
        
        // Display details
        binding.textCallDetails.text = details.joinToString(" • ")
        binding.textCallDetails.visibility = View.VISIBLE
    }
    
    private fun formatCallDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> {
                val minutes = seconds / 60
                val secs = seconds % 60
                if (secs > 0) "${minutes}m ${secs}s" else "${minutes}m"
            }
            else -> {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            }
        }
    }
    
    private fun formatCallTime(timestamp: Long): String {
        val date = Date(timestamp)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return timeFormat.format(date)
    }
    
    private fun addToContacts() {
        if (callerNumber.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_number), Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, callerNumber)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to contacts", e)
            Toast.makeText(this, getString(R.string.add_contact_failed), Toast.LENGTH_SHORT).show()
        }
    }
}

