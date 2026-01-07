package com.text.messages.sms.messanger.ui.main

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
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityMainBinding
import com.text.messages.sms.messanger.ui.compose.ComposeActivity
import com.text.messages.sms.messanger.ui.contacts.ContactsActivity
import com.text.messages.sms.messanger.ui.personalize.PersonalizeActivity
import com.text.messages.sms.messanger.ui.settings.SettingsActivity
import com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity
import com.text.messages.sms.messanger.ui.swipe.SwipeGesturesActivity
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.CustomFilter
import com.text.messages.sms.messanger.util.CustomFilterStorage
import com.text.messages.sms.messanger.util.BlockedConversationStorage
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.RemoteConfigHelper
import android.provider.Telephony
import android.content.ContentValues
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.text.messages.sms.messanger.observer.SmsContentObserver
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ConversationCache
import com.text.messages.sms.messanger.MessagesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var savedScrollPositions = mutableMapOf<String, Int>() // Store scroll positions by filter/category
    private var loadingIndicatorHandler: android.os.Handler? = null
    private var loadingIndicatorRunnable: Runnable? = null
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var exitBottomSheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var exitNativeAd: NativeAd? = null
    private var exitNativeAdView: NativeAdView? = null
    private var customFilterTabs = mutableMapOf<String, TextView>()
    private var currentCustomFilterId: String? = null
    private var mmsRefreshReceiver: BroadcastReceiver? = null
    private var conversationRestoredReceiver: BroadcastReceiver? = null
    private var conversationActionReceiver: BroadcastReceiver? = null
    private var conversationUpdateReceiver: BroadcastReceiver? = null
    private var isActivityResumed: Boolean = false // Track if MainActivity is in foreground
    
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
        Log.d(TAG, "=== MainActivity.onCreate() ===")
        AnalyticsHelper.logScreenView("MainActivity", "MainActivity")
        
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "MainActivity.onCreate(): Binding initialized, RecyclerView visibility: ${binding.recyclerViewConversations.visibility}")
        
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
        
        // Ensure search bar is enabled after setup (in case loading state interferes)
        // This will be overridden by loading observer if loading is active
        enableSearchBar()
        
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
        
        // Register receiver for conversation actions (archive/delete/block)
        registerConversationActionReceiver()
        
        // Register receiver for conversation updates (new messages, read status, etc.)
        registerConversationUpdateReceiver()
        
        // Initialize loading indicator handler
        loadingIndicatorHandler = android.os.Handler(android.os.Looper.getMainLooper())
    }
    
    private fun registerSmsContentObserver() {
        Log.d(TAG, "=== registerSmsContentObserver() ===")
        val handler = Handler(Looper.getMainLooper())
        smsContentObserver = SmsContentObserver(handler) {
            Log.d(TAG, "=== SMS ContentObserver onChange triggered ===")
            Log.d(TAG, "ContentObserver: isActivityResumed=$isActivityResumed")
            // Only reload conversations if MainActivity is in the foreground
            if (!isActivityResumed) {
                Log.d(TAG, "ContentObserver: SKIPPING - MainActivity not in foreground (isActivityResumed=false)")
                return@SmsContentObserver
            }
            Log.d(TAG, "ContentObserver: PROCEEDING - MainActivity is in foreground")
            // Reload conversations when SMS database changes
            // Use postDelayed to debounce rapid changes and allow database to commit
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                // Double-check activity is still resumed after delay
                if (!isActivityResumed) {
                    Log.d(TAG, "MainActivity no longer in foreground after delay, skipping reload")
                    return@postDelayed
                }
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
        registerReceiver(themeChangeReceiver, IntentFilter("com.text.messages.sms.messanger.THEME_CHANGED"), receiverFlags)
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
                // Only reload conversations if MainActivity is in the foreground
                if (!isActivityResumed) {
                    Log.d(TAG, "MainActivity not in foreground, skipping MMS refresh reload")
                    return
                }
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
        registerReceiver(mmsRefreshReceiver, IntentFilter("com.text.messages.sms.messanger.MMS_SENT_REFRESH"), receiverFlags)
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
                    
                    // Ensure we're on the main thread
                    Handler(Looper.getMainLooper()).post {
                        try {
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
                                    // Ensure callback runs on main thread
                                    Handler(Looper.getMainLooper()).post {
                                        if (restoredConversation != null) {
                                            try {
                                                // Always update allConversations and cache regardless of activity state
                                                val allList = allConversations.toMutableList()
                                                val existingIndex = allList.indexOfFirst { it.threadId == threadId }
                                                if (existingIndex >= 0) {
                                                    allList[existingIndex] = restoredConversation
                                                } else {
                                                    // Insert at correct position in allConversations
                                                    val allInsertIndex = allList.indexOfFirst { it.date < restoredConversation.date }
                                                    if (allInsertIndex >= 0) {
                                                        allList.add(allInsertIndex, restoredConversation)
                                                    } else {
                                                        allList.add(restoredConversation)
                                                    }
                                                }
                                                allConversations = allList
                                                
                                                // Update cache
                                                ConversationCache.updateConversation(threadId, restoredConversation)
                                                
                                                // Invalidate cache for current category to ensure it's updated
                                                ConversationCache.invalidate(category)
                                                
                                                // Only update UI if MainActivity is in the foreground
                                                if (!isActivityResumed) {
                                                    Log.d(TAG, "MainActivity not in foreground, updated data in background. UI will refresh on resume.")
                                                    return@post
                                                }
                                                
                                                // Check if adapter is initialized
                                                if (!::adapter.isInitialized) {
                                                    Log.w(TAG, "Adapter not initialized yet, will update on next load")
                                                    return@post
                                                }
                                                
                                                // Add to current list immediately on main thread
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
                                                
                                                // Create a new list instance to ensure DiffUtil detects the change
                                                val newList = currentList.toList()
                                                
                                                // Force update on main thread
                                                adapter.submitList(newList) {
                                                    Log.d(TAG, "Successfully restored conversation $threadId at position: $insertIndex")
                                                    updateEmptyState(newList.isEmpty())
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error restoring conversation in adapter", e)
                                            }
                                        } else {
                                            // If loading single conversation failed, do a full refresh
                                            Log.d(TAG, "Failed to load single conversation, doing full refresh")
                                            if (isActivityResumed) {
                                                viewModel.loadConversations(category, showLoading = false, useCache = false, forceRefresh = true)
                                            }
                                        }
                                    }
                                }
                            } else if (filterId != null) {
                                // For custom filters, do a full refresh (less common case)
                                if (isActivityResumed) {
                                    viewModel.loadConversationsForCustomFilter(this@MainActivity, filterId, useCache = false, forceRefresh = true)
                                }
                            } else {
                                // Default to "All"
                                viewModel.loadSingleConversation(this@MainActivity, threadId, "All") { restoredConversation ->
                                    Handler(Looper.getMainLooper()).post {
                                        if (restoredConversation != null) {
                                            try {
                                                // Always update allConversations and cache regardless of activity state
                                                val allList = allConversations.toMutableList()
                                                val existingIndex = allList.indexOfFirst { it.threadId == threadId }
                                                if (existingIndex >= 0) {
                                                    allList[existingIndex] = restoredConversation
                                                } else {
                                                    val allInsertIndex = allList.indexOfFirst { it.date < restoredConversation.date }
                                                    if (allInsertIndex >= 0) {
                                                        allList.add(allInsertIndex, restoredConversation)
                                                    } else {
                                                        allList.add(restoredConversation)
                                                    }
                                                }
                                                allConversations = allList
                                                
                                                // Update cache
                                                ConversationCache.updateConversation(threadId, restoredConversation)
                                                
                                                // Invalidate cache to ensure it's updated
                                                ConversationCache.invalidate("All")
                                                
                                                // Only update UI if MainActivity is in the foreground
                                                if (!isActivityResumed) {
                                                    Log.d(TAG, "MainActivity not in foreground, updated data in background. UI will refresh on resume.")
                                                    return@post
                                                }
                                                
                                                // Check if adapter is initialized
                                                if (!::adapter.isInitialized) {
                                                    Log.w(TAG, "Adapter not initialized yet, will update on next load")
                                                    return@post
                                                }
                                                
                                                val currentList = adapter.currentList.toMutableList()
                                                currentList.removeAll { it.threadId == threadId }
                                                val insertIndex = currentList.indexOfFirst { it.date < restoredConversation.date }
                                                if (insertIndex >= 0) {
                                                    currentList.add(insertIndex, restoredConversation)
                                                } else {
                                                    currentList.add(restoredConversation)
                                                }
                                                
                                                // Create a new list instance to ensure DiffUtil detects the change
                                                val newList = currentList.toList()
                                                
                                                adapter.submitList(newList) {
                                                    Log.d(TAG, "Successfully restored conversation $threadId in All filter")
                                                    updateEmptyState(newList.isEmpty())
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error restoring conversation in adapter", e)
                                            }
                                        } else {
                                            if (isActivityResumed) {
                                                viewModel.loadConversations("All", showLoading = false, useCache = false, forceRefresh = true)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in conversation restored receiver", e)
                        }
                    }
                }
            }
        }
        registerReceiver(conversationRestoredReceiver, IntentFilter("com.text.messages.sms.messanger.CONVERSATION_RESTORED"), receiverFlags)
    }
    
    private fun registerConversationActionReceiver() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        conversationActionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val threadId = intent?.getLongExtra("thread_id", -1) ?: -1
                val action = intent?.getStringExtra("action") ?: ""
                
                if (threadId != -1L && action in listOf("archived", "deleted", "blocked")) {
                    Log.d(TAG, "Conversation $action broadcast received for threadId: $threadId")
                    
                    // Ensure we're on the main thread
                    Handler(Looper.getMainLooper()).post {
                        // Always update data structures (cache, allConversations) regardless of activity state
                        val allIndex = allConversations.indexOfFirst { it.threadId == threadId }
                        if (allIndex >= 0) {
                            val allList = allConversations.toMutableList()
                            allList.removeAt(allIndex)
                            allConversations = allList
                        }
                        ConversationCache.removeConversation(threadId)
                        
                        // Invalidate cache for current category to ensure it's updated
                        val currentCategory = if (currentCustomFilterId != null) {
                            currentCustomFilterId
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
                        if (currentCategory != null) {
                            ConversationCache.invalidate(currentCategory)
                        }
                        
                        // Only update UI if MainActivity is in the foreground
                        if (!isActivityResumed) {
                            Log.d(TAG, "MainActivity not in foreground, updated data in background. UI will refresh on resume.")
                            return@post
                        }
                        
                        // Check if adapter is initialized
                        if (!::adapter.isInitialized) {
                            Log.w(TAG, "Adapter not initialized yet, will update on next load")
                            return@post
                        }
                        
                        try {
                            // Remove conversation from adapter immediately without reloading entire list
                            val currentList = adapter.currentList.toMutableList()
                            val index = currentList.indexOfFirst { it.threadId == threadId }
                            
                            if (index >= 0) {
                                currentList.removeAt(index)
                                
                                // Create a new list instance to ensure DiffUtil detects the change
                                val newList = currentList.toList()
                                
                                // Force update on main thread
                                adapter.submitList(newList) {
                                    // Update empty state after removal
                                    updateEmptyState(newList.isEmpty())
                                    Log.d(TAG, "Successfully removed conversation $threadId from recycler view after $action")
                                }
                            } else {
                                Log.d(TAG, "Conversation $threadId not found in current list, may have been already removed or not in current filter")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating adapter after $action", e)
                        }
                    }
                }
            }
        }
        registerReceiver(conversationActionReceiver, IntentFilter("com.text.messages.sms.messanger.CONVERSATION_ACTION"), receiverFlags)
    }
    
    private fun registerConversationUpdateReceiver() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        conversationUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val threadId = intent?.getLongExtra("thread_id", -1) ?: -1
                val updateType = intent?.getStringExtra("update_type") ?: ""
                
                if (threadId != -1L) {
                    Log.d(TAG, "Conversation update broadcast received for threadId: $threadId, type: $updateType")
                    
                    // Load updated conversation from Realm (always update data, UI update depends on activity state)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val realm = MessagesApp.realm
                            val conversation = realm.query(Conversation::class, "threadId == $threadId").first().find()
                            
                            if (conversation != null) {
                                // Access conversation properties directly (Realm objects are live)
                                val updatedConversation = Conversation().apply {
                                    this.threadId = conversation.threadId
                                    this.address = conversation.address
                                    this.contactName = conversation.contactName
                                    this.snippet = conversation.snippet
                                    this.date = conversation.date
                                    this.unreadCount = conversation.unreadCount
                                    this.archived = conversation.archived
                                    this.blocked = conversation.blocked
                                    this.photoUri = conversation.photoUri
                                }
                                
                                withContext(Dispatchers.Main) {
                                    // Always update allConversations and cache regardless of activity state
                                    val allList = allConversations.toMutableList()
                                    val existingIndex = allList.indexOfFirst { it.threadId == threadId }
                                    if (existingIndex >= 0) {
                                        allList[existingIndex] = updatedConversation
                                    } else {
                                        // Insert at correct position
                                        val insertIndex = allList.indexOfFirst { it.date < updatedConversation.date }
                                        if (insertIndex >= 0) {
                                            allList.add(insertIndex, updatedConversation)
                                        } else {
                                            allList.add(updatedConversation)
                                        }
                                    }
                                    allConversations = allList
                                    
                                    // Update cache
                                    ConversationCache.updateConversation(threadId, updatedConversation)
                                    
                                    // Only update UI if MainActivity is in the foreground
                                    if (!isActivityResumed) {
                                        Log.d(TAG, "MainActivity not in foreground, updated data in background. UI will refresh on resume.")
                                        return@withContext
                                    }
                                    
                                    // Check if adapter is initialized
                                    if (!::adapter.isInitialized) {
                                        Log.w(TAG, "Adapter not initialized yet, will update on next load")
                                        return@withContext
                                    }
                                    
                                    // Update in adapter
                                    updateConversationInAdapter(threadId) { oldConv ->
                                        Conversation().apply {
                                            this.threadId = updatedConversation.threadId
                                            this.address = updatedConversation.address
                                            this.contactName = updatedConversation.contactName
                                            this.snippet = updatedConversation.snippet
                                            this.date = updatedConversation.date
                                            this.unreadCount = updatedConversation.unreadCount
                                            this.archived = updatedConversation.archived
                                            this.blocked = updatedConversation.blocked
                                            this.photoUri = updatedConversation.photoUri
                                        }
                                    }
                                    
                                    // If date changed significantly (new message), might need to reorder
                                    val currentList = adapter.currentList
                                    val currentIndex = currentList.indexOfFirst { it.threadId == threadId }
                                    if (currentIndex > 0 && updatedConversation.date > currentList[0].date) {
                                        // New message received - move to top
                                        val newList = currentList.toMutableList()
                                        newList.removeAt(currentIndex)
                                        newList.add(0, Conversation().apply {
                                            this.threadId = updatedConversation.threadId
                                            this.address = updatedConversation.address
                                            this.contactName = updatedConversation.contactName
                                            this.snippet = updatedConversation.snippet
                                            this.date = updatedConversation.date
                                            this.unreadCount = updatedConversation.unreadCount
                                            this.archived = updatedConversation.archived
                                            this.blocked = updatedConversation.blocked
                                            this.photoUri = updatedConversation.photoUri
                                        })
                                        adapter.submitList(newList)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating conversation from broadcast", e)
                        }
                    }
                }
            }
        }
        registerReceiver(conversationUpdateReceiver, IntentFilter("com.text.messages.sms.messanger.CONVERSATION_UPDATED"), receiverFlags)
    }
    
    private fun updateVisibleFragments() {
        // Update theme for visible fragments
        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.findFragmentById(R.id.fragmentContainer)
        currentFragment?.view?.let { fragmentView ->
            ThemeManager.applyTheme(this, fragmentView)
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "=== MainActivity.onPause() ===")
        Log.d(TAG, "MainActivity.onPause(): Current RecyclerView visibility: ${if (::binding.isInitialized) binding.recyclerViewConversations.visibility else "binding not initialized"}")
        
        // Mark activity as not resumed - prevents recycler view updates when in background
        isActivityResumed = false
        Log.d(TAG, "MainActivity.onPause(): isActivityResumed set to false")
        
        // Hide RecyclerView when MainActivity is paused to prevent it from showing on other activities
        if (::binding.isInitialized) {
            binding.recyclerViewConversations.visibility = View.GONE
            binding.layoutLoadingState.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.GONE
            Log.d(TAG, "MainActivity.onPause(): RecyclerView, loading, and empty state hidden. New visibility: ${binding.recyclerViewConversations.visibility}")
        } else {
            Log.w(TAG, "MainActivity.onPause(): Binding not initialized, cannot hide RecyclerView")
        }
        
        // Reset flag when MainActivity is paused
        // This ensures ad only shows when MainActivity is actually in foreground
        (application as com.text.messages.sms.messanger.MessagesApp).isMainReady = false
        Log.d(TAG, "MainActivity.onPause(): App Open Ad disabled")
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
        // Unregister conversation action receiver
        conversationActionReceiver?.let {
            unregisterReceiver(it)
        }
        // Unregister conversation update receiver
        conversationUpdateReceiver?.let {
            unregisterReceiver(it)
        }
        // Cancel loading indicator handler
        loadingIndicatorHandler?.removeCallbacksAndMessages(null)
        loadingIndicatorRunnable = null
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
        // Prevent tab switching when loading
        if (viewModel.isLoading.value == true) {
            return
        }
        
        // Save current scroll position before switching
        val currentCategory = when (selectedTab?.id) {
            R.id.tabAll -> "All"
            R.id.tabPersonal -> "Personal"
            R.id.tabOTPs -> "OTPs"
            R.id.tabOffers -> "Offers"
            R.id.tabTransactions -> "Transactions"
            else -> currentCustomFilterId ?: "All"
        }
        if (currentCategory != null) {
            val layoutManager = binding.recyclerViewConversations.layoutManager as? LinearLayoutManager
            val scrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
            savedScrollPositions[currentCategory] = scrollPosition
            Log.d(TAG, "Saved scroll position for $currentCategory: $scrollPosition")
        }
        
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
        // Prevent tab switching when loading
        if (viewModel.isLoading.value == true) {
            return
        }
        
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
            
            AnalyticsHelper.logConversationOpened(conversation.threadId.toString())
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
        // Ensure EditText is enabled and focusable
        binding.editTextSearch.isEnabled = true
        binding.editTextSearch.isFocusable = true
        binding.editTextSearch.isFocusableInTouchMode = true
        binding.editTextSearch.isClickable = true
        
        // Ensure EditText can receive focus when clicked
        binding.editTextSearch.setOnClickListener {
            binding.editTextSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.editTextSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        
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
        
        // Clear focus when root view is clicked (but only if search field has focus and search is empty)
        // Child views (like EditText) will receive clicks first, so this won't interfere
        // Don't clear focus if user is actively searching (has text in search bar)
        binding.root.setOnClickListener {
            if (binding.editTextSearch.hasFocus() && currentSearchQuery.isEmpty()) {
                clearSearchFocus()
            }
        }
        
        // Clear focus when RecyclerView is scrolled or touched
        // BUT only if search bar is empty (user is not actively searching)
        binding.recyclerViewConversations.setOnScrollChangeListener { _, _, _, _, _ ->
            // Don't clear focus if user is actively searching (has text in search bar)
            if (binding.editTextSearch.hasFocus() && currentSearchQuery.isEmpty()) {
                clearSearchFocus()
            }
        }
        
        // Also clear focus when user taps on RecyclerView (but allow item clicks to work)
        // BUT only if search bar is empty (user is not actively searching)
        binding.recyclerViewConversations.addOnItemTouchListener(
            object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                    // Don't clear focus if user is actively searching (has text in search bar)
                    if (e.action == android.view.MotionEvent.ACTION_DOWN && 
                        binding.editTextSearch.hasFocus() && 
                        currentSearchQuery.isEmpty()) {
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
        // Only update RecyclerView if MainActivity is in the foreground and adapter is initialized
        if (!isActivityResumed || !::adapter.isInitialized) {
            Log.d(TAG, "MainActivity not in foreground or adapter not initialized, skipping filter update")
            return
        }
        
        // Preserve focus on search bar when filtering
        val hadFocus = binding.editTextSearch.hasFocus()
        
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
            // Ensure loading stops when list is populated
            ensureLoadingStopped()
            // Update empty state after list is submitted
            // Don't show empty state if we have the "Add Conversation" item for custom filters
            val hasRealConversations = listToShow.any { it.threadId != -1L }
            updateEmptyState(listToShow.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
            
            // Restore focus if user was searching (had focus before filtering)
            // This prevents focus loss when list updates during search
            if (hadFocus && currentSearchQuery.isNotEmpty()) {
                binding.editTextSearch.post {
                    binding.editTextSearch.requestFocus()
                    // Show keyboard if it was visible before
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.editTextSearch, InputMethodManager.SHOW_IMPLICIT)
                }
            }
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
    
    /**
     * Update a single conversation in the adapter without reloading the entire list
     * This method updates the conversation, cache, and notifies the adapter of the change
     */
    private fun updateConversationInAdapter(threadId: Long, updateFunction: (Conversation) -> Conversation) {
        val currentList = adapter.currentList.toMutableList()
        val index = currentList.indexOfFirst { it.threadId == threadId }
        
        if (index >= 0) {
            val conversation = currentList[index]
            val updatedConversation = updateFunction(conversation)
            currentList[index] = updatedConversation
            
            // Update allConversations to keep it in sync
            val allList = allConversations.toMutableList()
            val allIndex = allList.indexOfFirst { it.threadId == threadId }
            if (allIndex >= 0) {
                allList[allIndex] = updatedConversation
                allConversations = allList
            }
            
            // Update cache with the changed conversation
            com.text.messages.sms.messanger.util.ConversationCache.updateConversation(threadId, updatedConversation)
            
            // Determine what changed for payload
            val payload = mutableSetOf<String>()
            if (conversation.snippet != updatedConversation.snippet) payload.add("snippet")
            if (conversation.date != updatedConversation.date) payload.add("date")
            if (conversation.unreadCount != updatedConversation.unreadCount) payload.add("unreadCount")
            if (conversation.contactName != updatedConversation.contactName) payload.add("contactName")
            if (conversation.photoUri != updatedConversation.photoUri) payload.add("photoUri")
            
            // Use incremental update with DiffUtil payload if specific fields changed, otherwise full update
            if (payload.isNotEmpty()) {
                adapter.notifyItemChanged(index, payload)
            } else {
                adapter.notifyItemChanged(index)
            }
            
            // If date changed significantly, might need to reorder - check if should move to top
            if (conversation.date != updatedConversation.date && updatedConversation.date > conversation.date) {
                // New message - might need to move to top, but let's be conservative and only update
                // The full reload will handle reordering if needed
            }
        }
    }
    
    private fun updateConversationUnreadCount(threadId: Long, unreadCount: Int, position: Int) {
        updateConversationInAdapter(threadId) { conversation ->
            Conversation().apply {
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
        }
        
        // Reset swipe position if needed
        val index = adapter.currentList.indexOfFirst { it.threadId == threadId }
        if (index >= 0) {
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
        AnalyticsHelper.logConversationDeleted(conversation.threadId.toString())
        try {
            // Save to recycle bin (don't actually delete from SMS, just mark as deleted)
            saveToRecycleBin(conversation)
            
            // Remove from cache
            com.text.messages.sms.messanger.util.ConversationCache.removeConversation(conversation.threadId)
            
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
            com.text.messages.sms.messanger.util.ConversationCache.removeConversation(conversation.threadId)
            
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
            val type = object : com.google.gson.reflect.TypeToken<MutableList<com.text.messages.sms.messanger.ui.archive.ArchivedMessageData>>() {}.type
            gson.fromJson<MutableList<com.text.messages.sms.messanger.ui.archive.ArchivedMessageData>>(archivedJson, type)
        } else {
            mutableListOf()
        }
        
        // Check if already archived
        if (!archivedMessages.any { it.threadId == conversation.threadId }) {
            // Add current conversation to archive
            archivedMessages.add(com.text.messages.sms.messanger.ui.archive.ArchivedMessageData(
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
                val realm = com.text.messages.sms.messanger.MessagesApp.realm
                realm.writeBlocking {
                    val existingConversation = query(com.text.messages.sms.messanger.data.model.Conversation::class, "threadId == ${conversation.threadId}").first().find()
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
            com.text.messages.sms.messanger.util.ConversationCache.removeConversation(conversation.threadId)
            
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
                    navigateToFragment(com.text.messages.sms.messanger.ui.contacts.ContactsFragment::class.java)
                    true
                }
                R.id.nav_personalize -> {
                    navigateToFragment(com.text.messages.sms.messanger.ui.personalize.PersonalizeFragment::class.java)
                    true
                }
                R.id.nav_settings -> {
                    navigateToFragment(com.text.messages.sms.messanger.ui.settings.SettingsFragment::class.java)
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
        Log.d(TAG, "=== showMessagesContent() ===")
        Log.d(TAG, "showMessagesContent: Current RecyclerView visibility: ${binding.recyclerViewConversations.visibility}")
        
        // Hide fragment container, show messages content
        binding.fragmentContainer.visibility = View.GONE
        binding.searchBar.visibility = View.VISIBLE
        binding.categoryTabs.visibility = View.VISIBLE
        
        // Only show FAB and recycler view if not loading
        val isLoading = viewModel.isLoading.value == true
        if (!isLoading) {
            binding.recyclerViewConversations.visibility = View.VISIBLE
            binding.fabStartChat.visibility = View.VISIBLE
            Log.d(TAG, "showMessagesContent: Showing RecyclerView and FAB. New RecyclerView visibility: ${binding.recyclerViewConversations.visibility}")
        } else {
            binding.recyclerViewConversations.visibility = View.GONE
            binding.fabStartChat.visibility = View.GONE
            Log.d(TAG, "showMessagesContent: Hiding RecyclerView and FAB (loading). New RecyclerView visibility: ${binding.recyclerViewConversations.visibility}")
        }
        
        binding.layoutEmptyState.visibility = if (allConversations.isEmpty() && currentSearchQuery.isEmpty() && !isLoading) View.VISIBLE else View.GONE
    }
    
    private fun isFragmentVisible(): Boolean {
        return binding.fragmentContainer.visibility == View.VISIBLE
    }
    
    private fun <T : Fragment> navigateToFragment(fragmentClass: Class<T>) {
        Log.d(TAG, "=== navigateToFragment() ===")
        Log.d(TAG, "navigateToFragment: Navigating to fragment: ${fragmentClass.simpleName}")
        Log.d(TAG, "navigateToFragment: Current RecyclerView visibility: ${binding.recyclerViewConversations.visibility}")
        
        // Cancel any ongoing loading when navigating away from messages screen
        viewModel.cancelLoading()
        
        // Hide messages content, show fragment container
        binding.searchBar.visibility = View.GONE
        binding.categoryTabs.visibility = View.GONE
        binding.recyclerViewConversations.visibility = View.GONE
        binding.fabStartChat.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE
        binding.layoutLoadingState.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE
        
        Log.d(TAG, "navigateToFragment: RecyclerView hidden, fragment container shown. New RecyclerView visibility: ${binding.recyclerViewConversations.visibility}")
        
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
            com.text.messages.sms.messanger.ui.contacts.ContactsFragment::class.java -> R.id.nav_contacts
            com.text.messages.sms.messanger.ui.personalize.PersonalizeFragment::class.java -> R.id.nav_personalize
            com.text.messages.sms.messanger.ui.settings.SettingsFragment::class.java -> R.id.nav_settings
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
        Log.d(TAG, "=== MainActivity.onResume() ===")
        Log.d(TAG, "MainActivity.onResume(): Current RecyclerView visibility: ${if (::binding.isInitialized) binding.recyclerViewConversations.visibility else "binding not initialized"}")
        
        // Mark activity as resumed - allows recycler view updates
        isActivityResumed = true
        Log.d(TAG, "MainActivity.onResume(): isActivityResumed set to true")
        
        // Show RecyclerView when MainActivity is resumed (it was hidden in onPause)
        // But only if we're showing messages, not fragments
        if (::binding.isInitialized && ::adapter.isInitialized) {
            // Check if fragment is visible - if so, don't show RecyclerView
            if (isFragmentVisible()) {
                Log.d(TAG, "MainActivity.onResume(): SKIPPING RecyclerView update - Fragment is visible")
                return
            }
            
            // Check if we should show RecyclerView or empty state
            val currentList = adapter.currentList
            val hasRealConversations = currentList.any { it.threadId != -1L }
            val isEmpty = currentList.isEmpty() || (!hasRealConversations && currentCustomFilterId == null)
            val isLoading = viewModel.isLoading.value ?: false
            
            Log.d(TAG, "MainActivity.onResume(): currentList.size=${currentList.size}, hasRealConversations=$hasRealConversations, isEmpty=$isEmpty, isLoading=$isLoading")
            
            if (!isLoading) {
                // Not loading - show RecyclerView or empty state
                if (isEmpty) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.recyclerViewConversations.visibility = View.GONE
                    Log.d(TAG, "MainActivity.onResume(): Showing empty state, hiding RecyclerView")
                } else {
                    binding.recyclerViewConversations.visibility = View.VISIBLE
                    binding.layoutEmptyState.visibility = View.GONE
                    Log.d(TAG, "MainActivity.onResume(): Showing RecyclerView, hiding empty state. New visibility: ${binding.recyclerViewConversations.visibility}")
                }
                binding.layoutLoadingState.visibility = View.GONE
            } else {
                // Still loading - show loading state
                binding.layoutLoadingState.visibility = View.VISIBLE
                binding.recyclerViewConversations.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                Log.d(TAG, "MainActivity.onResume(): Showing loading state, hiding RecyclerView")
            }
        } else {
            Log.w(TAG, "MainActivity.onResume(): Binding or adapter not initialized. binding=${::binding.isInitialized}, adapter=${::adapter.isInitialized}")
        }
        
        // Refresh the list to ensure it's up to date if there were changes while in background
        // Use cache first for fast loading, but force refresh to get latest data
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
            // Refresh current category without showing loading indicator
            viewModel.loadConversations(category, showLoading = false, useCache = true, forceRefresh = true)
        } else if (filterId != null) {
            // Refresh custom filter
            viewModel.loadConversationsForCustomFilter(this, filterId, useCache = true, forceRefresh = true)
        }
        
        // CRITICAL: Mark MainActivity as ready for App Open Ads
        // This ensures ads only show on stable MainActivity, not on splash/permission screens
        (application as com.text.messages.sms.messanger.MessagesApp).isMainReady = true
        Log.d(TAG, "MainActivity ready - App Open Ad can now be shown")
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
                is com.text.messages.sms.messanger.ui.contacts.ContactsFragment -> R.id.nav_contacts
                is com.text.messages.sms.messanger.ui.personalize.PersonalizeFragment -> R.id.nav_personalize
                is com.text.messages.sms.messanger.ui.settings.SettingsFragment -> R.id.nav_settings
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
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
    
    private fun observeConversations() {
        Log.d(TAG, "=== observeConversations() setup ===")
        viewModel.conversations.observe(this) { newConversations ->
            Log.d(TAG, "=== observeConversations: CALLED ===")
            Log.d(TAG, "observeConversations: Received ${newConversations.size} conversations, currentCustomFilterId=$currentCustomFilterId")
            Log.d(TAG, "observeConversations: isActivityResumed=$isActivityResumed, adapter initialized=${::adapter.isInitialized}")
            Log.d(TAG, "observeConversations: RecyclerView visibility=${if (::binding.isInitialized) binding.recyclerViewConversations.visibility else "binding not initialized"}")
            
            // Store all conversations for search filtering (always update in background)
            allConversations = newConversations
            
            // Update cache when new conversations arrive (always update in background)
            if (currentCustomFilterId != null) {
                com.text.messages.sms.messanger.util.ConversationCache.cacheForFilter(currentCustomFilterId!!, newConversations)
            } else {
                val category = when (selectedTab?.id) {
                    R.id.tabAll -> "All"
                    R.id.tabPersonal -> "Personal"
                    R.id.tabOTPs -> "OTPs"
                    R.id.tabOffers -> "Offers"
                    R.id.tabTransactions -> "Transactions"
                    else -> "All"
                }
                com.text.messages.sms.messanger.util.ConversationCache.cache(category, newConversations)
            }
            
            // Only update RecyclerView if MainActivity is in the foreground and adapter is initialized
            if (!isActivityResumed) {
                Log.d(TAG, "observeConversations: SKIPPING UI UPDATE - MainActivity not in foreground (isActivityResumed=false)")
                Log.d(TAG, "observeConversations: Updated cache and data in background. UI will refresh on resume.")
                // Ensure loading stops when conversations are received (even in background)
                ensureLoadingStopped()
                return@observe
            }
            
            // Check if a fragment is currently visible - if so, don't update RecyclerView
            if (isFragmentVisible()) {
                Log.d(TAG, "observeConversations: SKIPPING UI UPDATE - Fragment is visible (Contacts/Personalize/Settings)")
                Log.d(TAG, "observeConversations: Updated cache and data in background. UI will refresh when returning to messages.")
                ensureLoadingStopped()
                return@observe
            }
            
            // Check if adapter is initialized before updating
            if (!::adapter.isInitialized) {
                Log.d(TAG, "observeConversations: SKIPPING UI UPDATE - Adapter not initialized yet")
                ensureLoadingStopped()
                return@observe
            }
            
            Log.d(TAG, "observeConversations: PROCEEDING with UI update - MainActivity is in foreground and showing messages")
            
            // Ensure loading stops when conversations are received
            ensureLoadingStopped()
            
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
                    Log.d(TAG, "observeConversations: Current list is empty, submitting new list directly")
                    Log.d(TAG, "observeConversations: About to call adapter.submitList() with ${conversationsToShow.size} items")
                    Log.d(TAG, "observeConversations: RecyclerView visibility before submitList: ${binding.recyclerViewConversations.visibility}")
                    adapter.submitList(conversationsToShow) {
                        Log.d(TAG, "observeConversations: adapter.submitList() callback executed")
                        Log.d(TAG, "observeConversations: RecyclerView visibility after submitList: ${binding.recyclerViewConversations.visibility}")
                        Log.d(TAG, "observeConversations: isFragmentVisible=${isFragmentVisible()}")
                        
                        // Don't update UI if fragment is visible
                        if (isFragmentVisible()) {
                            Log.d(TAG, "observeConversations: SKIPPING UI update in callback - Fragment is visible")
                            // Still clear loading state in ViewModel
                            viewModel.clearLoadingState()
                            return@submitList
                        }
                        
                        // Ensure loading stops when list is populated
                        ensureLoadingStopped()
                        // Don't show empty state if we have the "Add Conversation" item
                        val hasRealConversations = conversationsToShow.any { it.threadId != -1L }
                        updateEmptyState(conversationsToShow.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
                    }
                } else {
                    // Check if we're switching filters (current list has different conversations than new list)
                    val currentThreadIds = currentList.filter { it.threadId != -1L }.map { it.threadId }.toSet()
                    val newThreadIds = conversationsToShow.filter { it.threadId != -1L }.map { it.threadId }.toSet()
                    val isFilterSwitch = currentThreadIds != newThreadIds
                    
                    if (isFilterSwitch) {
                        // When switching filters, preserve conversation order based on date
                        // Don't merge - just use the new list sorted by date to maintain original positions
                        Log.d(TAG, "Filter switch detected - using sorted list to preserve positions")
                        val filteredNew = conversationsToShow.filter { it.threadId != -1L }
                        val sortedNewList = filteredNew.sortedByDescending { it.date }
                        
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
                            listOf(addConversationItem) + sortedNewList
                        } else {
                            sortedNewList
                        }
                        
                        Log.d(TAG, "observeConversations: About to call adapter.submitList() for filter switch with ${finalList.size} items")
                        Log.d(TAG, "observeConversations: RecyclerView visibility before submitList: ${binding.recyclerViewConversations.visibility}")
                        adapter.submitList(finalList) {
                            Log.d(TAG, "observeConversations: adapter.submitList() callback executed (filter switch)")
                            Log.d(TAG, "observeConversations: RecyclerView visibility after submitList: ${binding.recyclerViewConversations.visibility}")
                            Log.d(TAG, "observeConversations: isFragmentVisible=${isFragmentVisible()}")
                            
                            // Don't update UI if fragment is visible
                            if (isFragmentVisible()) {
                                Log.d(TAG, "observeConversations: SKIPPING UI update in callback (filter switch) - Fragment is visible")
                                // Still clear loading state in ViewModel
                                viewModel.clearLoadingState()
                                return@submitList
                            }
                            
                            // Ensure loading stops when list is populated
                            ensureLoadingStopped()
                            val hasRealConversations = finalList.any { it.threadId != -1L }
                            updateEmptyState(finalList.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
                            // Restore scroll position after filter switch
                            restoreScrollPosition()
                        }
                    } else {
                        // Same filter - intelligently merge: add new conversations and move updated ones to top
                        Log.d(TAG, "Same filter - merging conversation lists")
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
                            
                            Log.d(TAG, "observeConversations: List has changes, submitting merged list. Has new messages: $hasNewMessages")
                            Log.d(TAG, "observeConversations: About to call adapter.submitList() for merge with ${finalList.size} items")
                            Log.d(TAG, "observeConversations: RecyclerView visibility before submitList: ${binding.recyclerViewConversations.visibility}")
                            adapter.submitList(finalList) {
                                Log.d(TAG, "observeConversations: adapter.submitList() callback executed (merge)")
                                Log.d(TAG, "observeConversations: RecyclerView visibility after submitList: ${binding.recyclerViewConversations.visibility}")
                                Log.d(TAG, "observeConversations: isFragmentVisible=${isFragmentVisible()}")
                                
                                // Don't update UI if fragment is visible
                                if (isFragmentVisible()) {
                                    Log.d(TAG, "observeConversations: SKIPPING UI update in callback (merge) - Fragment is visible")
                                    // Still clear loading state in ViewModel
                                    viewModel.clearLoadingState()
                                    return@submitList
                                }
                                
                                // Ensure loading stops when list is populated
                                ensureLoadingStopped()
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
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d(TAG, "=== isLoading observer: CALLED ===")
            Log.d(TAG, "isLoading observer: isLoading=$isLoading, isActivityResumed=$isActivityResumed")
            Log.d(TAG, "isLoading observer: RecyclerView visibility=${if (::binding.isInitialized) binding.recyclerViewConversations.visibility else "binding not initialized"}")
            
            // Only update UI if MainActivity is in the foreground
            if (!isActivityResumed) {
                Log.d(TAG, "isLoading observer: SKIPPING UI UPDATE - MainActivity not in foreground (isActivityResumed=false)")
                return@observe
            }
            
            // Check if a fragment is currently visible - if so, don't update RecyclerView
            if (isFragmentVisible()) {
                Log.d(TAG, "isLoading observer: SKIPPING UI UPDATE - Fragment is visible (Contacts/Personalize/Settings)")
                return@observe
            }
            
            Log.d(TAG, "isLoading observer: PROCEEDING with UI update - MainActivity is in foreground and showing messages")
            
            if (isLoading) {
                Log.d(TAG, "isLoading observer: Showing loading state, hiding RecyclerView")
                // Show loading indicator immediately with "Syncing messages" text
                binding.layoutLoadingState.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerViewConversations.visibility = View.GONE
                Log.d(TAG, "isLoading observer: RecyclerView visibility after setting to GONE: ${binding.recyclerViewConversations.visibility}")
                
                // Hide bottom nav bar and FAB when loading
                binding.bottomNavigationView.visibility = View.GONE
                binding.fabStartChat.visibility = View.GONE
                
                // Disable search bar and tabs when loading
                disableSearchBar()
                disableTabs()
            } else {
                // Cancel delayed loading indicator since loading finished
                cancelDelayedLoadingIndicator()
                binding.layoutLoadingState.visibility = View.GONE
                
                // Show bottom navigation bar and FAB after loading
                binding.bottomNavigationView.visibility = View.VISIBLE
                binding.fabStartChat.visibility = View.VISIBLE
                
                // Enable search bar and tabs after loading
                enableSearchBar()
                enableTabs()
                
                // Show recycler view after loading
                binding.recyclerViewConversations.visibility = View.VISIBLE
                Log.d(TAG, "isLoading observer: Loading finished, showing RecyclerView. New visibility: ${binding.recyclerViewConversations.visibility}")
                
                // After loading, check if list is empty
                val currentList = adapter.currentList
                val hasRealConversations = currentList.any { it.threadId != -1L }
                updateEmptyState(currentList.isEmpty() || (!hasRealConversations && currentCustomFilterId == null))
            }
        }
    }
    
    /**
     * Ensure loading stops and UI is enabled when list is populated
     * This is called whenever conversations are received to guarantee loading stops
     */
    private fun ensureLoadingStopped() {
        Log.d(TAG, "=== ensureLoadingStopped() ===")
        Log.d(TAG, "ensureLoadingStopped: isFragmentVisible=${isFragmentVisible()}")
        
        // Clear loading state in ViewModel first
        viewModel.clearLoadingState()
        
        // Don't update RecyclerView visibility if fragment is visible
        if (isFragmentVisible()) {
            Log.d(TAG, "ensureLoadingStopped: SKIPPING RecyclerView update - Fragment is visible")
            // Still hide loading state and enable UI elements, but don't show RecyclerView
            binding.layoutLoadingState.visibility = View.GONE
            binding.bottomNavigationView.visibility = View.VISIBLE
            binding.fabStartChat.visibility = View.VISIBLE
            enableSearchBar()
            enableTabs()
            return
        }
        
        // Directly enable UI elements regardless of loading state
        // This ensures UI is always enabled when conversations are received
        binding.layoutLoadingState.visibility = View.GONE
        binding.recyclerViewConversations.visibility = View.VISIBLE
        binding.bottomNavigationView.visibility = View.VISIBLE
        binding.fabStartChat.visibility = View.VISIBLE
        enableSearchBar()
        enableTabs()
        Log.d(TAG, "ensureLoadingStopped: RecyclerView shown. New visibility: ${binding.recyclerViewConversations.visibility}")
    }
    
    /**
     * Disable all tabs to prevent switching during loading
     */
    private fun disableTabs() {
        binding.tabAll.isEnabled = false
        binding.tabAll.alpha = 0.5f
        binding.tabPersonal.isEnabled = false
        binding.tabPersonal.alpha = 0.5f
        binding.tabOTPs.isEnabled = false
        binding.tabOTPs.alpha = 0.5f
        binding.tabOffers.isEnabled = false
        binding.tabOffers.alpha = 0.5f
        binding.tabTransactions.isEnabled = false
        binding.tabTransactions.alpha = 0.5f
        binding.tabAddFilter.isEnabled = false
        binding.tabAddFilter.alpha = 0.5f
        
        // Disable custom filter tabs
        customFilterTabs.values.forEach { tab ->
            tab.isEnabled = false
            tab.alpha = 0.5f
        }
    }
    
    /**
     * Disable search EditText during loading
     */
    private fun disableSearchBar() {
        binding.editTextSearch.isEnabled = false
        binding.editTextSearch.isFocusable = false
        binding.editTextSearch.isFocusableInTouchMode = false
        binding.editTextSearch.isClickable = false
    }
    
    /**
     * Enable search EditText after loading completes
     */
    private fun enableSearchBar() {
        binding.editTextSearch.isEnabled = true
        binding.editTextSearch.isFocusable = true
        binding.editTextSearch.isFocusableInTouchMode = true
        binding.editTextSearch.isClickable = true
    }
    
    /**
     * Enable all tabs after loading completes
     */
    private fun enableTabs() {
        binding.tabAll.isEnabled = true
        binding.tabAll.alpha = 1.0f
        binding.tabPersonal.isEnabled = true
        binding.tabPersonal.alpha = 1.0f
        binding.tabOTPs.isEnabled = true
        binding.tabOTPs.alpha = 1.0f
        binding.tabOffers.isEnabled = true
        binding.tabOffers.alpha = 1.0f
        binding.tabTransactions.isEnabled = true
        binding.tabTransactions.alpha = 1.0f
        binding.tabAddFilter.isEnabled = true
        binding.tabAddFilter.alpha = 1.0f
        
        // Enable custom filter tabs
        customFilterTabs.values.forEach { tab ->
            tab.isEnabled = true
            tab.alpha = 1.0f
        }
    }
    
    /**
     * Start delayed loading indicator - only shows if loading takes more than 1 second
     * NOTE: This is kept for backward compatibility but loading now shows immediately
     */
    private fun startDelayedLoadingIndicator() {
        cancelDelayedLoadingIndicator()
        // Loading indicator now shows immediately in isLoading observer
    }
    
    /**
     * Cancel delayed loading indicator
     */
    private fun cancelDelayedLoadingIndicator() {
        loadingIndicatorRunnable?.let {
            loadingIndicatorHandler?.removeCallbacks(it)
            loadingIndicatorRunnable = null
        }
    }
    
    /**
     * Restore scroll position for current filter/category
     */
    private fun restoreScrollPosition() {
        val category = if (currentCustomFilterId != null) {
            currentCustomFilterId
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
        
        val savedPosition = savedScrollPositions[category]
        if (savedPosition != null && savedPosition > 0) {
            Log.d(TAG, "Restoring scroll position for $category: $savedPosition")
            binding.recyclerViewConversations.post {
                val layoutManager = binding.recyclerViewConversations.layoutManager as? LinearLayoutManager
                if (layoutManager != null && savedPosition < adapter.itemCount) {
                    layoutManager.scrollToPositionWithOffset(savedPosition, 0)
                }
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        Log.d(TAG, "=== updateEmptyState() ===")
        Log.d(TAG, "updateEmptyState: isEmpty=$isEmpty, isActivityResumed=$isActivityResumed, currentCustomFilterId=$currentCustomFilterId")
        Log.d(TAG, "updateEmptyState: RecyclerView visibility before: ${binding.recyclerViewConversations.visibility}")
        Log.d(TAG, "updateEmptyState: isFragmentVisible=${isFragmentVisible()}")
        
        // Don't update if fragment is visible
        if (isFragmentVisible()) {
            Log.d(TAG, "updateEmptyState: SKIPPING - Fragment is visible (Contacts/Personalize/Settings)")
            return
        }
        
        // Only show empty state if not loading
        val isLoading = viewModel.isLoading.value ?: false
        Log.d(TAG, "updateEmptyState: isLoading=$isLoading")
        
        if (!isLoading) {
            // For custom filters, always show recycler view (even if empty) because "Add Conversation" item should be visible
            if (isEmpty && currentCustomFilterId == null) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.recyclerViewConversations.visibility = View.GONE
                Log.d(TAG, "updateEmptyState: Showing empty state, hiding RecyclerView. New visibility: ${binding.recyclerViewConversations.visibility}")
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerViewConversations.visibility = View.VISIBLE
                Log.d(TAG, "updateEmptyState: Showing RecyclerView, hiding empty state. New visibility: ${binding.recyclerViewConversations.visibility}")
            }
        } else {
            Log.d(TAG, "updateEmptyState: Skipping update - still loading")
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
        
        // Initialize native ad view structure first
        initializeExitNativeAdView(bottomSheetView)
        
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
            exitNativeAdView = null
            exitBottomSheet = null
        }
    }
    
    private fun initializeExitNativeAdView(bottomSheetView: android.view.View) {
        val nativeAdFrame = bottomSheetView.findViewById<android.widget.FrameLayout>(R.id.nativeAdFrame)
        if (nativeAdFrame == null) {
            Log.w(TAG, "Native ad frame not found in exit bottom sheet")
            return
        }
        
        // Pre-inflate the native ad view structure so the layout is complete from the start
        exitNativeAdView = layoutInflater.inflate(R.layout.native_ad_layout, nativeAdFrame, false) as NativeAdView
        nativeAdFrame.addView(exitNativeAdView)
        
        val adBinding = com.text.messages.sms.messanger.databinding.NativeAdLayoutBinding.bind(exitNativeAdView!!)
        
        // Apply theme colors to native ad
        val themeColor = ThemeManager.getThemeColor(this)
        
        // Apply theme to entire ad view (will handle background)
        ThemeManager.applyTheme(this, exitNativeAdView!!)
        
        // Apply theme to "Ad" label background
        val adLabel = exitNativeAdView!!.findViewById<android.widget.TextView>(R.id.nativeAdLabel)
        adLabel?.setBackgroundColor(themeColor)
        
        // Apply theme to info icon
        val infoIcon = exitNativeAdView!!.findViewById<android.widget.ImageView>(R.id.nativeAdInfoIcon)
        infoIcon?.imageTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Apply theme to call to action button
        adBinding.nativeAdCallToAction.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Register views with NativeAdView (will be populated when ad loads)
        exitNativeAdView!!.headlineView = adBinding.nativeAdHeadline
        exitNativeAdView!!.bodyView = adBinding.nativeAdBody
        exitNativeAdView!!.callToActionView = adBinding.nativeAdCallToAction
        exitNativeAdView!!.iconView = adBinding.nativeAdIcon
    }
    
    private fun loadExitNativeAd(bottomSheetView: android.view.View) {
        val nativeAdFrame = bottomSheetView.findViewById<android.widget.FrameLayout>(R.id.nativeAdFrame)
        if (nativeAdFrame == null) {
            Log.w(TAG, "Native ad frame not found in exit bottom sheet")
            return
        }
        
        val nativeAdUnitId = RemoteConfigHelper.getNativeAdUnitId()
        val adLoader = AdLoader.Builder(this, nativeAdUnitId)
            .forNativeAd { ad ->
                exitNativeAd = ad
                populateExitNativeAdView(ad)
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, true)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    AnalyticsHelper.logAdLoad("native", nativeAdUnitId, false)
                    AnalyticsHelper.logAdError("native", nativeAdUnitId, loadAdError.code.toString())
                }
                
                override fun onAdClicked() {
                    super.onAdClicked()
                    AnalyticsHelper.logAdClick("native", nativeAdUnitId)
                }
                
                override fun onAdImpression() {
                    super.onAdImpression()
                    AnalyticsHelper.logAdImpression("native", nativeAdUnitId)
                }
            })
            .build()
        
        adLoader.loadAd(AdRequest.Builder().build())
    }
    
    private fun populateExitNativeAdView(ad: NativeAd) {
        // Use the pre-inflated view instead of creating a new one
        val adView = exitNativeAdView ?: return
        val adBinding = com.text.messages.sms.messanger.databinding.NativeAdLayoutBinding.bind(adView)
        
        // Set ad assets (view structure already exists, just populate with data)
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
