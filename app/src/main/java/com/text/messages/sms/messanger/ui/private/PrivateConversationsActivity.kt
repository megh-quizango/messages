package com.text.messages.sms.messanger.ui.private

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.databinding.ActivityPrivateConversationsBinding
import com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity
import com.text.messages.sms.messanger.ui.main.ConversationSelectionActivity
import com.text.messages.sms.messanger.ui.main.ConversationAdapter
import com.text.messages.sms.messanger.ui.main.MainViewModel
import com.text.messages.sms.messanger.ui.settings.SettingsActivity
import com.text.messages.sms.messanger.util.PrivateConversationStorage
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class PrivateConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateConversationsBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ConversationAdapter

    private var isSelectionMode: Boolean = false
    private var selectedThreadId: Long? = null

    private val conversationSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Reload private conversations
            loadPrivateConversations()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("PrivateConversationsActivity", "PrivateConversationsActivity")
        
        enableEdgeToEdge()
        binding = ActivityPrivateConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupBackButton()
        setupIcons()
        setupRecyclerView()
        setupBannerAd()
        observeConversations()
        loadPrivateConversations()
        
        // Check if opened from notification with specific threadId
        val threadId = intent.getLongExtra("threadId", -1)
        if (threadId != -1L) {
            // Wait for conversations to load, then open the specific conversation
            viewModel.conversations.observe(this) { conversations ->
                val conversation = conversations.find { it.threadId == threadId }
                if (conversation != null) {
                    val detailIntent = Intent(this, ConversationDetailActivity::class.java)
                    detailIntent.putExtra("thread_id", conversation.threadId)
                    detailIntent.putExtra("address", conversation.address)
                    detailIntent.putExtra("contact_name", conversation.contactName ?: "")
                    startActivity(detailIntent)
                    // Remove observer to prevent reopening
                    viewModel.conversations.removeObservers(this)
                }
            }
        }
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }
    }

    private fun setupIcons() {
        binding.imageAdd.setOnClickListener {
            // Open conversation selection activity for private conversations
            val intent = Intent(this, ConversationSelectionActivity::class.java)
            intent.putExtra("is_private", true)
            conversationSelectionLauncher.launch(intent)
        }

        binding.imageSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.imageCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        binding.imageRemove.setOnClickListener {
            val threadId = selectedThreadId
            if (threadId != null) {
                // Optimistically remove from UI immediately
                val updatedList = adapter.currentList.filter { it.threadId != threadId }
                adapter.submitList(updatedList)
                if (updatedList.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                }

                PrivateConversationStorage.removeThreadId(this, threadId)

                // Immediately remove from cache so MainActivity shows the update instantly
                com.text.messages.sms.messanger.util.ConversationCache.removeConversation(threadId)
                
                // Invalidate all category caches to ensure the conversation appears in MainActivity
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("All")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("Personal")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("OTPs")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("Offers")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("Transactions")

                // Preload the restored conversation into cache so MainActivity shows it instantly
                viewModel.loadSingleConversation(this, threadId, category = "All") { /* no-op */ }

                // Sync with device state in background
                loadPrivateConversations()
            }
            exitSelectionMode()
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(
            onConversationClick = { conversation ->
                if (isSelectionMode) {
                    selectConversation(conversation.threadId)
                    return@ConversationAdapter
                }

                // Mark conversation as read when opened
                if (conversation.unreadCount > 0) {
                    val position = adapter.currentList.indexOfFirst { it.threadId == conversation.threadId }
                    markConversationAsRead(conversation.threadId, position)
                }

                val intent = Intent(this, ConversationDetailActivity::class.java)
                intent.putExtra("thread_id", conversation.threadId)
                intent.putExtra("address", conversation.address)
                intent.putExtra("contact_name", conversation.contactName ?: "")
                startActivity(intent)
            },
            onConversationLongClick = { conversation ->
                enterSelectionMode(conversation.threadId)
            }
        )
        
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConversations.adapter = adapter
    }

    private fun enterSelectionMode(threadId: Long) {
        isSelectionMode = true
        selectConversation(threadId)
        updateTopIconsForSelectionMode()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedThreadId = null
        adapter.setSelectedThreadId(null)
        updateTopIconsForSelectionMode()
    }

    private fun selectConversation(threadId: Long) {
        selectedThreadId = threadId
        adapter.setSelectedThreadId(threadId)
    }

    private fun updateTopIconsForSelectionMode() {
        if (isSelectionMode) {
            binding.imageAdd.visibility = View.GONE
            binding.imageSettings.visibility = View.GONE
            binding.imageRemove.visibility = View.VISIBLE
            binding.imageCancelSelection.visibility = View.VISIBLE
        } else {
            binding.imageAdd.visibility = View.VISIBLE
            binding.imageSettings.visibility = View.VISIBLE
            binding.imageRemove.visibility = View.GONE
            binding.imageCancelSelection.visibility = View.GONE
        }
    }

    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }

    private fun observeConversations() {
        viewModel.conversations.observe(this) { conversations ->
            if (conversations.isEmpty()) {
                showEmptyState()
            } else {
                hideEmptyState()
                adapter.submitList(conversations)
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            // Loading state is handled by showing/hiding empty state and recycler view
            if (!isLoading) {
                // After loading, check if list is empty
                val currentList = adapter.currentList
                if (currentList.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                }
            }
        }
    }

    private fun loadPrivateConversations() {
        viewModel.loadPrivateConversations(this)
    }

    private fun markConversationAsRead(threadId: Long, position: Int) {
        // Update adapter without reloading
        val currentList = adapter.currentList.toMutableList()
        val index = if (position >= 0 && position < currentList.size && currentList[position].threadId == threadId) {
            position
        } else {
            currentList.indexOfFirst { it.threadId == threadId }
        }
        
        if (index >= 0) {
            val conversation = currentList[index]
            val updatedConversation = conversation.copy(unreadCount = 0)
            currentList[index] = updatedConversation
            adapter.submitList(currentList)
        }
    }

    private fun showEmptyState() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.recyclerViewConversations.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerViewConversations.visibility = View.VISIBLE
    }
}
