package com.quizangomedia.messages.ui.conversation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityConversationDetailBinding

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var viewModel: ConversationDetailViewModel
    private lateinit var adapter: MessageAdapter
    private var threadId: Long = -1
    private var address: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        threadId = intent.getLongExtra("thread_id", -1)
        address = intent.getStringExtra("address") ?: ""
        
        enableEdgeToEdge()
        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        viewModel = ViewModelProvider(this)[ConversationDetailViewModel::class.java]
        
        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        observeMessages()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = address
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapter
    }
    
    private fun setupSendButton() {
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString()
            if (messageText.isNotEmpty()) {
                viewModel.sendMessage(threadId, address, messageText)
                binding.editTextMessage.text?.clear()
            }
        }
    }
    
    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages)
            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
        }
        
        viewModel.loadMessages(threadId)
    }
}

