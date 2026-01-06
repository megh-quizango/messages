package com.quizangomedia.messages.ui.archive

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityArchiveBinding
import com.quizangomedia.messages.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var adapter: ArchiveMessageAdapter
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "archived_messages"
        private const val KEY_ARCHIVED_MESSAGES = "archived_messages_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                val intent = android.content.Intent(this, com.quizangomedia.messages.ui.conversation.ConversationDetailActivity::class.java).apply {
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
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
    
    private fun loadArchivedMessages() {
        val archivedJson = prefs.getString(KEY_ARCHIVED_MESSAGES, null)
        val archivedMessages = if (archivedJson != null) {
            val type = object : TypeToken<List<ArchivedMessageData>>() {}.type
            val messages = gson.fromJson<List<ArchivedMessageData>>(archivedJson, type)
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
            val type = object : TypeToken<MutableList<ArchivedMessageData>>() {}.type
            val archivedMessages = gson.fromJson<MutableList<ArchivedMessageData>>(archivedJson, type)
            archivedMessages.removeAll { it.threadId == archivedMessage.threadId }
            
            val updatedJson = gson.toJson(archivedMessages)
            prefs.edit().putString(KEY_ARCHIVED_MESSAGES, updatedJson).apply()
            
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

