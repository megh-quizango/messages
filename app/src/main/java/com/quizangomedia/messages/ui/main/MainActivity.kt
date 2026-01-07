package com.quizangomedia.messages.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
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
import com.quizangomedia.messages.data.model.CustomFilter
import com.quizangomedia.messages.util.CustomFilterStorage
import com.quizangomedia.messages.util.BlockedConversationStorage
import android.provider.Telephony
import android.content.ContentValues
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.quizangomedia.messages.observer.SmsContentObserver
import com.quizangomedia.messages.util.ThemeManager
import java.util.UUID
import android.widget.EditText

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ConversationAdapter
    private var selectedTab: TextView? = null
    private var isSettingSelectedItem = false
    private var pendingPhoneCall: String? = null
    private var allConversations: List<Conversation> = emptyList()
    private var currentSearchQuery: String = ""
    private var smsContentObserver: SmsContentObserver? = null
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var exitBottomSheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var exitNativeAd: NativeAd? = null
    private var customFilterTabs = mutableMapOf<String, TextView>()
    private var currentCustomFilterId: String? = null
    private var mmsRefreshReceiver: BroadcastReceiver? = null
    private var conversationRestoredReceiver: BroadcastReceiver? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, load conversations with cache
            viewModel.loadConversations("All", showLoading = true, useCache = true)
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
    
    private val conversationSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && currentCustomFilterId != null) {
            // Reload conversations for the current custom filter
            currentCustomFilterId?.let { filterId ->
                viewModel.loadConversationsForCustomFilter(this, filterId, useCache = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
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
        setupMenu()
        setupFab()
        setupBottomNavigation()
        setupBannerAd()
        setupBackPressHandler()
        observeConversations()
        loadCustomFilterTabs()
        
        // Request SMS permission and load conversations
        // Use cache first for instant loading
        checkSmsPermissionAndLoad()
        
        // Set Messages as selected initially and apply theme after views are laid out
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_messages)
            // Apply theme after bottom nav is fully laid out to ensure colors are applied
            ThemeManager.applyTheme(this, binding.root)
        }
        
        // Also apply theme immediately
        ThemeManager.applyTheme(this, binding.root)
        
        // Apply theme to loading state progress indicator
        binding.root.post {
            ThemeManager.applyTheme(this, binding.layoutLoadingState)
        }
        
        // Register ContentObserver to detect new SMS messages
        registerSmsContentObserver()
        
        // Register receiver for theme changes
        registerThemeChangeReceiver()
        
        // Register receiver for MMS sent refresh
        registerMmsRefreshReceiver()
        
        // Register receiver for conversation restorations
        registerConversationRestoredReceiver()
    }
    
    private fun registerSmsContentObserver() {
        Log.d(TAG, "registerSmsContentObserver called")
        val handler = Handler(Looper.getMainLooper())
        smsContentObserver = SmsContentObserver(handler) {
            Log.d(TAG, "SMS ContentObserver onChange triggered")
            // Reload conversations when SMS database changes
            // Use postDelayed to debounce rapid changes and allow database to commit
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                val filterId = selectedTab?.tag as? String
                val category = if (filterId != null && customFilterTabs.containsKey(filterId)) {
                    // Custom filter - handled separately
                    null
                } else {
                    when (selectedTab?.id) {
                        R.id.tabAll -> "All"
                        R.id.tabPersonal -> "Personal"
                        R.id.tabOTPs -> "OTPs"
                        R.id.tabOffers -> "Offers"
                        R.id.tabTransactions -> "Transactions"
                        else -> "All"
                    }
                }
                
                if (category != null) {
                    Log.d(TAG, "ContentObserver: Reloading conversations for category: $category (background refresh, no loading indicator)")
                    // Force refresh to get latest data
                    viewModel.loadConversations(category, showLoading = false, useCache = false, forceRefresh = true)
                } else if (filterId != null) {
                    Log.d(TAG, "ContentObserver: Reloading conversations for custom filter: $filterId (background refresh, no loading indicator)")
                    viewModel.loadConversationsForCustomFilter(this@MainActivity, filterId, useCache = false, forceRefresh = true)
                } else {
                    viewModel.loadConversations("All", showLoading = false, useCache = false, forceRefresh = true)
                }
            }, 500) // 500ms delay to ensure database is committed
        }
        
        // Register observer for both inbox and sent SMS
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsContentObserver!!
        )
        contentResolver.registerContentObserver(
            Telephony.Sms.Inbox.CONTENT_URI,
            true,
            smsContentObserver!!
        )
        contentResolver.registerContentObserver(
            Telephony.Sms.Sent.CONTENT_URI,
            true,
            smsContentObserver!!
        )
        // Register observer for MMS as well
        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://mms"),
                true,
                smsContentObserver!!
            )
            contentResolver.registerContentObserver(
                Uri.parse("content://mms/inbox"),
                true,
                smsContentObserver!!
            )
            contentResolver.registerContentObserver(
                Uri.parse("content://mms/sent"),
                true,
                smsContentObserver!!
            )
            Log.d(TAG, "MMS ContentObserver registered")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register MMS ContentObserver", e)
        }
        Log.d(TAG, "SMS and MMS ContentObserver registered for all URIs")
    }
    
    private fun registerThemeChangeReceiver() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Re-apply theme when it changes
                binding.root.post {
                    ThemeManager.applyTheme(this@MainActivity, binding.root)
                    // Also update visible fragments
                    updateVisibleFragments()
                }
                // Also apply immediately
                ThemeManager.applyTheme(this@MainActivity, binding.root)
                updateVisibleFragments()
            }
        }
        registerReceiver(themeChangeReceiver, IntentFilter("com.quizangomedia.messages.THEME_CHANGED"), receiverFlags)
    }
    
    private fun registerMmsRefreshReceiver() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        mmsRefreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "MMS sent refresh broadcast received")
                // Reload conversations when MMS is sent
                val filterId = selectedTab?.tag as? String
                val category = if (filterId != null && customFilterTabs.containsKey(filterId)) {
                    null
                } else {
                    when (selectedTab?.id) {
                        R.id.tabAll -> "All"
                        R.id.tabPersonal -> "Personal"
                        R.id.tabOTPs -> "OTPs"
                        R.id.tabOffers -> "Offers"
                        R.id.tabTransactions -> "Transactions"
                        else -> "All"
                    }
                }
                
                if (category != null) {
                    viewModel.loadConversations(category, showLoading = false, useCache = false, forceRefresh = true)
                } else if (filterId != null) {
                    viewModel.loadConversationsForCustomFilter(this@MainActivity, filterId, useCache = false, forceRefresh = true)
                } else {
                    viewModel.loadConversations("All", showLoading = false, useCache = false, forceRefresh = true)
                }
            }
        }
        registerReceiver(mmsRefreshReceiver, IntentFilter("com.quizangomedia.messages.MMS_SENT_REFRESH"), receiverFlags)
    }
    
    private fun registerConversationRestoredReceiver() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        conversationRestoredReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val threadId = intent?.getLongExtra("thread_id", -1) ?: -1
                if (threadId != -1L) {
                    Log.d(TAG, "Conversation restored broadcast received for threadId: $threadId")
                    
                    // Get current category/filter
                    val filterId = selectedTab?.tag as? String
                    val category = if (filterId != null && customFilterTabs.containsKey(filterId)) {
                        null
                    } else {
                        when (selectedTab?.id) {
                            R.id.tabAll -> "All"
                            R.id.tabPersonal -> "Personal"
                            R.id.tabOTPs -> "OTPs"
                            R.id.tabOffers -> "Offers"
                            R.id.tabTransactions -> "Transactions"
                            else -> "All"
                        }
                    }
                    
                    // Load the single conversation quickly and add it to the current list
                    if (category != null) {
                        // Load conversation and add to list immediately
                        viewModel.loadSingleConversation(this@MainActivity, threadId, category) { restoredConversation ->
                            if (restoredConversation != null) {
                                // Add to current list immediately on main thread
                                binding.recyclerViewConversations.post {
                                    val currentList = adapter.currentList.toMutableList()
                                    
                                    // Remove if already exists (shouldn't happen, but safety check)
                                    currentList.removeAll { it.threadId == threadId }
                                    
                                    // Insert at correct position (sorted by date, newest first)
                                    val insertIndex = currentList.indexOfFirst { it.date < restoredConversation.date }
                                    if (insertIndex >= 0) {
                                        currentList.add(insertIndex, restoredConversation)
                                    } else {
                                        // Add to end if it's the oldest
                                        currentList.add(restoredConversation)
                                    }
                                    
                                    // Update allConversations
                                    allConversations = currentList.filter { it.threadId != -1L }
                                    
                                    // Update adapter with new list - DiffUtil will handle smooth animation
                                    adapter.submitList(currentList) {
                                        Log.d(TAG, "Restored conversation added to list at position: $insertIndex")
                                        updateEmptyState(currentList.isEmpty())
                                    }
                                }
                            } else {
                                // If loading single conversation failed, do a full refresh
                                Log.d(TAG, "Failed to load single conversation, doing full refresh")
                                viewModel.loadConversations(category, showLoading = false, useCache = false, forceRefresh = true)
                            }
                        }
                    } else if (filterId != null) {
                        // For custom filters, do a full refresh (less common case)
                        viewModel.loadConversationsForCustomFilter(this@MainActivity, filterId, useCache = false, forceRefresh = true)
                    } else {
                        // Default to "All"
                        viewModel.loadSingleConversation(this@MainActivity, threadId, "All") { restoredConversation ->
                            if (restoredConversation != null) {
                                binding.recyclerViewConversations.post {
                                    val currentList = adapter.currentList.toMutableList()
                                    currentList.removeAll { it.threadId == threadId }
                                    val insertIndex = currentList.indexOfFirst { it.date < restoredConversation.date }
                                    if (insertIndex >= 0) {
                                        currentList.add(insertIndex, restoredConversation)
                                    } else {
                                        currentList.add(restoredConversation)
                                    }
                                    allConversations = currentList.filter { it.threadId != -1L }
                                    adapter.submitList(currentList) {
                                        updateEmptyState(currentList.isEmpty())
                                    }
                                }
                            } else {
                                viewModel.loadConversations("All", showLoading = false, useCache = false, forceRefresh = true)
                            }
                        }
                    }
                }
            }
        }
        registerReceiver(conversationRestoredReceiver, IntentFilter("com.quizangomedia.messages.CONVERSATION_RESTORED"), receiverFlags)
    }
    
    private fun updateVisibleFragments() {
        // Update theme for visible fragments
        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.findFragmentById(R.id.fragmentContainer)
        currentFragment?.view?.let { fragmentView ->
            ThemeManager.applyTheme(this, fragmentView)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unregister ContentObserver to prevent memory leaks
        smsContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        // Unregister theme change receiver
        themeChangeReceiver?.let {
            unregisterReceiver(it)
        }
        // Unregister MMS refresh receiver
        mmsRefreshReceiver?.let {
            unregisterReceiver(it)
        }
        // Unregister conversation restored receiver
        conversationRestoredReceiver?.let {
            unregisterReceiver(it)
        }
        super.onDestroy()
        exitNativeAd?.destroy()
        exitNativeAd = null
    }
    
    private fun checkSmsPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, load conversations with cache
                viewModel.loadConversations("All", showLoading = true, useCache = true)
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
        binding.tabAddFilter.setOnClickListener { showAddFilterDialog() }
    }
    
    private fun loadCustomFilterTabs() {
        val filters = CustomFilterStorage.loadFilters(this)
        filters.forEach { filter ->
            addCustomFilterTab(filter)
        }
    }
    
    private fun addCustomFilterTab(filter: CustomFilter) {
        val tab = TextView(this).apply {
            text = filter.name
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            setPadding(
                (20 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            textSize = 14f
            setTextColor(getColor(R.color.black))
            background = getDrawable(R.drawable.bg_tab_unselected)
            tag = filter.id
            setOnClickListener { selectCustomFilterTab(it as TextView, filter.id) }
        }
        
        // Insert before the "+" tab
        val addFilterTabIndex = binding.layoutTabs.indexOfChild(binding.tabAddFilter)
        binding.layoutTabs.addView(tab, addFilterTabIndex)
        customFilterTabs[filter.id] = tab
    }
    
    private fun selectCustomFilterTab(tab: TextView, filterId: String) {
        // Cancel any ongoing loading when switching filters
        viewModel.cancelLoading()
        
        currentCustomFilterId = filterId
        selectTab(tab)
        // Use cache first for instant loading
        viewModel.loadConversationsForCustomFilter(this, filterId, useCache = true)
    }
    
    private fun showAddFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_filter, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val editTextFilterName = dialogView.findViewById<EditText>(R.id.editTextFilterName)
        val buttonCancel = dialogView.findViewById<TextView>(R.id.buttonCancel)
        val buttonOk = dialogView.findViewById<TextView>(R.id.buttonOk)
        
        buttonCancel.setOnClickListener { dialog.dismiss() }
        buttonOk.setOnClickListener {
            val filterName = editTextFilterName.text.toString().trim()
            if (filterName.isEmpty()) {
                Toast.makeText(this, "Please enter a filter name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Create new filter
            val filter = CustomFilter(
                id = UUID.randomUUID().toString(),
                name = filterName
            )
            CustomFilterStorage.addFilter(this, filter)
            addCustomFilterTab(filter)
            dialog.dismiss()
            
            // Select the new filter tab
            customFilterTabs[filter.id]?.let { tab ->
                selectCustomFilterTab(tab, filter.id)
            }
        }
        
        dialog.show()
    }
    
    private fun selectTab(tab: TextView) {
        val themeColor = ThemeManager.getThemeColor(this)
        val themeColorLight = ThemeManager.getThemeColorLight(this)
        
        // Cancel any ongoing loading when switching tabs
        viewModel.cancelLoading()
        
        // Deselect previous tab
        selectedTab?.let {
            // Create new drawable with theme light color dynamically
            val unselectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 50f * resources.displayMetrics.density
                setColor(themeColorLight)
            }
            it.background = unselectedDrawable
            it.setTextColor(getColor(R.color.black))
        }
        
        // Select new tab - create new drawable with theme color dynamically
        val selectedDrawable = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 50f * resources.displayMetrics.density
            setColor(themeColor)
        }
        tab.background = selectedDrawable
        tab.setTextColor(getColor(R.color.white))
        selectedTab = tab
        
        // Clear search when switching tabs (optional - you can remove this if you want search to persist)
        // binding.editTextSearch.setText("")
        
        // Check if this is a custom filter tab
        val filterId = tab.tag as? String
        if (filterId != null && customFilterTabs.containsKey(filterId)) {
            // This is handled by selectCustomFilterTab
            return
        }
        
        // Reset current custom filter if switching to a standard tab
        currentCustomFilterId = null
        
        // Filter conversations by category - use cache for instant loading
        val category = when (tab.id) {
            R.id.tabAll -> "All"
            R.id.tabPersonal -> "Personal"
            R.id.tabOTPs -> "OTPs"
            R.id.tabOffers -> "Offers"
            R.id.tabTransactions -> "Transactions"
            else -> "All"
        }
        // Use cache first, then refresh in background if needed
        viewModel.loadConversations(category, showLoading = false, useCache = true)
    }
    
    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
            // Handle "Add Conversation" item for custom filters
            if (conversation.threadId == -1L && currentCustomFilterId != null) {
                // Open conversation selection activity
                val intent = Intent(this, ConversationSelectionActivity::class.java)
                intent.putExtra("filter_id", currentCustomFilterId)
                conversationSelectionLauncher.launch(intent)
                return@ConversationAdapter
            }
            
            // Mark conversation as read when opened (unless there are new messages)
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
    
    private fun setupMenu() {
        binding.imageViewMenu.setOnClickListener {
            showPopupMenu(it)
        }
    }
    
    private fun showPopupMenu(anchor: View) {
        // Create a custom popup window instead of PopupMenu for better control
        val popupView = layoutInflater.inflate(R.layout.popup_menu_custom, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        
        // Set background with rounded corners
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_popup_menu))
        popupWindow.elevation = 8f
        
        // Setup menu items
        val layoutFilterByTime = popupView.findViewById<LinearLayout>(R.id.layoutFilterByTime)
        val layoutMarkAllRead = popupView.findViewById<LinearLayout>(R.id.layoutMarkAllRead)
        
        layoutFilterByTime.setOnClickListener {
            popupWindow.dismiss()
            showFilterTimeBottomSheet()
        }
        
        layoutMarkAllRead.setOnClickListener {
            popupWindow.dismiss()
            markAllAsRead()
        }
        
        // Measure the popup view to get its width
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        // Get screen width to ensure menu fits
        val displayMetrics = resources.displayMetrics
        val maxWidth = (displayMetrics.widthPixels * 0.85).toInt() // 85% of screen width
        val popupWidth = popupView.measuredWidth.coerceAtMost(maxWidth)
        popupWindow.width = popupWidth
        
        // Show popup below the anchor, aligned to the right
        val xOffset = anchor.width - popupWidth
        popupWindow.showAsDropDown(anchor, xOffset, 0)
    }
    
    private fun showFilterTimeBottomSheet() {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_filter_time, null)
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)
        
        // Apply theme to bottom sheet
        ThemeManager.applyTheme(this, bottomSheetView)
        
        // Ensure rounded corners are visible
        bottomSheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        var selectedFilter = "Default"
        val layouts = listOf(
            bottomSheetView.findViewById<LinearLayout>(R.id.layoutDefault),
            bottomSheetView.findViewById<LinearLayout>(R.id.layoutToday),
            bottomSheetView.findViewById<LinearLayout>(R.id.layoutMonth),
            bottomSheetView.findViewById<LinearLayout>(R.id.layoutYear),
            bottomSheetView.findViewById<LinearLayout>(R.id.layoutCustom)
        )
        
        fun updateSelection(selectedLayout: LinearLayout, filterName: String) {
            layouts.forEachIndexed { index, layout ->
                val textView = layout.getChildAt(0) as? TextView
                textView?.setTextColor(if (layout == selectedLayout) getColor(R.color.blue_primary) else getColor(R.color.gray_dark))
            }
            selectedFilter = filterName
        }
        
        bottomSheetView.findViewById<LinearLayout>(R.id.layoutDefault).setOnClickListener {
            updateSelection(it as LinearLayout, "Default")
        }
        
        bottomSheetView.findViewById<LinearLayout>(R.id.layoutToday).setOnClickListener {
            updateSelection(it as LinearLayout, "Today")
        }
        
        bottomSheetView.findViewById<LinearLayout>(R.id.layoutMonth).setOnClickListener {
            updateSelection(it as LinearLayout, "Month")
        }
        
        bottomSheetView.findViewById<LinearLayout>(R.id.layoutYear).setOnClickListener {
            updateSelection(it as LinearLayout, "Year")
        }
        
        bottomSheetView.findViewById<LinearLayout>(R.id.layoutCustom).setOnClickListener {
            updateSelection(it as LinearLayout, "Custom")
            showCustomDateRangePicker(bottomSheetDialog)
        }
        
        bottomSheetView.findViewById<TextView>(R.id.textViewReset).setOnClickListener {
            updateSelection(layouts[0], "Default")
        }
        
        bottomSheetView.findViewById<TextView>(R.id.textViewOk).setOnClickListener {
            applyTimeFilter(selectedFilter)
            bottomSheetDialog.dismiss()
        }
        
        bottomSheetDialog.show()
    }
    
    private fun showCustomDateRangePicker(bottomSheetDialog: com.google.android.material.bottomsheet.BottomSheetDialog) {
        val calendar = java.util.Calendar.getInstance()
        val startDatePicker = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val startCalendar = java.util.Calendar.getInstance()
                startCalendar.set(year, month, dayOfMonth)
                val startTime = startCalendar.timeInMillis
                
                val endDatePicker = android.app.DatePickerDialog(
                    this,
                    { _, year2, month2, dayOfMonth2 ->
                        val endCalendar = java.util.Calendar.getInstance()
                        endCalendar.set(year2, month2, dayOfMonth2, 23, 59, 59)
                        val endTime = endCalendar.timeInMillis
                        
                        if (endTime >= startTime) {
                            applyCustomDateRange(startTime, endTime)
                            bottomSheetDialog.dismiss()
                        } else {
                            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
                        }
                    },
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                )
                endDatePicker.show()
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        startDatePicker.show()
    }
    
    private fun applyTimeFilter(filter: String) {
        val category = when (selectedTab?.id) {
            R.id.tabAll -> "All"
            R.id.tabPersonal -> "Personal"
            R.id.tabOTPs -> "OTPs"
            R.id.tabOffers -> "Offers"
            R.id.tabTransactions -> "Transactions"
            else -> "All"
        }
        viewModel.loadConversations(category, timeFilter = filter)
    }
    
    private fun applyCustomDateRange(startTime: Long, endTime: Long) {
        val category = when (selectedTab?.id) {
            R.id.tabAll -> "All"
            R.id.tabPersonal -> "Personal"
            R.id.tabOTPs -> "OTPs"
            R.id.tabOffers -> "Offers"
            R.id.tabTransactions -> "Transactions"
            else -> "All"
        }
        viewModel.loadConversations(category, timeFilter = "Custom", startDate = startTime, endDate = endTime)
    }
    
    private fun markAllAsRead() {
        viewModel.markAllAsRead()
        // Reload conversations to update UI
        val category = when (selectedTab?.id) {
            R.id.tabAll -> "All"
            R.id.tabPersonal -> "Personal"
            R.id.tabOTPs -> "OTPs"
            R.id.tabOffers -> "Offers"
            R.id.tabTransactions -> "Transactions"
            else -> "All"
        }
        viewModel.loadConversations(category)
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
        val listToShow = if (currentSearchQuery.isEmpty()) {
            // Show all conversations for current category
            // For custom filters, prepend "Add Conversation" item
            if (currentCustomFilterId != null) {
                val addConversationItem = Conversation().apply {
                    threadId = -1L
                    contactName = "Add Conversation"
                    snippet = "Tap to add conversations to this filter"
                    address = ""
                    date = 0
                    unreadCount = 0
                }
                listOf(addConversationItem) + allConversations
            } else {
                allConversations
            }
        } else {
            val query = currentSearchQuery.lowercase().trim()
            val filtered = allConversations.filter { conversation ->
                // Exclude "Add Conversation" item from search
                if (conversation.threadId == -1L) return@filter false
                
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
            
            // For custom filters, still show "Add Conversation" item even when searching
            if (currentCustomFilterId != null) {
                val addConversationItem = Conversation().apply {
                    threadId = -1L
                    contactName = "Add Conversation"
                    snippet = "Tap to add conversations to this filter"
                    address = ""
                    date = 0
                    unreadCount = 0
                }
                listOf(addConversationItem) + filtered
            } else {
                filtered
            }
        }
        
        adapter.submitList(listToShow) {
            // Update empty state after list is submitted
            // Don't show empty state if we have the "Add Conversation" item for custom filters
            val hasRealConversations = listToShow.any { it.threadId != -1L }
            updateEmptyState(listToShow.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
        }
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
                archiveConversation(conversation, position)
            }
            SwipeGesturesActivity.SwipeAction.DELETE -> {
                deleteConversation(conversation, position)
            }
            SwipeGesturesActivity.SwipeAction.BLOCK -> {
                blockConversation(conversation, position)
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
            
            // Update cache with the changed conversation
            com.quizangomedia.messages.util.ConversationCache.updateConversation(threadId, updatedConversation)
            
            // Use incremental update with DiffUtil payload instead of full refresh
            val payload = setOf("unreadCount")
            adapter.notifyItemChanged(index, payload)
            
            // Reset swipe position and update UI immediately
            binding.recyclerViewConversations.post {
                val viewHolder = binding.recyclerViewConversations.findViewHolderForAdapterPosition(index)
                viewHolder?.itemView?.let { itemView ->
                    itemView.translationX = 0f
                    itemView.alpha = 1f
                    itemView.clearAnimation()
                }
            }
        }
    }
    
    private fun deleteConversation(conversation: Conversation, position: Int) {
        try {
            // Save to recycle bin (don't actually delete from SMS, just mark as deleted)
            saveToRecycleBin(conversation)
            
            // Remove from cache
            com.quizangomedia.messages.util.ConversationCache.removeConversation(conversation.threadId)
            
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
                    // Update empty state after deletion
                    updateEmptyState(currentList.isEmpty())
                }
            } else {
                // If position is invalid, refresh the list (which will filter out deleted items)
                val category = when (selectedTab?.id) {
                    R.id.tabAll -> "All"
                    R.id.tabPersonal -> "Personal"
                    R.id.tabOTPs -> "OTPs"
                    R.id.tabOffers -> "Offers"
                    R.id.tabTransactions -> "Transactions"
                    else -> "All"
                }
                viewModel.loadConversations(category, showLoading = false, useCache = false, forceRefresh = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun archiveConversation(conversation: Conversation, position: Int) {
        try {
            // Save to archive
            saveToArchive(conversation)
            
            // Remove from cache
            com.quizangomedia.messages.util.ConversationCache.removeConversation(conversation.threadId)
            
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
                    // Update empty state after archiving
                    updateEmptyState(currentList.isEmpty())
                }
            } else {
                // If position is invalid, refresh the list (which will filter out archived items)
                val category = when (selectedTab?.id) {
                    R.id.tabAll -> "All"
                    R.id.tabPersonal -> "Personal"
                    R.id.tabOTPs -> "OTPs"
                    R.id.tabOffers -> "Offers"
                    R.id.tabTransactions -> "Transactions"
                    else -> "All"
                }
                viewModel.loadConversations(category, showLoading = false, useCache = false, forceRefresh = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveToArchive(conversation: Conversation) {
        val prefs = getSharedPreferences("archived_messages", MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        
        // Load existing archived items
        val archivedJson = prefs.getString("archived_messages_list", null)
        val archivedMessages = if (archivedJson != null) {
            val type = object : com.google.gson.reflect.TypeToken<MutableList<com.quizangomedia.messages.ui.archive.ArchivedMessageData>>() {}.type
            gson.fromJson<MutableList<com.quizangomedia.messages.ui.archive.ArchivedMessageData>>(archivedJson, type)
        } else {
            mutableListOf()
        }
        
        // Check if already archived
        if (!archivedMessages.any { it.threadId == conversation.threadId }) {
            // Add current conversation to archive
            archivedMessages.add(com.quizangomedia.messages.ui.archive.ArchivedMessageData(
                threadId = conversation.threadId,
                address = conversation.address,
                contactName = conversation.contactName,
                snippet = conversation.snippet,
                date = conversation.date,
                unreadCount = conversation.unreadCount
            ))
            
            // Save back to SharedPreferences
            val updatedJson = gson.toJson(archivedMessages)
            prefs.edit().putString("archived_messages_list", updatedJson).apply()
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
    
    private fun blockConversation(conversation: Conversation, position: Int) {
        try {
            // Save to blocked conversations storage
            BlockedConversationStorage.addThreadId(this, conversation.threadId)
            
            // Update Realm conversation as blocked
            try {
                val realm = com.quizangomedia.messages.MessagesApp.realm
                realm.writeBlocking {
                    val existingConversation = query(com.quizangomedia.messages.data.model.Conversation::class, "threadId == ${conversation.threadId}").first().find()
                    if (existingConversation != null) {
                        findLatest(existingConversation)?.apply {
                            this.blocked = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating Realm conversation as blocked", e)
            }
            
            // Remove from cache
            com.quizangomedia.messages.util.ConversationCache.removeConversation(conversation.threadId)
            
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
                    // Update empty state after blocking
                    updateEmptyState(currentList.isEmpty())
                }
                
                Toast.makeText(this, "Conversation blocked", Toast.LENGTH_SHORT).show()
            } else {
                // If position is invalid, refresh the list (which will filter out blocked items)
                val category = when (selectedTab?.id) {
                    R.id.tabAll -> "All"
                    R.id.tabPersonal -> "Personal"
                    R.id.tabOTPs -> "OTPs"
                    R.id.tabOffers -> "Offers"
                    R.id.tabTransactions -> "Transactions"
                    else -> "All"
                }
                viewModel.loadConversations(category, showLoading = false, useCache = false, forceRefresh = true)
                Toast.makeText(this, "Conversation blocked", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to block conversation", Toast.LENGTH_SHORT).show()
        }
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
            startActivity(Intent(this, ContactsActivity::class.java).apply {
                putExtra("from_fab", true)
            })
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
                    showMessagesContent()
                    true
                }
                R.id.nav_contacts -> {
                    navigateToFragment(com.quizangomedia.messages.ui.contacts.ContactsFragment::class.java)
                    true
                }
                R.id.nav_personalize -> {
                    navigateToFragment(com.quizangomedia.messages.ui.personalize.PersonalizeFragment::class.java)
                    true
                }
                R.id.nav_settings -> {
                    navigateToFragment(com.quizangomedia.messages.ui.settings.SettingsFragment::class.java)
                    true
                }
                else -> false
            }
        }
    }
    
    fun navigateToMessages() {
        showMessagesContent()
        setSelectedNavigationItem(R.id.nav_messages)
    }
    
    private fun showMessagesContent() {
        // Hide fragment container, show messages content
        binding.fragmentContainer.visibility = View.GONE
        binding.searchBar.visibility = View.VISIBLE
        binding.categoryTabs.visibility = View.VISIBLE
        binding.recyclerViewConversations.visibility = View.VISIBLE
        binding.fabStartChat.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = if (allConversations.isEmpty() && currentSearchQuery.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun <T : Fragment> navigateToFragment(fragmentClass: Class<T>) {
        // Cancel any ongoing loading when navigating away from messages screen
        viewModel.cancelLoading()
        
        // Hide messages content, show fragment container
        binding.searchBar.visibility = View.GONE
        binding.categoryTabs.visibility = View.GONE
        binding.recyclerViewConversations.visibility = View.GONE
        binding.fabStartChat.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE
        
        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.findFragmentById(R.id.fragmentContainer)
        
        // Don't replace if already showing this fragment
        if (currentFragment != null && currentFragment.javaClass == fragmentClass) {
            return
        }
        
        val fragment = fragmentClass.newInstance()
        fragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        
        // Set the correct navigation item based on fragment
        val selectedItem = when (fragmentClass) {
            com.quizangomedia.messages.ui.contacts.ContactsFragment::class.java -> R.id.nav_contacts
            com.quizangomedia.messages.ui.personalize.PersonalizeFragment::class.java -> R.id.nav_personalize
            com.quizangomedia.messages.ui.settings.SettingsFragment::class.java -> R.id.nav_settings
            else -> R.id.nav_messages
        }
        setSelectedNavigationItem(selectedItem)
        
        // Apply theme to fragment after it's added
        binding.fragmentContainer.post {
            val fragmentView = fragment.view
            if (fragmentView != null) {
                ThemeManager.applyTheme(this, fragmentView)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure Messages tab is selected when activity is visible
        // Only set if we're showing messages content, not a fragment
        if (binding.fragmentContainer.visibility == View.GONE) {
            binding.bottomNavigationView.post {
                setSelectedNavigationItem(R.id.nav_messages)
            }
        } else {
            // If showing a fragment, set the appropriate selection
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            val selectedItem = when (currentFragment) {
                is com.quizangomedia.messages.ui.contacts.ContactsFragment -> R.id.nav_contacts
                is com.quizangomedia.messages.ui.personalize.PersonalizeFragment -> R.id.nav_personalize
                is com.quizangomedia.messages.ui.settings.SettingsFragment -> R.id.nav_settings
                else -> R.id.nav_messages
            }
            binding.bottomNavigationView.post {
                setSelectedNavigationItem(selectedItem)
            }
        }
    }
    
    private fun setSelectedNavigationItem(itemId: Int) {
        isSettingSelectedItem = true
        
        // First, uncheck all menu items
        for (i in 0 until binding.bottomNavigationView.menu.size()) {
            binding.bottomNavigationView.menu.getItem(i).isChecked = false
        }
        
        // Then check the selected item
        binding.bottomNavigationView.menu.findItem(itemId)?.isChecked = true
        binding.bottomNavigationView.selectedItemId = itemId
        
        // Force refresh
        binding.bottomNavigationView.invalidate()
        binding.bottomNavigationView.post {
            // Force refresh after layout
            binding.bottomNavigationView.invalidate()
            binding.bottomNavigationView.postDelayed({
                isSettingSelectedItem = false
                // One more refresh to ensure tint is applied
                binding.bottomNavigationView.invalidate()
            }, 50)
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
    
    private fun observeConversations() {
        viewModel.conversations.observe(this) { newConversations ->
            Log.d(TAG, "observeConversations: Received ${newConversations.size} conversations, currentCustomFilterId=$currentCustomFilterId")
            // Store all conversations for search filtering
            allConversations = newConversations
            
            // Update cache when new conversations arrive
            if (currentCustomFilterId != null) {
                com.quizangomedia.messages.util.ConversationCache.cacheForFilter(currentCustomFilterId!!, newConversations)
            } else {
                val category = when (selectedTab?.id) {
                    R.id.tabAll -> "All"
                    R.id.tabPersonal -> "Personal"
                    R.id.tabOTPs -> "OTPs"
                    R.id.tabOffers -> "Offers"
                    R.id.tabTransactions -> "Transactions"
                    else -> "All"
                }
                com.quizangomedia.messages.util.ConversationCache.cache(category, newConversations)
            }
            
            // For custom filters, prepend "Add Conversation" item
            val conversationsToShow = if (currentCustomFilterId != null) {
                Log.d(TAG, "observeConversations: Custom filter active, adding 'Add Conversation' item")
                // Create a special conversation item for "Add Conversation"
                val addConversationItem = Conversation().apply {
                    threadId = -1L // Special ID to identify this item
                    contactName = "Add Conversation"
                    snippet = "Tap to add conversations to this filter"
                    address = ""
                    date = 0
                    unreadCount = 0
                }
                val result = listOf(addConversationItem) + newConversations
                Log.d(TAG, "observeConversations: Final list size with 'Add Conversation': ${result.size}")
                result
            } else {
                newConversations
            }
            
            // Apply search filter if there's an active search query
            if (currentSearchQuery.isNotEmpty()) {
                Log.d(TAG, "Applying search filter with query: $currentSearchQuery")
                allConversations = conversationsToShow.filter { it.threadId != -1L }
                filterConversations()
            } else {
                // Get current list from adapter
                val currentList = adapter.currentList.toMutableList()
                Log.d(TAG, "Current adapter list size: ${currentList.size}, New list size: ${conversationsToShow.size}")
                
                // If current list is empty, just submit the new list
                if (currentList.isEmpty()) {
                    Log.d(TAG, "Current list is empty, submitting new list directly")
                    adapter.submitList(conversationsToShow) {
                        // Don't show empty state if we have the "Add Conversation" item
                        val hasRealConversations = conversationsToShow.any { it.threadId != -1L }
                        updateEmptyState(conversationsToShow.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
                    }
                } else {
                    // Intelligently merge: add new conversations and move updated ones to top
                    Log.d(TAG, "Merging conversation lists")
                    val filteredCurrent = currentList.filter { it.threadId != -1L }
                    val filteredNew = conversationsToShow.filter { it.threadId != -1L }
                    val mergedList = mergeConversationLists(filteredCurrent, filteredNew)
                    
                    // Prepend "Add Conversation" item if it's a custom filter
                    val finalList = if (currentCustomFilterId != null) {
                        val addConversationItem = Conversation().apply {
                            threadId = -1L
                            contactName = "Add Conversation"
                            snippet = "Tap to add conversations to this filter"
                            address = ""
                            date = 0
                            unreadCount = 0
                        }
                        listOf(addConversationItem) + mergedList
                    } else {
                        mergedList
                    }
                    
                    Log.d(TAG, "Merged list size: ${finalList.size}")
                    
                    // Check if the merged list is actually different from current list
                    val isDifferent = finalList.size != currentList.size || 
                                     finalList.zip(currentList).any { (new, old) -> 
                                         new.threadId != old.threadId || 
                                         new.date != old.date || 
                                         new.snippet != old.snippet || 
                                         new.unreadCount != old.unreadCount 
                                     }
                    
                    if (isDifferent) {
                        // Check if we have new messages that require moving items to top
                        val currentNonAdd = currentList.filter { it.threadId != -1L }
                        val hasNewMessages = mergedList.isNotEmpty() && currentNonAdd.isNotEmpty() && 
                                            mergedList.first().threadId != currentNonAdd.first().threadId
                        
                        Log.d(TAG, "List has changes, submitting merged list. Has new messages: $hasNewMessages")
                        adapter.submitList(finalList) {
                            // Don't show empty state if we have the "Add Conversation" item
                            val hasRealConversations = finalList.any { it.threadId != -1L }
                            updateEmptyState(finalList.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
                            // Only scroll to top if a conversation was actually moved there
                            if (hasNewMessages) {
                                Log.d(TAG, "Conversation moved to top, scrolling to position 0")
                                binding.recyclerViewConversations.scrollToPosition(0)
                            }
                        }
                    } else {
                        Log.d(TAG, "No changes detected, skipping list update")
                    }
                }
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                // Show loading state
                binding.layoutLoadingState.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerViewConversations.visibility = View.GONE
                // Hide bottom navigation bar during loading
                binding.bottomNavigationView.visibility = View.GONE
            } else {
                // Hide loading state
                binding.layoutLoadingState.visibility = View.GONE
                // Show bottom navigation bar after loading
                binding.bottomNavigationView.visibility = View.VISIBLE
                // After loading, check if list is empty
                val currentList = adapter.currentList
                val hasRealConversations = currentList.any { it.threadId != -1L }
                updateEmptyState(currentList.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        // Only show empty state if not loading
        val isLoading = viewModel.isLoading.value ?: false
        if (!isLoading) {
            // For custom filters, always show recycler view (even if empty) because "Add Conversation" item should be visible
            if (isEmpty && currentCustomFilterId == null) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.recyclerViewConversations.visibility = View.GONE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerViewConversations.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * Intelligently merges new conversations with existing list:
     * - Adds new conversations to the top
     * - Updates existing conversations in place
     * - Moves conversations with new messages to the top
     * - Preserves order of unchanged conversations from current list
     */
    private fun mergeConversationLists(
        currentList: List<Conversation>,
        newList: List<Conversation>
    ): List<Conversation> {
        Log.d(TAG, "mergeConversationLists - current: ${currentList.size}, new: ${newList.size}")
        val newMap = newList.associateBy { it.threadId }
        val conversationsToMoveToTop = mutableListOf<Conversation>()
        val unchangedConversations = mutableListOf<Conversation>()
        val processedThreadIds = mutableSetOf<Long>()
        
        // First pass: identify conversations that have new messages or are completely new
        newList.forEach { newConv ->
            val currentConv = currentList.find { it.threadId == newConv.threadId }
            if (currentConv == null) {
                // New conversation - add to top
                Log.d(TAG, "New conversation ${newConv.threadId}, adding to top")
                conversationsToMoveToTop.add(newConv)
                processedThreadIds.add(newConv.threadId)
            } else {
                // Existing conversation - check if it has new messages
                // Consider it has new message if:
                // 1. Date is newer (new message received)
                // 2. Unread count increased
                // 3. Snippet changed AND date is same or newer (message updated)
                val hasNewMessage = newConv.date > currentConv.date || 
                                   newConv.unreadCount > currentConv.unreadCount ||
                                   (newConv.snippet != currentConv.snippet && newConv.date >= currentConv.date)
                if (hasNewMessage) {
                    // Has new message - move to top
                    Log.d(TAG, "Conversation ${newConv.threadId} has new message (oldDate: ${currentConv.date}, newDate: ${newConv.date}, oldUnread: ${currentConv.unreadCount}, newUnread: ${newConv.unreadCount}), moving to top")
                    conversationsToMoveToTop.add(newConv)
                    processedThreadIds.add(newConv.threadId)
                }
            }
        }
        
        // Sort conversations to move to top by date (newest first)
        conversationsToMoveToTop.sortByDescending { it.date }
        
        // Second pass: preserve order of unchanged conversations from current list
        // This maintains the relative position of conversations that haven't changed
        currentList.forEach { currentConv ->
            if (!processedThreadIds.contains(currentConv.threadId)) {
                // This conversation wasn't moved to top, check if it exists in new list
                val newConv = newMap[currentConv.threadId]
                if (newConv != null) {
                    // Conversation exists in new list but hasn't changed - keep in same relative position
                    unchangedConversations.add(newConv)
                    processedThreadIds.add(newConv.threadId)
                } else {
                    // Conversation was removed from new list - don't add it
                    Log.d(TAG, "Conversation ${currentConv.threadId} removed from new list")
                }
            }
        }
        
        // Third pass: add any remaining new conversations that weren't processed
        // (This shouldn't happen, but it's a safety check)
        newList.forEach { newConv ->
            if (!processedThreadIds.contains(newConv.threadId)) {
                Log.d(TAG, "Adding remaining conversation ${newConv.threadId} to unchanged list")
                unchangedConversations.add(newConv)
            }
        }
        
        Log.d(TAG, "mergeConversationLists result - toTop: ${conversationsToMoveToTop.size}, unchanged: ${unchangedConversations.size}")
        // Combine: conversations with new messages first (sorted by date), then unchanged ones in their original order
        return conversationsToMoveToTop + unchangedConversations
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitBottomSheet()
            }
        })
    }
    
    private fun showExitBottomSheet() {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_exit, null)
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheet.setContentView(bottomSheetView)
        
        // Apply theme
        ThemeManager.applyTheme(this, bottomSheetView)
        
        // Get exit button and apply theme color
        val exitButton = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonExit)
        exitButton?.let {
            val themeColor = ThemeManager.getThemeColor(this)
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        }
        
        // Set exit button click listener
        exitButton?.setOnClickListener {
            finishAffinity()
        }
        
        // Load native ad
        loadExitNativeAd(bottomSheetView)
        
        // Set behavior
        bottomSheet.behavior.isDraggable = true
        bottomSheet.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        exitBottomSheet = bottomSheet
        bottomSheet.show()
        
        // Clean up on dismiss
        bottomSheet.setOnDismissListener {
            exitNativeAd?.destroy()
            exitNativeAd = null
            exitBottomSheet = null
        }
    }
    
    private fun loadExitNativeAd(bottomSheetView: android.view.View) {
        val nativeAdFrame = bottomSheetView.findViewById<android.widget.FrameLayout>(R.id.nativeAdFrame)
        if (nativeAdFrame == null) {
            Log.w(TAG, "Native ad frame not found in exit bottom sheet")
            return
        }
        
        val adLoader = AdLoader.Builder(this, "ca-app-pub-3940256099942544/2247696110")
            .forNativeAd { ad ->
                exitNativeAd = ad
                populateExitNativeAdView(ad, nativeAdFrame)
            }
            .build()
        
        adLoader.loadAd(AdRequest.Builder().build())
    }
    
    private fun populateExitNativeAdView(ad: NativeAd, adFrame: android.widget.FrameLayout) {
        val adView = layoutInflater.inflate(R.layout.native_ad_layout, adFrame, false) as NativeAdView
        adFrame.removeAllViews()
        adFrame.addView(adView)
        
        val adBinding = com.quizangomedia.messages.databinding.NativeAdLayoutBinding.bind(adView)
        
        // Apply theme colors to native ad
        val themeColor = ThemeManager.getThemeColor(this)
        val themeColorLight = ThemeManager.getThemeColorLight(this)
        
        // Apply theme to entire ad view (will handle background)
        ThemeManager.applyTheme(this, adView)
        
        // Apply theme to "Ad" label background
        val adLabel = adView.findViewById<android.widget.TextView>(R.id.nativeAdLabel)
        adLabel?.setBackgroundColor(themeColor)
        
        // Apply theme to info icon
        val infoIcon = adView.findViewById<android.widget.ImageView>(R.id.nativeAdInfoIcon)
        infoIcon?.imageTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Apply theme to call to action button
        adBinding.nativeAdCallToAction.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Register views with NativeAdView
        adView.headlineView = adBinding.nativeAdHeadline
        adView.bodyView = adBinding.nativeAdBody
        adView.callToActionView = adBinding.nativeAdCallToAction
        adView.iconView = adBinding.nativeAdIcon
        
        // Set ad assets
        if (ad.headline != null) {
            adBinding.nativeAdHeadline.text = ad.headline
        }
        if (ad.body != null) {
            adBinding.nativeAdBody.text = ad.body
        }
        if (ad.callToAction != null) {
            adBinding.nativeAdCallToAction.text = ad.callToAction
        }
        
        val icon = ad.icon
        if (icon != null) {
            adBinding.nativeAdIcon.setImageDrawable(icon.drawable)
            adBinding.nativeAdIcon.visibility = android.view.View.VISIBLE
        } else {
            adBinding.nativeAdIcon.visibility = android.view.View.GONE
        }
        
        // Handle main image
        if (ad.images.isNotEmpty() && ad.images[0].drawable != null) {
            adBinding.nativeAdMedia.setImageDrawable(ad.images[0].drawable)
            adBinding.nativeAdMedia.visibility = android.view.View.VISIBLE
        } else {
            adBinding.nativeAdMedia.visibility = android.view.View.GONE
        }
        
        // Register the view
        adView.setNativeAd(ad)
    }
    

}
