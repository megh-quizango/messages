package com.quizangomedia.messages.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityMainBinding
import com.quizangomedia.messages.ui.compose.ComposeActivity
import com.quizangomedia.messages.ui.contacts.ContactsActivity
import com.quizangomedia.messages.ui.personalize.PersonalizeActivity
import com.quizangomedia.messages.ui.settings.SettingsActivity
import com.quizangomedia.messages.ui.conversation.ConversationDetailActivity
import com.quizangomedia.messages.ui.swipe.SwipeGesturesActivity
import com.quizangomedia.messages.data.model.Conversation
import android.provider.Telephony
import android.content.ContentValues
import android.net.Uri

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ConversationAdapter
    private var selectedTab: TextView? = null
    private var isSettingSelectedItem = false
    private var pendingPhoneCall: String? = null
    private var allConversations: List<Conversation> = emptyList()
    private var currentSearchQuery: String = ""
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, load conversations
            viewModel.loadConversations("All")
        } else {
            // Permission denied
            Toast.makeText(
                this,
                "SMS permission is required to display messages",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private val requestCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && pendingPhoneCall != null) {
            val phoneNumber = pendingPhoneCall!!
            pendingPhoneCall = null
            val dialIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            if (dialIntent.resolveActivity(packageManager) != null) {
                startActivity(dialIntent)
            } else {
                Toast.makeText(this, "No app found to make calls", Toast.LENGTH_SHORT).show()
            }
        } else if (!isGranted) {
            Toast.makeText(
                this,
                "Phone permission is required to make calls",
                Toast.LENGTH_SHORT
            ).show()
            pendingPhoneCall = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Remove extra padding from internal menu container while preserving outer padding
        binding.bottomNavigationView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.bottomNavigationView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // Preserve the padding we set in XML (8dp top and bottom) for breathing room
                val topPadding = binding.bottomNavigationView.paddingTop
                val bottomPadding = binding.bottomNavigationView.paddingBottom
                binding.bottomNavigationView.setPadding(0, topPadding, 0, bottomPadding)
                binding.bottomNavigationView.minimumHeight = 0
                
                // Remove padding from the internal menu container (usually a LinearLayout)
                val menuView = binding.bottomNavigationView.getChildAt(0) as? ViewGroup
                menuView?.let {
                    it.setPadding(0, 0, 0, 0)
                    it.minimumHeight = 0
                    
                    // Remove padding from individual menu item views
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        child?.let { item ->
                            if (item is ViewGroup) {
                                // Keep horizontal padding but remove vertical
                                item.setPadding(item.paddingLeft, 0, item.paddingRight, 0)
                                item.minimumHeight = 0
                            }
                        }
                    }
                }
            }
        })




        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to root so ad view stays above system navigation
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Bottom navigation should not have extra padding from window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { view, insets ->
            // Don't add padding - we want it to be exactly the size of its content
            insets
        }



        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupTabs()
        setupRecyclerView()
        setupSearch()
        setupFab()
        setupBottomNavigation()
        setupBannerAd()
        observeConversations()
        
        // Request SMS permission and load conversations
        checkSmsPermissionAndLoad()
        
        // Set Messages as selected initially
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_messages)
        }
    }
    
    private fun checkSmsPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, load conversations
                viewModel.loadConversations("All")
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }
    
    private fun setupTabs() {
        // Set "All" as initially selected
        selectedTab = binding.tabAll
        binding.tabAll.setOnClickListener { selectTab(it as TextView) }
        binding.tabPersonal.setOnClickListener { selectTab(it as TextView) }
        binding.tabOTPs.setOnClickListener { selectTab(it as TextView) }
        binding.tabOffers.setOnClickListener { selectTab(it as TextView) }
        binding.tabTransactions.setOnClickListener { selectTab(it as TextView) }
    }
    
    private fun selectTab(tab: TextView) {
        // Deselect previous tab
        selectedTab?.let {
            it.setBackgroundResource(R.drawable.bg_tab_unselected)
            it.setTextColor(getColor(R.color.black))
        }
        
        // Select new tab
        tab.setBackgroundResource(R.drawable.bg_tab_selected)
        tab.setTextColor(getColor(R.color.white))
        selectedTab = tab
        
        // Clear search when switching tabs (optional - you can remove this if you want search to persist)
        // binding.editTextSearch.setText("")
        
        // Filter conversations by category
        val category = when (tab.id) {
            R.id.tabAll -> "All"
            R.id.tabPersonal -> "Personal"
            R.id.tabOTPs -> "OTPs"
            R.id.tabOffers -> "Offers"
            R.id.tabTransactions -> "Transactions"
            else -> "All"
        }
        viewModel.loadConversations(category)
    }
    
    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
            // Mark conversation as read when opened (unless there are new messages)
            if (conversation.unreadCount > 0) {
                val position = adapter.currentList.indexOfFirst { it.threadId == conversation.threadId }
                markConversationAsRead(conversation.threadId, position)
            }
            
            val intent = Intent(this, ConversationDetailActivity::class.java)
            intent.putExtra("thread_id", conversation.threadId)
            intent.putExtra("address", conversation.address)
            startActivity(intent)
        }
        
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConversations.adapter = adapter
        
        // Setup swipe gestures
        val swipeHelper = SwipeHelper(this, adapter) { conversation: Conversation, action: SwipeGesturesActivity.SwipeAction ->
            handleSwipeAction(conversation, action)
        }
        val itemTouchHelper = ItemTouchHelper(swipeHelper)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewConversations)
    }
    
    private fun setupSearch() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                filterConversations()
            }
        })
        
        // Clear focus when clicking outside the search field
        setupClickOutsideToClearFocus()
    }
    
    private fun setupClickOutsideToClearFocus() {
        // Make root view focusable so it can receive focus
        binding.root.isFocusableInTouchMode = true
        
        // Clear focus when root view is clicked (but only if search field has focus)
        binding.root.setOnClickListener {
            if (binding.editTextSearch.hasFocus()) {
                clearSearchFocus()
            }
        }
        
        // Clear focus when RecyclerView is scrolled or touched
        binding.recyclerViewConversations.setOnScrollChangeListener { _, _, _, _, _ ->
            if (binding.editTextSearch.hasFocus()) {
                clearSearchFocus()
            }
        }
        
        // Also clear focus when user taps on RecyclerView (but allow item clicks to work)
        binding.recyclerViewConversations.addOnItemTouchListener(
            object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                    if (e.action == android.view.MotionEvent.ACTION_DOWN && binding.editTextSearch.hasFocus()) {
                        clearSearchFocus()
                    }
                    return false // Don't intercept, let RecyclerView handle it
                }
            }
        )
    }
    
    private fun clearSearchFocus() {
        if (binding.editTextSearch.hasFocus()) {
            binding.editTextSearch.clearFocus()
            hideKeyboard()
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editTextSearch.windowToken, 0)
    }
    
    private fun filterConversations() {
        if (currentSearchQuery.isEmpty()) {
            // Show all conversations for current category
            adapter.submitList(allConversations)
            return
        }
        
        val query = currentSearchQuery.lowercase().trim()
        val filtered = allConversations.filter { conversation ->
            // Search in contact name
            val contactName = conversation.contactName?.lowercase() ?: ""
            // Search in phone number/address
            val address = conversation.address.lowercase()
            // Search in message snippet
            val snippet = conversation.snippet.lowercase()
            
            contactName.contains(query) ||
            address.contains(query) ||
            snippet.contains(query)
        }
        
        adapter.submitList(filtered)
    }
    
    private fun markConversationAsRead(threadId: Long, position: Int) {
        // Update adapter without reloading - this will remove the blue dot
        // Don't update device SMS, manage state in app
        updateConversationUnreadCount(threadId, 0, position)
    }
    
    private fun handleSwipeAction(conversation: Conversation, action: SwipeGesturesActivity.SwipeAction) {
        // Find the position of the conversation in the adapter
        val currentList = adapter.currentList
        val position = currentList.indexOfFirst { it.threadId == conversation.threadId }
        
        when (action) {
            SwipeGesturesActivity.SwipeAction.MARK_AS_READ -> {
                markConversationAsRead(conversation.threadId, position)
            }
            SwipeGesturesActivity.SwipeAction.MARK_AS_UNREAD -> {
                markConversationAsUnread(conversation.threadId, position)
            }
            SwipeGesturesActivity.SwipeAction.ARCHIVE -> {
                // TODO: Implement archive functionality
                Toast.makeText(this, "Archive functionality not yet implemented", Toast.LENGTH_SHORT).show()
            }
            SwipeGesturesActivity.SwipeAction.DELETE -> {
                deleteConversation(conversation, position)
            }
            SwipeGesturesActivity.SwipeAction.BLOCK -> {
                // TODO: Implement block functionality
                Toast.makeText(this, "Block functionality not yet implemented", Toast.LENGTH_SHORT).show()
            }
            SwipeGesturesActivity.SwipeAction.CALL -> {
                makePhoneCall(conversation.address)
            }
            SwipeGesturesActivity.SwipeAction.NONE -> {
                // Do nothing
            }
        }
    }
    
    private fun markConversationAsUnread(threadId: Long, position: Int) {
        // Update adapter without reloading - set unread count to 1 to show blue dot
        // Don't update device SMS, manage state in app
        updateConversationUnreadCount(threadId, 1, position)
    }
    
    private fun updateConversationUnreadCount(threadId: Long, unreadCount: Int, position: Int) {
        // Find the conversation in the current list and update it
        val currentList = adapter.currentList.toMutableList()
        val index = if (position >= 0 && position < currentList.size && currentList[position].threadId == threadId) {
            position
        } else {
            currentList.indexOfFirst { it.threadId == threadId }
        }
        
        if (index >= 0) {
            val conversation = currentList[index]
            // Create a new conversation object with updated unread count
            val updatedConversation = Conversation().apply {
                this.threadId = conversation.threadId
                this.address = conversation.address
                this.contactName = conversation.contactName
                this.snippet = conversation.snippet
                this.date = conversation.date
                this.unreadCount = unreadCount
                this.archived = conversation.archived
                this.blocked = conversation.blocked
                this.photoUri = conversation.photoUri
            }
            currentList[index] = updatedConversation
            
            // Update allConversations to keep it in sync
            val allList = allConversations.toMutableList()
            val allIndex = allList.indexOfFirst { it.threadId == threadId }
            if (allIndex >= 0) {
                allList[allIndex] = updatedConversation
                allConversations = allList
            }
            
            // Update the list immediately
            adapter.submitList(currentList)
            
            // Reset swipe position and update UI immediately
            binding.recyclerViewConversations.post {
                val viewHolder = binding.recyclerViewConversations.findViewHolderForAdapterPosition(index)
                viewHolder?.itemView?.let { itemView ->
                    itemView.translationX = 0f
                    itemView.alpha = 1f
                    itemView.clearAnimation()
                }
                // Force immediate UI update to show/hide blue dot
                adapter.notifyItemChanged(index)
            }
        }
    }
    
    private fun deleteConversation(conversation: Conversation, position: Int) {
        try {
            // Save to recycle bin (don't actually delete from SMS, just mark as deleted)
            saveToRecycleBin(conversation)
            
            // Remove from adapter immediately
            val currentList = adapter.currentList.toMutableList()
            if (position >= 0 && position < currentList.size) {
                currentList.removeAt(position)
                
                // Update allConversations to keep it in sync
                val allList = allConversations.toMutableList()
                val allIndex = allList.indexOfFirst { it.threadId == conversation.threadId }
                if (allIndex >= 0) {
                    allList.removeAt(allIndex)
                    allConversations = allList
                }
                
                adapter.submitList(currentList) {
                    // Reset swipe position after list update
                    binding.recyclerViewConversations.post {
                        val viewHolder = binding.recyclerViewConversations.findViewHolderForAdapterPosition(position)
                        viewHolder?.itemView?.translationX = 0f
                        viewHolder?.itemView?.alpha = 1f
                    }
                }
            } else {
                // If position is invalid, refresh the list (which will filter out deleted items)
                viewModel.loadConversations(selectedTab?.text?.toString() ?: "All")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveToRecycleBin(conversation: Conversation) {
        val prefs = getSharedPreferences("recycle_bin", MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        
        // Load existing recycle bin items
        val recycleBinJson = prefs.getString("deleted_conversations", null)
        val deletedConversations = if (recycleBinJson != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<DeletedConversationData>>() {}.type
            gson.fromJson<List<DeletedConversationData>>(recycleBinJson, type).toMutableList()
        } else {
            mutableListOf()
        }
        
        // Add current conversation to recycle bin
        deletedConversations.add(DeletedConversationData(
            threadId = conversation.threadId,
            address = conversation.address,
            contactName = conversation.contactName,
            snippet = conversation.snippet,
            date = conversation.date,
            unreadCount = conversation.unreadCount,
            deletedAt = System.currentTimeMillis()
        ))
        
        // Save back to SharedPreferences
        val updatedJson = gson.toJson(deletedConversations)
        prefs.edit().putString("deleted_conversations", updatedJson).apply()
    }
    
    private fun makePhoneCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val dialIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            if (dialIntent.resolveActivity(packageManager) != null) {
                startActivity(dialIntent)
            } else {
                Toast.makeText(this, "No app found to make calls", Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingPhoneCall = phoneNumber
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }
    
    private fun setupFab() {
        binding.fabStartChat.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            // Ignore if we're programmatically setting the selected item
            if (isSettingSelectedItem) {
                return@setOnItemSelectedListener true
            }
            
            when (item.itemId) {
                R.id.nav_messages -> {
                    // Already on Messages screen
                    true
                }
                R.id.nav_contacts -> {
                    startActivity(Intent(this, ContactsActivity::class.java))
                    true
                }
                R.id.nav_personalize -> {
                    startActivity(Intent(this, PersonalizeActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure Messages tab is selected when activity is visible
        setSelectedNavigationItem(R.id.nav_messages)
    }
    
    private fun setSelectedNavigationItem(itemId: Int) {
        if (binding.bottomNavigationView.selectedItemId != itemId) {
            isSettingSelectedItem = true
            binding.bottomNavigationView.selectedItemId = itemId
            binding.bottomNavigationView.post {
                isSettingSelectedItem = false
            }
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
    
    private fun observeConversations() {
        viewModel.conversations.observe(this) { conversations ->
            // Store all conversations for search filtering
            allConversations = conversations
            // Apply search filter if there's an active search query
            if (currentSearchQuery.isNotEmpty()) {
                filterConversations()
            } else {
                adapter.submitList(conversations)
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.recyclerViewConversations.visibility = if (isLoading) View.GONE else View.VISIBLE
        }
    }
}
