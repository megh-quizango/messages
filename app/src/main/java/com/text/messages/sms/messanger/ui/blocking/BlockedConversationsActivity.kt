package com.text.messages.sms.messanger.ui.blocking

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.databinding.ActivityBlockedConversationsBinding
import com.text.messages.sms.messanger.ui.blocking.overlay.ConversationSelectionActivity
import com.text.messages.sms.messanger.ui.main.MainViewModel
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.BlockedConversationStorage
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class BlockedConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedConversationsBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: BlockedConversationsAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private val blockedConversations = mutableSetOf<Long>()

    companion object {
        private const val REQUEST_CODE_SELECT_CONVERSATIONS = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("BlockedConversationsActivity", "BlockedConversationsActivity")
        
        enableEdgeToEdge()
        binding = ActivityBlockedConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Load blocked conversations from storage
        blockedConversations.addAll(BlockedConversationStorage.getThreadIds(this))
        
        setupBackButton()
        setupAddButton()
        setupRecyclerView()
        setupBannerAd()
        
        // Load blocked conversations specifically
        viewModel.loadBlockedConversations(this)
        observeConversations()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupAddButton() {
        binding.imageAddBlocked.setOnClickListener {
            startActivityForResult(
                Intent(this, ConversationSelectionActivity::class.java),
                REQUEST_CODE_SELECT_CONVERSATIONS
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = BlockedConversationsAdapter(
            onUnblockClick = { threadId ->
                blockedConversations.remove(threadId)
                BlockedConversationStorage.removeThreadId(this, threadId)
                
                // Immediately invalidate all category caches so MainActivity shows the unblocked conversation
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("All")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("Personal")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("OTPs")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("Offers")
                com.text.messages.sms.messanger.util.ConversationCache.invalidate("Transactions")
                
                updateUI()
            }
        )
        binding.recyclerViewBlockedConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewBlockedConversations.adapter = adapter
    }

    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }

    private fun observeConversations() {
        viewModel.conversations.observe(this) { conversations ->
            updateUI(conversations)
        }
    }

    private fun updateUI(conversations: List<com.text.messages.sms.messanger.data.model.Conversation>? = null) {
        // Reload blocked conversations from storage to ensure we have the latest list
        blockedConversations.clear()
        blockedConversations.addAll(BlockedConversationStorage.getThreadIds(this))
        
        val allConversations = conversations ?: viewModel.conversations.value ?: emptyList()
        // Filter to only show conversations that are in the blocked list
        val blocked = allConversations.filter { blockedConversations.contains(it.threadId) }
        if (blocked.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerViewBlockedConversations.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerViewBlockedConversations.visibility = View.VISIBLE
            adapter.submitList(blocked)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_SELECT_CONVERSATIONS) {
            val selectedContacts = data?.getParcelableArrayListExtra<CustomBlockingActivity.BlockedContact>("selected_contacts")
            selectedContacts?.forEach { contact: CustomBlockingActivity.BlockedContact ->
                // Find conversation by phone number and add to blocked list
                viewModel.conversations.value?.firstOrNull { it.address == contact.phoneNumber }?.let {
                    blockedConversations.add(it.threadId)
                    BlockedConversationStorage.addThreadId(this, it.threadId)
                }
            }
            updateUI()
        }
    }
}

