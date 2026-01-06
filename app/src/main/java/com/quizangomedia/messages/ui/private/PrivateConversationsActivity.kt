package com.quizangomedia.messages.ui.private

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
import com.quizangomedia.messages.databinding.ActivityPrivateConversationsBinding
import com.quizangomedia.messages.ui.conversation.ConversationDetailActivity
import com.quizangomedia.messages.ui.main.ConversationSelectionActivity
import com.quizangomedia.messages.ui.main.ConversationAdapter
import com.quizangomedia.messages.ui.main.MainViewModel
import com.quizangomedia.messages.util.PrivateConversationStorage
import com.quizangomedia.messages.util.ThemeManager

class PrivateConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateConversationsBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ConversationAdapter

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
            finish()
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
            // TODO: Implement settings
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
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
        }
        
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConversations.adapter = adapter
    }

    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
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
            val updatedConversation = com.quizangomedia.messages.data.model.Conversation().apply {
                this.threadId = conversation.threadId
                this.address = conversation.address
                this.contactName = conversation.contactName
                this.snippet = conversation.snippet
                this.date = conversation.date
                this.unreadCount = 0
                this.archived = conversation.archived
                this.blocked = conversation.blocked
                this.photoUri = conversation.photoUri
            }
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
