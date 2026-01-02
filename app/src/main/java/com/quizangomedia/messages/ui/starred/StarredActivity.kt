package com.quizangomedia.messages.ui.starred

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
import com.quizangomedia.messages.databinding.ActivityStarredBinding
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StarredActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStarredBinding
    private lateinit var adapter: StarredMessageAdapter
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "starred_messages"
        private const val KEY_STARRED_MESSAGES = "starred_messages_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityStarredBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
            val intent = android.content.Intent(this, com.quizangomedia.messages.ui.conversation.ConversationDetailActivity::class.java).apply {
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
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
    
    private fun loadStarredMessages() {
        val starredJson = prefs.getString(KEY_STARRED_MESSAGES, null)
        val starredMessages = if (starredJson != null) {
            val type = object : TypeToken<List<StarredMessageData>>() {}.type
            val messages = gson.fromJson<List<StarredMessageData>>(starredJson, type)
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

