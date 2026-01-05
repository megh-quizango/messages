package com.quizangomedia.messages.ui.recyclebin

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityRecycleBinBinding
import com.quizangomedia.messages.ui.main.DeletedConversationData
import com.quizangomedia.messages.util.ThemeManager

class RecycleBinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecycleBinBinding
    private lateinit var adapter: RecycleBinAdapter
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "recycle_bin"
        private const val KEY_DELETED_CONVERSATIONS = "deleted_conversations"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRecycleBinBinding.inflate(layoutInflater)
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
        loadDeletedConversations()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = RecycleBinAdapter(
            onItemClick = { deletedConversation ->
                // Open conversation detail activity for this contact
                val intent = android.content.Intent(this, com.quizangomedia.messages.ui.conversation.ConversationDetailActivity::class.java).apply {
                    putExtra("thread_id", deletedConversation.threadId)
                    putExtra("address", deletedConversation.address)
                    putExtra("contact_name", deletedConversation.contactName ?: deletedConversation.address)
                }
                startActivity(intent)
            },
            onRecoverClick = { deletedConversation ->
                showRecoverConfirmationDialog(deletedConversation)
            }
        )
        binding.recyclerViewRecycleBin.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewRecycleBin.adapter = adapter
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
    
    private fun loadDeletedConversations() {
        val deletedJson = prefs.getString(KEY_DELETED_CONVERSATIONS, null)
        val deletedConversations = if (deletedJson != null) {
            val type = object : TypeToken<List<DeletedConversationData>>() {}.type
            val conversations = gson.fromJson<List<DeletedConversationData>>(deletedJson, type)
            // Update contact names
            conversations.map { conversation ->
                if (conversation.contactName.isNullOrEmpty()) {
                    conversation.copy(contactName = getContactName(conversation.address))
                } else {
                    conversation
                }
            }
        } else {
            emptyList()
        }
        
        if (deletedConversations.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerViewRecycleBin.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerViewRecycleBin.visibility = View.VISIBLE
            adapter.submitList(deletedConversations)
        }
    }
    
    private fun showRecoverConfirmationDialog(deletedConversation: DeletedConversationData) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Restore Conversation")
            .setMessage("Do you want to restore the conversation?")
            .setPositiveButton("Yes") { _, _ ->
                restoreConversation(deletedConversation)
            }
            .setNegativeButton("No", null)
            .create()
        
        // Apply theme to dialog
        dialog.show()
        dialog.window?.decorView?.let { ThemeManager.applyTheme(this, it) }
    }
    
    private fun restoreConversation(deletedConversation: DeletedConversationData) {
        // Remove from recycle bin
        val deletedJson = prefs.getString(KEY_DELETED_CONVERSATIONS, null)
        if (deletedJson != null) {
            val type = object : TypeToken<List<DeletedConversationData>>() {}.type
            val deletedConversations = gson.fromJson<List<DeletedConversationData>>(deletedJson, type).toMutableList()
            deletedConversations.removeAll { it.threadId == deletedConversation.threadId }
            
            val updatedJson = gson.toJson(deletedConversations)
            prefs.edit().putString(KEY_DELETED_CONVERSATIONS, updatedJson).apply()
        }
        
        // Reload the list
        loadDeletedConversations()
        
        // Note: The conversation will appear in main activity when it's reloaded
        // since we're not actually deleting from SMS content provider, just marking as deleted
        // For now, we'll just remove from recycle bin
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
        loadDeletedConversations()
    }
}

