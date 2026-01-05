package com.quizangomedia.messages.ui.conversation

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityEditQuickResponseBinding
import com.quizangomedia.messages.util.ThemeManager

class EditQuickResponseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditQuickResponseBinding
    private lateinit var adapter: EditQuickMessageAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private var isDeleteMode = false
    private val selectedItems = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityEditQuickResponseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        sharedPreferences = getSharedPreferences("quick_messages", MODE_PRIVATE)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupFAB()
        setupDeleteButton()
    }
    
    private fun setupToolbar() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
        
        binding.buttonDelete.setOnClickListener {
            toggleDeleteMode()
        }
    }
    
    private fun setupRecyclerView() {
        val messages = loadQuickMessages()
        adapter = EditQuickMessageAdapter(messages, isDeleteMode, selectedItems) { position ->
            if (isDeleteMode) {
                adapter.toggleSelection(position)
                updateDeleteButton()
            }
        }
        
        binding.recyclerViewQuickMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewQuickMessages.adapter = adapter
        
        // Enable drag and drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewQuickMessages)
    }
    
    private fun setupFAB() {
        binding.fabAdd.setOnClickListener {
            showAddMessageDialog()
        }
    }
    
    private fun setupDeleteButton() {
        binding.buttonDeleteSelected.setOnClickListener {
            deleteSelectedMessages()
        }
        
        binding.buttonCancelDelete.setOnClickListener {
            exitDeleteMode()
        }
    }
    
    private fun toggleDeleteMode() {
        isDeleteMode = !isDeleteMode
        adapter.setDeleteMode(isDeleteMode)
        
        if (isDeleteMode) {
            binding.layoutDeleteActions.visibility = View.VISIBLE
            binding.fabAdd.visibility = View.GONE
            selectedItems.clear()
            updateDeleteButton()
        } else {
            exitDeleteMode()
        }
    }
    
    private fun exitDeleteMode() {
        isDeleteMode = false
        adapter.setDeleteMode(false)
        binding.buttonDelete.visibility = View.VISIBLE
        binding.layoutDeleteActions.visibility = View.GONE
        binding.fabAdd.visibility = View.VISIBLE
        selectedItems.clear()
        adapter.notifyDataSetChanged()
    }
    
    private fun updateDeleteButton() {
        binding.buttonDeleteSelected.isEnabled = selectedItems.isNotEmpty()
        binding.buttonDeleteSelected.alpha = if (selectedItems.isNotEmpty()) 1f else 0.5f
    }
    
    private fun deleteSelectedMessages() {
        if (selectedItems.isEmpty()) return
        
        val messages = adapter.getMessages().toMutableList()
        val sortedIndices = selectedItems.sortedDescending()
        sortedIndices.forEach { index ->
            if (index < messages.size) {
                messages.removeAt(index)
            }
        }
        
        adapter.updateMessages(messages)
        saveQuickMessages(messages)
        exitDeleteMode()
    }
    
    private fun showAddMessageDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_message, null)
        val editText = dialogView.findViewById<EditText>(R.id.editTextMessage)
        val buttonCancel = dialogView.findViewById<TextView>(R.id.buttonCancel)
        val buttonOk = dialogView.findViewById<TextView>(R.id.buttonOk)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        ThemeManager.applyTheme(this, dialogView)
        
        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        buttonOk.setOnClickListener {
            val message = editText.text.toString().trim()
            if (message.isNotEmpty()) {
                val messages = adapter.getMessages().toMutableList()
                messages.add(message)
                adapter.updateMessages(messages)
                saveQuickMessages(messages)
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
    
    private fun loadQuickMessages(): List<String> {
        val messagesJson = sharedPreferences.getString("quick_messages_list", null)
        return if (messagesJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(messagesJson, type) ?: getDefaultQuickMessages()
        } else {
            getDefaultQuickMessages()
        }
    }
    
    private fun saveQuickMessages(messages: List<String>) {
        val gson = Gson()
        val json = gson.toJson(messages)
        sharedPreferences.edit().putString("quick_messages_list", json).apply()
    }
    
    private fun getDefaultQuickMessages(): List<String> {
        return listOf(
            "What's up?",
            "I'll be running a bit late, but I'll be there soon",
            "Where is the meeting taking place?",
            "Where are you?",
            "How are things?",
            "Please give me a call after receiving this message.",
            "When are we meeting?",
            "I'll let you know a bit.",
            "No problem. I missed your call."
        )
    }
}

