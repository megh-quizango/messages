package com.text.messages.sms.messanger.ui.archive

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.gson.Gson
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityArchiveBinding
import com.text.messages.sms.messanger.util.ConversationStorageParser
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ArchiveActivity : BaseActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var adapter: ArchiveMessageAdapter
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "archived_messages"
        private const val KEY_ARCHIVED_MESSAGES = "archived_messages_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("ArchiveActivity", "ArchiveActivity")
        
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupRecyclerView()
        setupBannerAd()
        loadArchivedMessages()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ArchiveMessageAdapter(
            onItemClick = { archivedMessage ->
                // Open conversation detail activity for this contact
                val intent = android.content.Intent(this, com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity::class.java).apply {
                    putExtra("thread_id", archivedMessage.threadId)
                    putExtra("address", archivedMessage.address)
                    putExtra("contact_name", archivedMessage.contactName ?: archivedMessage.address)
                }
                startActivity(intent)
            },
            onArchiveIconClick = { archivedMessage ->
                // Remove from archive when archive icon is tapped
                removeFromArchive(archivedMessage)
            }
        )
        binding.recyclerViewArchive.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewArchive.adapter = adapter
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
    
    private fun loadArchivedMessages() {
        val archivedJson = prefs.getString(KEY_ARCHIVED_MESSAGES, null)
        val archivedMessages = if (archivedJson != null) {
            val messages = ConversationStorageParser.parseArchivedMessages(archivedJson, gson)
            // Update contact names
            messages.map { message ->
                if (message.contactName.isNullOrEmpty()) {
                    message.copy(contactName = getContactName(message.address))
                } else {
                    message
                }
            }
        } else {
            emptyList<ArchivedMessageData>()
        }
        
        if (archivedMessages.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerViewArchive.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerViewArchive.visibility = View.VISIBLE
            adapter.submitList(archivedMessages)
        }
    }
    
    private fun removeFromArchive(archivedMessage: ArchivedMessageData) {
        val archivedJson = prefs.getString(KEY_ARCHIVED_MESSAGES, null)
        if (archivedJson != null) {
            val archivedMessages = ConversationStorageParser.parseArchivedMessages(archivedJson, gson)
            archivedMessages.removeAll { it.threadId == archivedMessage.threadId }
            
            val updatedJson = gson.toJson(archivedMessages)
            prefs.edit().putString(KEY_ARCHIVED_MESSAGES, updatedJson).apply()
            
            // Immediately invalidate all category caches so MainActivity shows the unarchived conversation
            com.text.messages.sms.messanger.util.ConversationCache.invalidate("All")
            com.text.messages.sms.messanger.util.ConversationCache.invalidate("Personal")
            com.text.messages.sms.messanger.util.ConversationCache.invalidate("OTPs")
            com.text.messages.sms.messanger.util.ConversationCache.invalidate("Offers")
            com.text.messages.sms.messanger.util.ConversationCache.invalidate("Transactions")
            
            // Send broadcast to notify MainActivity to restore the conversation
            val intent = android.content.Intent("com.text.messages.sms.messanger.CONVERSATION_RESTORED").apply {
                putExtra("thread_id", archivedMessage.threadId)
                setPackage(packageName)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                sendBroadcast(intent, android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                sendBroadcast(intent)
            }
            
            // Reload the list
            loadArchivedMessages()
        }
    }
    
    private fun getContactName(phoneNumber: String): String? {
        val uri = android.net.Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        
        val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        
        return null
    }
    
    override fun onResume() {
        super.onResume()
        loadArchivedMessages()
    }
}

data class ArchivedMessageData(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val unreadCount: Int
)

