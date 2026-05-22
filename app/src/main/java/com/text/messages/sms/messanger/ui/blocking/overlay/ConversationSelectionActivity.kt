package com.text.messages.sms.messanger.ui.blocking.overlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityConversationSelectionBinding
import com.text.messages.sms.messanger.ui.blocking.CustomBlockingActivity
import com.text.messages.sms.messanger.ui.main.MainViewModel
import com.text.messages.sms.messanger.util.ThemeManager

class ConversationSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityConversationSelectionBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ConversationSelectionAdapter
    private val selectedConversations = mutableSetOf<Long>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.loadConversations("All")
        } else {
            Toast.makeText(
                this,
                getString(R.string.sms_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityConversationSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply theme
        ThemeManager.applyTheme(this, binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupToolbar()
        setupRecyclerView()
        setupDoneButton()
        
        checkSmsPermissionAndLoad()
        observeConversations()
        
        // Apply theme after views are laid out
        binding.root.post {
            ThemeManager.applyTheme(this, binding.root)
        }
    }

    private fun checkSmsPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.loadConversations("All")
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }

    private fun setupToolbar() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationSelectionAdapter(
            onItemClick = { threadId ->
                if (selectedConversations.contains(threadId)) {
                    selectedConversations.remove(threadId)
                } else {
                    selectedConversations.add(threadId)
                }
                adapter.notifyDataSetChanged()
            },
            isSelected = { threadId ->
                selectedConversations.contains(threadId)
            }
        )
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConversations.adapter = adapter
    }

    private fun setupDoneButton() {
        binding.buttonSave.setOnClickListener {
            val selectedContacts = adapter.currentList
                .filter { selectedConversations.contains(it.threadId) }
                .map { conversation ->
                    CustomBlockingActivity.BlockedContact(
                        name = conversation.contactName ?: conversation.address,
                        phoneNumber = conversation.address,
                        photoUri = conversation.photoUri
                    )
                }

            val resultIntent = Intent().apply {
                putParcelableArrayListExtra("selected_contacts", ArrayList(selectedContacts))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun observeConversations() {
        viewModel.conversations.observe(this) { conversations ->
            adapter.submitList(conversations)
        }
    }
}

