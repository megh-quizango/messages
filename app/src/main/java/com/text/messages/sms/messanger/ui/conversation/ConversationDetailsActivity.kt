package com.text.messages.sms.messanger.ui.conversation

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityConversationDetailsBinding
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.ui.archive.ArchivedMessageData
import com.text.messages.sms.messanger.ui.main.DeletedConversationData
import com.text.messages.sms.messanger.util.AvatarHelper
import com.text.messages.sms.messanger.util.BlockedConversationStorage
import com.text.messages.sms.messanger.util.ConversationCache
import com.text.messages.sms.messanger.util.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ConversationDetails"
    }

    private lateinit var binding: ActivityConversationDetailsBinding
    private var threadId: Long = -1
    private var address: String = ""
    private var contactName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        threadId = intent.getLongExtra("thread_id", -1)
        address = intent.getStringExtra("address") ?: ""
        contactName = intent.getStringExtra("contact_name") ?: ""

        // If threadId is invalid but we have an address, try to get threadId from address
        if ((threadId <= 0) && address.isNotEmpty()) {
            try {
                threadId = Telephony.Threads.getOrCreateThreadId(this, address)
                Log.d(TAG, "Retrieved threadId from address: $threadId for address: $address")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting threadId from address", e)
            }
        }

        // If contact name is not provided, look it up
        if (contactName.isEmpty() && address.isNotEmpty()) {
            contactName = lookupContactName(address) ?: address
        }

        binding = ActivityConversationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup navigation bar
        ThemeManager.setupNavigationBar(this)

        // Apply theme
        ThemeManager.applyTheme(this, binding.root)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBackButton()
        setupContactInfo()
        setupOptions()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupContactInfo() {
        // Set contact name
        binding.textContactName.text = contactName

        // Set phone number
        binding.textContactNumber.text = address

        // Load avatar
        val photoUri = getContactPhotoUri()
        AvatarHelper.loadAvatar(
            imageView = binding.imageContact,
            textView = binding.textAvatarLetter,
            photoUri = photoUri,
            contactName = contactName,
            address = address,
            context = this
        )
    }

    private fun setupOptions() {
        // Archive option
        binding.optionArchive.setOnClickListener {
            archiveConversation()
        }

        // Block option
        binding.optionBlock.setOnClickListener {
            blockConversation()
        }

        // Delete conversation option
        binding.optionDelete.setOnClickListener {
            deleteConversation()
        }
    }

    private fun archiveConversation() {
        // Try to get threadId from address if it's invalid
        val actualThreadId = if (threadId <= 0 && address.isNotEmpty()) {
            try {
                val retrievedThreadId = Telephony.Threads.getOrCreateThreadId(this, address)
                Log.d(TAG, "Retrieved threadId from address for archiving: $retrievedThreadId")
                retrievedThreadId
            } catch (e: Exception) {
                Log.e(TAG, "Error getting threadId from address for archiving", e)
                0L
            }
        } else {
            threadId
        }
        
        if (actualThreadId <= 0) {
            Toast.makeText(this, "Unable to archive conversation: Invalid contact", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Archiving conversation - threadId: $actualThreadId, address: $address")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get conversation from database to get all details - ensure we only get the specific conversation
                val database = MessagesApp.database
                val conversationDao = database.conversationDao()
                val conversation = conversationDao.getConversationByThreadId(actualThreadId)
                
                // Create conversation object with current data if not found in database
                val conversationToArchive = if (conversation != null) {
                    // Use values from database object - ensure we use the exact threadId from query
                    val dbThreadId = conversation.threadId
                    Log.d(TAG, "Found conversation in database - threadId: $dbThreadId, address: ${conversation.address}")
                    
                    // Validate that the threadId matches
                    if (dbThreadId != actualThreadId) {
                        Log.w(TAG, "ThreadId mismatch! Expected: $actualThreadId, Found: $dbThreadId")
                    }
                    
                    conversation
                } else {
                    // Create with current data if not in database
                    Log.d(TAG, "Conversation not found in database, creating with current data - threadId: $actualThreadId, address: $address")
                    val currentContactName = this@ConversationDetailsActivity.contactName
                    Conversation(
                        threadId = actualThreadId,
                        address = address,
                        contactName = if (currentContactName.isNotEmpty()) currentContactName else null,
                        snippet = "", // Will be empty if not in database
                        date = System.currentTimeMillis(),
                        unreadCount = 0
                    )
                }
                
                // Validate conversation before archiving
                if (conversationToArchive.threadId <= 0) {
                    Log.e(TAG, "Invalid threadId for archiving: ${conversationToArchive.threadId}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ConversationDetailsActivity, "Invalid conversation data", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                Log.d(TAG, "Archiving conversation with threadId: ${conversationToArchive.threadId}, address: ${conversationToArchive.address}")
                
                // Save to archive (SharedPreferences) - same as MainActivity
                saveToArchive(conversationToArchive)
                
                        // Remove from cache
                        ConversationCache.removeConversation(actualThreadId)
                        
                        // Invalidate all category caches to ensure updates are immediately visible
                        ConversationCache.invalidate("All")
                        ConversationCache.invalidate("Personal")
                        ConversationCache.invalidate("OTPs")
                        ConversationCache.invalidate("Offers")
                        ConversationCache.invalidate("Transactions")
                        
                        // Send broadcast to notify calling activity
                        sendConversationActionBroadcast(actualThreadId, "archived")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConversationDetailsActivity, "Conversation archived", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error archiving conversation", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConversationDetailsActivity, "Failed to archive conversation", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun saveToArchive(conversation: Conversation) {
        // Validate conversation data
        if (conversation.threadId <= 0) {
            Log.e(TAG, "Cannot archive conversation with invalid threadId: ${conversation.threadId}")
            return
        }
        
        Log.d(TAG, "saveToArchive: Archiving conversation with threadId: ${conversation.threadId}, address: ${conversation.address}")
        
        val prefs = getSharedPreferences("archived_messages", MODE_PRIVATE)
        val gson = Gson()
        
        // Load existing archived items
        val archivedJson = prefs.getString("archived_messages_list", null)
        val archivedMessages = if (archivedJson != null) {
            val type = object : TypeToken<MutableList<ArchivedMessageData>>() {}.type
            gson.fromJson<MutableList<ArchivedMessageData>>(archivedJson, type)
        } else {
            mutableListOf()
        }
        
        Log.d(TAG, "saveToArchive: Found ${archivedMessages.size} existing archived conversations")
        
        // Check if already archived - ensure we're checking by threadId only
        val alreadyArchived = archivedMessages.any { it.threadId == conversation.threadId }
        if (alreadyArchived) {
            Log.d(TAG, "saveToArchive: Conversation with threadId ${conversation.threadId} is already archived")
            return
        }
        
        // Add ONLY this specific conversation to archive
        archivedMessages.add(ArchivedMessageData(
            threadId = conversation.threadId,
            address = conversation.address,
            contactName = conversation.contactName,
            snippet = conversation.snippet,
            date = conversation.date,
            unreadCount = conversation.unreadCount
        ))
        
        Log.d(TAG, "saveToArchive: Added conversation to archive. Total archived: ${archivedMessages.size}")
        
        // Save back to SharedPreferences
        val updatedJson = gson.toJson(archivedMessages)
        prefs.edit().putString("archived_messages_list", updatedJson).apply()
        
        Log.d(TAG, "saveToArchive: Saved ${archivedMessages.size} archived conversations to SharedPreferences")
    }

    private fun blockConversation() {
        // Try to get threadId from address if it's invalid
        val actualThreadId = if (threadId <= 0 && address.isNotEmpty()) {
            try {
                val retrievedThreadId = Telephony.Threads.getOrCreateThreadId(this, address)
                Log.d(TAG, "Retrieved threadId from address for blocking: $retrievedThreadId")
                retrievedThreadId
            } catch (e: Exception) {
                Log.e(TAG, "Error getting threadId from address for blocking", e)
                0L
            }
        } else {
            threadId
        }
        
        if (actualThreadId <= 0) {
            Toast.makeText(this, "Unable to block conversation: Invalid contact", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Block Conversation")
            .setMessage("Are you sure you want to block this conversation? You will not receive messages from this contact.")
            .setPositiveButton("Block") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Save to blocked conversations storage - same as MainActivity
                        BlockedConversationStorage.addThreadId(this@ConversationDetailsActivity, actualThreadId)
                        
                        // Update database conversation as blocked
                        try {
                            val database = MessagesApp.database
                            val conversationDao = database.conversationDao()
                            conversationDao.updateBlockedStatus(actualThreadId, true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating database conversation as blocked", e)
                        }
                        
                        // Remove from cache
                        ConversationCache.removeConversation(actualThreadId)
                        
                        // Invalidate all category caches to ensure updates are immediately visible
                        ConversationCache.invalidate("All")
                        ConversationCache.invalidate("Personal")
                        ConversationCache.invalidate("OTPs")
                        ConversationCache.invalidate("Offers")
                        ConversationCache.invalidate("Transactions")
                        
                        // Send broadcast to notify calling activity
                        sendConversationActionBroadcast(actualThreadId, "blocked")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Conversation blocked", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error blocking conversation", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Failed to block conversation", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteConversation() {
        // Try to get threadId from address if it's invalid
        val actualThreadId = if (threadId <= 0 && address.isNotEmpty()) {
            try {
                val retrievedThreadId = Telephony.Threads.getOrCreateThreadId(this, address)
                Log.d(TAG, "Retrieved threadId from address for deleting: $retrievedThreadId")
                retrievedThreadId
            } catch (e: Exception) {
                Log.e(TAG, "Error getting threadId from address for deleting", e)
                0L
            }
        } else {
            threadId
        }
        
        if (actualThreadId <= 0) {
            Toast.makeText(this, "Unable to delete conversation: Invalid contact", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Get conversation from database to get all details
                        val database = MessagesApp.database
                        val conversationDao = database.conversationDao()
                        val conversation = conversationDao.getConversationByThreadId(actualThreadId)
                        
                        // Create conversation object with current data if not found in database
                        val conversationToDelete = if (conversation != null) {
                            conversation
                        } else {
                            // Create with current data if not in database
                            val currentContactName = this@ConversationDetailsActivity.contactName
                            Conversation(
                                threadId = actualThreadId,
                                address = address,
                                contactName = if (currentContactName.isNotEmpty()) currentContactName else null,
                                snippet = "", // Will be empty if not in database
                                date = System.currentTimeMillis(),
                                unreadCount = 0
                            )
                        }
                        
                        // Save to recycle bin (SharedPreferences) - same as MainActivity
                        // Don't actually delete from SMS, just mark as deleted
                        saveToRecycleBin(conversationToDelete)
                        
                        // Remove from cache
                        ConversationCache.removeConversation(actualThreadId)
                        
                        // Invalidate all category caches to ensure updates are immediately visible
                        ConversationCache.invalidate("All")
                        ConversationCache.invalidate("Personal")
                        ConversationCache.invalidate("OTPs")
                        ConversationCache.invalidate("Offers")
                        ConversationCache.invalidate("Transactions")
                        
                        // Send broadcast to notify calling activity
                        sendConversationActionBroadcast(actualThreadId, "deleted")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Conversation deleted", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting conversation", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun saveToRecycleBin(conversation: Conversation) {
        val prefs = getSharedPreferences("recycle_bin", MODE_PRIVATE)
        val gson = Gson()
        
        // Load existing recycle bin items
        val recycleBinJson = prefs.getString("deleted_conversations", null)
        val deletedConversations = if (recycleBinJson != null) {
            val type = object : TypeToken<List<DeletedConversationData>>() {}.type
            gson.fromJson<List<DeletedConversationData>>(recycleBinJson, type).toMutableList()
        } else {
            mutableListOf()
        }
        
        // Add current conversation to recycle bin
        deletedConversations.add(DeletedConversationData(
            threadId = conversation.threadId,
            address = conversation.address,
            contactName = conversation.contactName,
            snippet = conversation.snippet,
            date = conversation.date,
            unreadCount = conversation.unreadCount,
            deletedAt = System.currentTimeMillis()
        ))
        
        // Save back to SharedPreferences
        val updatedJson = gson.toJson(deletedConversations)
        prefs.edit().putString("deleted_conversations", updatedJson).apply()
    }

    private fun lookupContactName(phoneNumber: String): String? {
        fun normalizePhoneNumber(phone: String): String {
            var normalized = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
            if (normalized.startsWith("+")) {
                normalized = normalized.substring(1)
            }
            if (normalized.length > 10) {
                if (normalized.startsWith("91") && normalized.length == 12) {
                    normalized = normalized.substring(2)
                } else if (normalized.startsWith("0") && normalized.length == 11) {
                    normalized = normalized.substring(1)
                }
            }
            if (normalized.length > 10) {
                normalized = normalized.takeLast(10)
            }
            return normalized
        }

        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val variations = listOf(
            normalizedNumber,
            phoneNumber,
            if (normalizedNumber.length > 10) normalizedNumber.takeLast(10) else null
        ).filterNotNull().distinct()

        for (number in variations) {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )

            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) {
                            return name
                        }
                    }
                }
            }
        }

        return null
    }

    private fun getContactPhotoUri(): String? {
        if (address.isEmpty()) return null

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )

            val projection = arrayOf(ContactsContract.PhoneLookup._ID)

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                    val photoUri = ContactsContract.Contacts.getLookupUri(contactId, null)
                    return photoUri?.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact photo URI", e)
        }

        return null
    }
    
    private fun sendConversationActionBroadcast(threadId: Long, action: String) {
        val intent = Intent("com.text.messages.sms.messanger.CONVERSATION_ACTION").apply {
            putExtra("thread_id", threadId)
            putExtra("action", action)
            setPackage(packageName) // Set package to ensure it's delivered to this app
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            sendBroadcast(intent, android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            sendBroadcast(intent)
        }
        Log.d(TAG, "Sent broadcast for conversation action: $action, threadId: $threadId")
    }
}

