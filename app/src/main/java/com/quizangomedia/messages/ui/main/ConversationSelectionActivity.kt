package com.quizangomedia.messages.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityConversationSelectionBinding
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.util.CustomFilterStorage
import com.quizangomedia.messages.util.ThemeManager

class ConversationSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityConversationSelectionBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ConversationSelectionAdapter
    private var filterId: String = ""
    private val selectedThreadIds = mutableSetOf<Long>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        filterId = intent.getStringExtra("filter_id") ?: ""
        if (filterId.isEmpty()) {
            Toast.makeText(this, "Invalid filter", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Enable edge-to-edge mode for safe area handling
        enableEdgeToEdge()
        
        // Load already selected conversations
        val filter = CustomFilterStorage.getFilter(this, filterId)
        filter?.threadIds?.forEach { threadId ->
            selectedThreadIds.add(threadId)
        }
        
        binding = ActivityConversationSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        // Handle window insets for safe area
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add padding to root to avoid overlapping with system bars
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Handle toolbar insets separately to add top padding for status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add top padding to toolbar for status bar
            view.setPadding(
                view.paddingLeft,
                systemBars.top + view.paddingTop,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        
        // Handle RecyclerView insets to add bottom padding for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerViewConversations) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to RecyclerView for navigation bar
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                systemBars.bottom + view.paddingBottom
            )
            insets
        }
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupToolbar()
        setupRecyclerView()
        setupSaveButton()
        
        // Load all conversations
        viewModel.loadConversations("All")
        
        // Apply theme after views are laid out
        binding.root.post {
            ThemeManager.applyTheme(this, binding.root)
        }
    }
    
    private fun setupToolbar() {
        binding.textTitle.text = "Select Conversations"
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ConversationSelectionAdapter(
            selectedThreadIds = selectedThreadIds,
            onConversationToggle = { conversation, isSelected ->
                if (isSelected) {
                    selectedThreadIds.add(conversation.threadId)
                } else {
                    selectedThreadIds.remove(conversation.threadId)
                }
                adapter.notifyDataSetChanged()
            }
        )
        
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConversations.adapter = adapter
        
        // Observe conversations
        viewModel.conversations.observe(this) { conversations ->
            adapter.submitList(conversations)
            updateEmptyState(conversations.isEmpty())
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                binding.layoutLoadingState.visibility = View.VISIBLE
                binding.recyclerViewConversations.visibility = View.GONE
            } else {
                binding.layoutLoadingState.visibility = View.GONE
                binding.recyclerViewConversations.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            // Save selected conversations to filter
            val filter = CustomFilterStorage.getFilter(this, filterId)
            if (filter != null) {
                filter.threadIds.clear()
                filter.threadIds.addAll(selectedThreadIds)
                CustomFilterStorage.updateFilter(this, filter)
                Toast.makeText(this, "Conversations added to filter", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
}

