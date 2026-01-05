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
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.quizangomedia.messages.observer.SmsContentObserver
import com.quizangomedia.messages.util.ThemeManager

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
        
        // Set navigation bar color to white with black icons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = getColor(android.R.color.white)
            // Set navigation bar icons to dark/black for visibility on white background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var flags = window.decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = flags
            }
        }
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
        observeConversations()
        
        // Request SMS permission and load conversations
        checkSmsPermissionAndLoad()
        
        // Set Messages as selected initially and apply theme after views are laid out
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_messages)
            // Apply theme after bottom nav is fully laid out to ensure colors are applied
            ThemeManager.applyTheme(this, binding.root)
        }
        
        // Also apply theme immediately
        ThemeManager.applyTheme(this, binding.root)
        
        // Register ContentObserver to detect new SMS messages
        registerSmsContentObserver()
        
        // Register receiver for theme changes
        registerThemeChangeReceiver()
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
                val category = when (selectedTab?.id) {
                    R.id.tabAll -> "All"
                    R.id.tabPersonal -> "Personal"
                    R.id.tabOTPs -> "OTPs"
                    R.id.tabOffers -> "Offers"
                    R.id.tabTransactions -> "Transactions"
                    else -> "All"
                }
                Log.d(TAG, "ContentObserver: Reloading conversations for category: $category (background refresh, no loading indicator)")
                // Background refresh - don't show loading indicator to avoid hiding RecyclerView
                viewModel.loadConversations(category, showLoading = false)
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
        Log.d(TAG, "SMS ContentObserver registered for all SMS URIs")
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
        val themeColor = ThemeManager.getThemeColor(this)
        val themeColorLight = ThemeManager.getThemeColorLight(this)
        
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
            allConversations
        } else {
            val query = currentSearchQuery.lowercase().trim()
            allConversations.filter { conversation ->
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
        }
        
        adapter.submitList(listToShow) {
            // Update empty state after list is submitted
            updateEmptyState(listToShow.isEmpty())
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
                    // Update empty state after deletion
                    updateEmptyState(currentList.isEmpty())
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
        setSelectedNavigationItem(R.id.nav_messages)
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
            Log.d(TAG, "observeConversations: Received ${newConversations.size} conversations")
            // Store all conversations for search filtering
            allConversations = newConversations
            // Apply search filter if there's an active search query
            if (currentSearchQuery.isNotEmpty()) {
                Log.d(TAG, "Applying search filter with query: $currentSearchQuery")
                filterConversations()
            } else {
                // Get current list from adapter
                val currentList = adapter.currentList.toMutableList()
                Log.d(TAG, "Current adapter list size: ${currentList.size}, New list size: ${newConversations.size}")
                
                // If current list is empty, just submit the new list
                if (currentList.isEmpty()) {
                    Log.d(TAG, "Current list is empty, submitting new list directly")
                    adapter.submitList(newConversations) {
                        updateEmptyState(newConversations.isEmpty())
                    }
                } else {
                    // Intelligently merge: add new conversations and move updated ones to top
                    Log.d(TAG, "Merging conversation lists")
                    val mergedList = mergeConversationLists(currentList, newConversations)
                    Log.d(TAG, "Merged list size: ${mergedList.size}")
                    
                    // Check if the merged list is actually different from current list
                    val isDifferent = mergedList.size != currentList.size || 
                                     mergedList.zip(currentList).any { (new, old) -> 
                                         new.threadId != old.threadId || 
                                         new.date != old.date || 
                                         new.snippet != old.snippet || 
                                         new.unreadCount != old.unreadCount 
                                     }
                    
                    if (isDifferent) {
                        // Check if we have new messages that require moving items to top
                        val hasNewMessages = mergedList.isNotEmpty() && currentList.isNotEmpty() && 
                                            mergedList.first().threadId != currentList.first().threadId
                        
                        Log.d(TAG, "List has changes, submitting merged list. Has new messages: $hasNewMessages")
                        adapter.submitList(mergedList) {
                            updateEmptyState(mergedList.isEmpty())
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
            binding.progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Hide empty state and RecyclerView when loading
            if (isLoading) {
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerViewConversations.visibility = View.GONE
            } else {
                // After loading, check if list is empty
                val currentList = adapter.currentList
                updateEmptyState(currentList.isEmpty())
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        // Only show empty state if not loading
        val isLoading = viewModel.isLoading.value ?: false
        if (!isLoading) {
            if (isEmpty) {
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
}
