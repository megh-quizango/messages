package com.text.messages.sms.messanger.ui.starred

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
import com.text.messages.sms.messanger.databinding.ActivityStarredBinding
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.util.ConversationStorageParser
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.data.model.MessageType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StarredActivity : BaseActivity() {

    private lateinit var binding: ActivityStarredBinding
    private lateinit var adapter: StarredMessageAdapter
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "starred_messages"
        private const val KEY_STARRED_MESSAGES = "starred_messages_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityStarredBinding.inflate(layoutInflater)
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
        loadStarredMessages()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = StarredMessageAdapter { starredMessage ->
            // Open conversation detail activity for this contact
            val intent = android.content.Intent(this, com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity::class.java).apply {
                putExtra("thread_id", starredMessage.threadId)
                putExtra("address", starredMessage.address)
                putExtra("contact_name", starredMessage.contactName ?: starredMessage.address)
            }
            startActivity(intent)
        }
        binding.recyclerViewStarred.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewStarred.adapter = adapter
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
    
    private fun loadStarredMessages() {
        val starredJson = prefs.getString(KEY_STARRED_MESSAGES, null)
        val starredMessages = if (starredJson != null) {
            val messages = ConversationStorageParser.parseStarredMessages(starredJson, gson)
            // Update contact names
            messages.map { message ->
                if (message.contactName.isNullOrEmpty()) {
                    message.copy(contactName = getContactName(message.address))
                } else {
                    message
                }
            }
        } else {
            emptyList()
        }
        
        if (starredMessages.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerViewStarred.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerViewStarred.visibility = View.VISIBLE
            adapter.submitList(starredMessages)
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
        loadStarredMessages()
    }
}

data class StarredMessageData(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val date: Long,
    val type: Int
)

