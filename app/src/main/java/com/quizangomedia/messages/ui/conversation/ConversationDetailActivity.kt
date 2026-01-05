package com.quizangomedia.messages.ui.conversation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.quizangomedia.messages.observer.SmsContentObserver
import com.quizangomedia.messages.util.ThemeManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.quizangomedia.messages.R
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.databinding.ActivityConversationDetailBinding
import com.quizangomedia.messages.receiver.ScheduledMessageReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ConversationDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ConversationDetail"
    }

    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var viewModel: ConversationDetailViewModel
    private lateinit var adapter: MessageAdapter
    private var threadId: Long = -1
    private var address: String = ""
    private var contactName: String = ""
    private var isScheduling: Boolean = false
    private var scheduledDate: Long? = null
    private var scheduledTime: Int? = null // Hour
    private var scheduledMinute: Int? = null
    private var smsContentObserver: SmsContentObserver? = null
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var bubbleColorChangeReceiver: BroadcastReceiver? = null
    private var fontChangeReceiver: BroadcastReceiver? = null

    private val requestCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initiatePhoneCall()
        } else {
            Toast.makeText(
                this,
                "Phone permission is required to make calls",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        threadId = intent.getLongExtra("thread_id", -1)
        address = intent.getStringExtra("address") ?: ""
        contactName = intent.getStringExtra("contact_name") ?: ""
        isScheduling = intent.getBooleanExtra("is_scheduling", false)
        
        // If contact name is not provided, look it up
        if (contactName.isEmpty() && address.isNotEmpty()) {
            contactName = lookupContactName(address) ?: address
            Log.d(TAG, "onCreate: Looked up contact name for $address: $contactName")
        }
        
        enableEdgeToEdge()
        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configure window to adjust for keyboard
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // When keyboard is visible, don't add bottom padding (keyboard handles it)
            // When keyboard is hidden, add bottom padding for navigation bar
            val bottomPadding = if (ime.bottom > 0) 0 else systemBars.bottom
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
            insets
        }
        
        // Handle IME (keyboard) insets on the input area to push it up
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutInput) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val layoutParams = view.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (ime.bottom > 0) {
                // Keyboard is visible - push input area up by keyboard height only (no extra spacing)
                layoutParams?.bottomMargin = ime.bottom
            } else {
                // Keyboard is hidden - no margin, input area sits at bottom (root handles system bar padding)
                layoutParams?.bottomMargin = 0
            }
            view.layoutParams = layoutParams
            insets
        }
        
        // Fix input area padding to remove extra space
        binding.layoutInput.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.layoutInput.viewTreeObserver.removeOnGlobalLayoutListener(this)
                binding.layoutInput.minimumHeight = 0
            }
        })
        
        viewModel = ViewModelProvider(this)[ConversationDetailViewModel::class.java]
        
        setupToolbar()
        setupRecyclerView()
        setupTextInput()
        setupSendButton()
        setupBannerAd()
        observeMessages()
        
        if (isScheduling) {
            showDatePicker()
        }
        
        // Register ContentObserver to detect new messages in this conversation
        registerSmsContentObserver()
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        // Register broadcast receivers for theme/font/bubble changes
        registerChangeReceivers()
    }
    
    private fun registerChangeReceivers() {
        // Theme change receiver
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                ThemeManager.applyTheme(this@ConversationDetailActivity, binding.root)
            }
        }
        // Use RECEIVER_NOT_EXPORTED for Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(themeChangeReceiver, IntentFilter("com.quizangomedia.messages.THEME_CHANGED"), android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(themeChangeReceiver, IntentFilter("com.quizangomedia.messages.THEME_CHANGED"))
        }
        
        // Bubble color change receiver
        bubbleColorChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Refresh adapter to apply new bubble colors
                adapter.notifyDataSetChanged()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleColorChangeReceiver, IntentFilter("com.quizangomedia.messages.BUBBLE_COLOR_CHANGED"), android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bubbleColorChangeReceiver, IntentFilter("com.quizangomedia.messages.BUBBLE_COLOR_CHANGED"))
        }
        
        // Font change receiver
        fontChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Refresh adapter to apply new font size and family
                adapter.notifyDataSetChanged()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fontChangeReceiver, IntentFilter("com.quizangomedia.messages.FONT_CHANGED"), android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(fontChangeReceiver, IntentFilter("com.quizangomedia.messages.FONT_CHANGED"))
        }
    }
    
    private fun unregisterChangeReceivers() {
        themeChangeReceiver?.let { unregisterReceiver(it) }
        bubbleColorChangeReceiver?.let { unregisterReceiver(it) }
        fontChangeReceiver?.let { unregisterReceiver(it) }
    }
    
    private fun registerSmsContentObserver() {
        val handler = Handler(Looper.getMainLooper())
        smsContentObserver = SmsContentObserver(handler) {
            // Reload messages when SMS database changes
            // Use postDelayed to debounce rapid changes and allow database to commit
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                // Force reload messages
                viewModel.loadMessages(threadId, address)
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
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unregister ContentObserver to prevent memory leaks
        smsContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        // Unregister broadcast receivers
        unregisterChangeReceivers()
    }
    
    private fun setupToolbar() {
        // Use contact name if available, otherwise use address
        val displayName = if (contactName.isNotEmpty() && contactName != address) {
            contactName
        } else {
            address
        }
        binding.textContactName.text = displayName
        Log.d(TAG, "setupToolbar: Display name set to: $displayName (contactName: $contactName, address: $address)")
        binding.buttonBack.setOnClickListener {
            finish()
        }
        binding.buttonCloseSelection.setOnClickListener {
            exitSelectionMode()
        }
        binding.imageCall.setOnClickListener {
            makePhoneCall()
        }
        binding.imageInfo.setOnClickListener {
            // TODO: Implement contact info functionality
        }
        binding.imageAttachment.setOnClickListener {
            // TODO: Implement attachment functionality
        }
        binding.imageStar.setOnClickListener {
            starSelectedMessages()
        }
    }
    
    private var isSelectionMode = false
    private val starredMessageIds = mutableSetOf<Long>()

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            onMessageLongClick = { message ->
                enterSelectionMode(message)
            },
            onMessageClick = { message ->
                if (isSelectionMode) {
                    adapter.toggleSelection(message.id)
                }
            },
            onSelectionChanged = {
                updateSelectionUI()
            }
        )
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapter
        loadStarredMessages()
    }
    
    private fun enterSelectionMode(message: Message) {
        isSelectionMode = true
        adapter.setSelectionMode(true)
        adapter.toggleSelection(message.id)
        showSelectionMode()
        updateSelectionUI()
    }
    
    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        adapter.clearSelection()
        hideSelectionMode()
    }
    
    private fun showSelectionMode() {
        binding.buttonBack.visibility = View.GONE
        binding.textContactName.visibility = View.GONE
        binding.imageCall.visibility = View.GONE
        binding.imageInfo.visibility = View.GONE
        
        binding.buttonCloseSelection.visibility = View.VISIBLE
        binding.textSelectionCount.visibility = View.VISIBLE
        binding.imageStar.visibility = View.VISIBLE
        
        // Update RecyclerView top constraint to start below selection header
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(binding.root)
        constraintSet.clear(binding.recyclerViewMessages.id, ConstraintSet.TOP)
        constraintSet.connect(binding.recyclerViewMessages.id, ConstraintSet.TOP, binding.buttonCloseSelection.id, ConstraintSet.BOTTOM, 0)
        constraintSet.applyTo(binding.root)
    }
    
    private fun hideSelectionMode() {
        binding.buttonBack.visibility = View.VISIBLE
        binding.textContactName.visibility = View.VISIBLE
        binding.imageCall.visibility = View.VISIBLE
        binding.imageInfo.visibility = View.VISIBLE
        
        binding.buttonCloseSelection.visibility = View.GONE
        binding.textSelectionCount.visibility = View.GONE
        binding.imageStar.visibility = View.GONE
        
        // Restore RecyclerView top constraint to start below normal header
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(binding.root)
        constraintSet.clear(binding.recyclerViewMessages.id, ConstraintSet.TOP)
        constraintSet.connect(binding.recyclerViewMessages.id, ConstraintSet.TOP, binding.buttonBack.id, ConstraintSet.BOTTOM, 0)
        constraintSet.applyTo(binding.root)
    }
    
    private fun updateSelectionUI() {
        val selectedCount = adapter.getSelectedMessages().size
        binding.textSelectionCount.text = "$selectedCount selected"
    }
    
    private fun loadStarredMessages() {
        val prefs = getSharedPreferences("starred_messages", MODE_PRIVATE)
        val starredJson = prefs.getString("starred_messages_list", null)
        if (starredJson != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<com.quizangomedia.messages.ui.starred.StarredMessageData>>() {}.type
            val starredMessages = gson.fromJson<List<com.quizangomedia.messages.ui.starred.StarredMessageData>>(starredJson, type)
            starredMessageIds.clear()
            starredMessageIds.addAll(starredMessages.map { it.messageId })
            adapter.setStarredMessages(starredMessageIds)
        }
    }
    
    private fun starSelectedMessages() {
        val selectedIds = adapter.getSelectedMessages()
        if (selectedIds.isEmpty()) return
        
        val prefs = getSharedPreferences("starred_messages", MODE_PRIVATE)
        val starredJson = prefs.getString("starred_messages_list", null)
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<com.quizangomedia.messages.ui.starred.StarredMessageData>>() {}.type
        val starredMessages = if (starredJson != null) {
            gson.fromJson<List<com.quizangomedia.messages.ui.starred.StarredMessageData>>(starredJson, type).toMutableList()
        } else {
            mutableListOf()
        }
        
        // Get current messages to find selected ones
        val currentMessages = viewModel.messages.value ?: emptyList()
        val messagesToStar = currentMessages.filter { item ->
            item is MessageListItem.MessageItem && selectedIds.contains((item as MessageListItem.MessageItem).message.id)
        }.mapNotNull { item ->
            val msg = (item as MessageListItem.MessageItem).message
            if (!starredMessageIds.contains(msg.id)) {
                com.quizangomedia.messages.ui.starred.StarredMessageData(
                    messageId = msg.id,
                    threadId = msg.threadId,
                    address = msg.address,
                    contactName = contactName.ifEmpty { null },
                    body = msg.body,
                    date = msg.date,
                    type = msg.type
                )
            } else null
        }
        
        starredMessages.addAll(messagesToStar)
        starredMessageIds.addAll(selectedIds)
        
        val updatedJson = gson.toJson(starredMessages)
        prefs.edit().putString("starred_messages_list", updatedJson).apply()
        
        adapter.setStarredMessages(starredMessageIds)
        exitSelectionMode()
        Toast.makeText(this, "Messages starred", Toast.LENGTH_SHORT).show()
    }

    private fun setupTextInput() {
        // Set text selection highlight color
        // Use theme color for text selection highlight
        val themeColor = ThemeManager.getThemeColor(this)
        val alpha = (android.graphics.Color.alpha(themeColor) * 0.1f).toInt()
        binding.editTextMessage.highlightColor = (alpha shl 24) or (themeColor and 0x00FFFFFF)
        
        // Monitor text changes to enable/disable send button
        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.toString()?.isNotEmpty() == true
                updateSendButtonState(hasText)
            }
        })
        
        // Initial state - disabled
        updateSendButtonState(false)
    }
    
    private fun updateSendButtonState(enabled: Boolean) {
        if (enabled) {
            binding.buttonSend.setImageResource(R.drawable.ic_send)
            binding.buttonSend.isEnabled = true
            binding.buttonSend.alpha = 1f
        } else {
            binding.buttonSend.setImageResource(R.drawable.ic_send_disabled)
            binding.buttonSend.isEnabled = false
            binding.buttonSend.alpha = 0.5f
        }
    }
    
    private fun setupSendButton() {
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString()
            if (messageText.isNotEmpty()) {
                if (scheduledDate != null && scheduledTime != null && scheduledMinute != null) {
                    scheduleMessage(messageText)
                } else {
                    viewModel.sendMessage(threadId, address, messageText)
                    binding.editTextMessage.text?.clear()
                }
            }
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { newMessages ->
            val currentList = adapter.currentList
            val wasEmpty = currentList.isEmpty()
            
            adapter.submitList(newMessages) {
                loadStarredMessages() // Refresh starred status when messages load
                
                if (newMessages.isNotEmpty()) {
                    // If list was empty or new messages were added, scroll to bottom
                    if (wasEmpty || newMessages.size > currentList.size) {
                        binding.recyclerViewMessages.post {
                            binding.recyclerViewMessages.smoothScrollToPosition(newMessages.size - 1)
                        }
                    }
                }
            }
        }
        
        // Load messages from device SMS for this contact
        viewModel.loadMessages(threadId, address)
    }

    private fun showDatePicker() {
        val themeColor = ThemeManager.getThemeColor(this)
        
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .build()

        datePicker.addOnPositiveButtonClickListener { selectedDate ->
            scheduledDate = selectedDate
            showTimePicker()
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
        
        // Apply theme after picker is shown with multiple attempts to ensure it's applied
        supportFragmentManager.executePendingTransactions()
        
        // Function to apply theme to picker
        fun applyPickerTheme() {
            datePicker.dialog?.window?.decorView?.let { view ->
                // Use aggressive theming for Material pickers
                ThemeManager.applyThemeToMaterialPicker(this, view)
                
                // Also try to access the dialog's content view directly
                datePicker.dialog?.findViewById<View>(android.R.id.content)?.let { contentView ->
                    ThemeManager.applyThemeToMaterialPicker(this, contentView)
                }
                
                // Find and replace purple/black colors directly
                replaceColorsInView(view, themeColor)
            }
        }
        
        // Apply immediately
        applyPickerTheme()
        
        // Use ViewTreeObserver to catch views as they're added
        datePicker.dialog?.window?.decorView?.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                applyPickerTheme()
            }
        })
        
        // Additional delayed attempts with longer delays
        val delays = listOf(50, 100, 200, 300, 500, 800, 1000, 1500, 2000, 3000)
        delays.forEach { delay ->
            Handler(Looper.getMainLooper()).postDelayed({
                applyPickerTheme()
            }, delay.toLong())
        }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val themeColor = ThemeManager.getThemeColor(this)
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText("Select Time")
            .build()

        timePicker.addOnPositiveButtonClickListener {
            scheduledTime = timePicker.hour
            scheduledMinute = timePicker.minute
            showScheduledInfo()
        }

        timePicker.show(supportFragmentManager, "TIME_PICKER")
        
        // Apply theme after picker is shown with multiple attempts to ensure it's applied
        supportFragmentManager.executePendingTransactions()
        
        // Function to apply theme to picker
        fun applyPickerTheme() {
            timePicker.dialog?.window?.decorView?.let { view ->
                // Use aggressive theming for Material pickers
                ThemeManager.applyThemeToMaterialPicker(this, view)
                
                // Also try to access the dialog's content view directly
                timePicker.dialog?.findViewById<View>(android.R.id.content)?.let { contentView ->
                    ThemeManager.applyThemeToMaterialPicker(this, contentView)
                }
                
                // Find and replace purple/black colors directly
                replaceColorsInView(view, themeColor)
            }
        }
        
        // Apply immediately
        applyPickerTheme()
        
        // Use ViewTreeObserver to catch views as they're added
        timePicker.dialog?.window?.decorView?.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                applyPickerTheme()
            }
        })
        
        // Additional delayed attempts with longer delays
        val delays = listOf(50, 100, 200, 300, 500, 800, 1000, 1500, 2000, 3000)
        delays.forEach { delay ->
            Handler(Looper.getMainLooper()).postDelayed({
                applyPickerTheme()
            }, delay.toLong())
        }
    }

    private fun replaceColorsInView(view: View, themeColor: Int) {
        try {
            val purpleColors = listOf(
                android.graphics.Color.parseColor("#6200EE"),
                android.graphics.Color.parseColor("#6750A4"),
                android.graphics.Color.parseColor("#6366F1"),
                android.graphics.Color.parseColor("#7B1FA2"),
                android.graphics.Color.parseColor("#9C27B0"),
                android.graphics.Color.parseColor("#0C56CF")
            )
            val lightPurpleColors = listOf(
                android.graphics.Color.parseColor("#E1BEE7"),
                android.graphics.Color.parseColor("#F3E5F5"),
                android.graphics.Color.parseColor("#E8EAF6"),
                android.graphics.Color.parseColor("#C5CAE9")
            )
            val blackColor = android.graphics.Color.BLACK
            
            // Check for MaterialCardView (time picker containers)
            if (view is com.google.android.material.card.MaterialCardView) {
                val cardBgColor = view.cardBackgroundColor.defaultColor
                if (purpleColors.contains(cardBgColor) || lightPurpleColors.contains(cardBgColor) || 
                    cardBgColor == blackColor) {
                    view.setCardBackgroundColor(themeColor)
                }
            }
            
            // Check background
            val bg = view.background
            if (bg is android.graphics.drawable.ColorDrawable) {
                val bgColor = bg.color
                if (purpleColors.contains(bgColor) || lightPurpleColors.contains(bgColor) || bgColor == blackColor) {
                    view.setBackgroundColor(themeColor)
                    // If this is a TextView with black text, make it white
                    if (view is android.widget.TextView && view.currentTextColor == blackColor) {
                        view.setTextColor(android.graphics.Color.WHITE)
                    }
                }
            } else if (bg is android.graphics.drawable.GradientDrawable) {
                // Check if it's a circular drawable (for date selection circles)
                try {
                    val shape = bg.shape
                    if (shape == android.graphics.drawable.GradientDrawable.OVAL) {
                        // Try to get color using reflection
                        val color = try {
                            val method = android.graphics.drawable.GradientDrawable::class.java.getDeclaredMethod("getColor")
                            method.isAccessible = true
                            val colorStateList = method.invoke(bg) as? android.content.res.ColorStateList
                            colorStateList?.defaultColor
                        } catch (e: Exception) {
                            null
                        }
                        if (color != null && (purpleColors.contains(color) || lightPurpleColors.contains(color))) {
                            bg.setColor(themeColor)
                            view.background = bg
                            // Make text white if it's black
                            if (view is android.widget.TextView && view.currentTextColor == blackColor) {
                                view.setTextColor(android.graphics.Color.WHITE)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Check background tint
            val bgTint = view.backgroundTintList
            if (bgTint != null) {
                val tintColor = bgTint.defaultColor
                if (purpleColors.contains(tintColor) || lightPurpleColors.contains(tintColor)) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
                }
            }
            
            // Check text color for buttons and text views
            if (view is android.widget.TextView) {
                val textColor = view.currentTextColor
                // If text is black and parent/self has colored background, make it white
                if (textColor == blackColor) {
                    val parentBg = (view.parent as? View)?.background
                    val viewBg = view.background
                    val hasColoredBg = (parentBg is android.graphics.drawable.ColorDrawable && 
                        (purpleColors.contains((parentBg as android.graphics.drawable.ColorDrawable).color) || 
                        (parentBg as android.graphics.drawable.ColorDrawable).color == themeColor)) ||
                        (viewBg is android.graphics.drawable.ColorDrawable && 
                        (purpleColors.contains((viewBg as android.graphics.drawable.ColorDrawable).color) || 
                        (viewBg as android.graphics.drawable.ColorDrawable).color == themeColor))
                    if (hasColoredBg) {
                        view.setTextColor(android.graphics.Color.WHITE)
                    }
                }
            }
            
            // Check for ImageView (clock hands, icons)
            if (view is android.widget.ImageView) {
                val colorFilter = view.colorFilter
                if (colorFilter != null) {
                    view.setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN)
                }
            }
            
            // Recursively check children
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    replaceColorsInView(view.getChildAt(i), themeColor)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun showScheduledInfo() {
        scheduledDate?.let { date ->
            scheduledTime?.let { hour ->
                scheduledMinute?.let { minute ->
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = date
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)

                    val dateFormat = SimpleDateFormat("MMMM dd", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    val dateStr = dateFormat.format(calendar.time)
                    val timeStr = timeFormat.format(calendar.time)

                    // Show scheduled info container and update text
                    binding.layoutScheduledInfo.visibility = View.VISIBLE
                    binding.textScheduledInfo.text = "Scheduled for $dateStr $timeStr"
                    binding.buttonCancelScheduled.setOnClickListener {
                        cancelScheduling()
                    }
                }
            }
        }
    }

    private fun cancelScheduling() {
        scheduledDate = null
        scheduledTime = null
        scheduledMinute = null
        binding.layoutScheduledInfo.visibility = View.GONE
    }

    private fun scheduleMessage(messageText: String) {
        scheduledDate?.let { date ->
            scheduledTime?.let { hour ->
                scheduledMinute?.let { minute ->
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = date
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)

                    val scheduleTime = calendar.timeInMillis
                    if (scheduleTime <= System.currentTimeMillis()) {
                        Toast.makeText(this, "Please select a future date and time", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Get signature and append to message
                    val prefs = getSharedPreferences("signature", MODE_PRIVATE)
                    val signature = prefs.getString("signature_text", "") ?: ""
                    val messageBody = if (signature.isNotEmpty()) {
                        "$messageText\n$signature"
                    } else {
                        messageText
                    }

                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val intent = Intent(this, ScheduledMessageReceiver::class.java).apply {
                        putExtra("address", address)
                        putExtra("message", messageBody)
                        putExtra("thread_id", threadId)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        this,
                        System.currentTimeMillis().toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        scheduleTime,
                        pendingIntent
                    )

                    Toast.makeText(this, "Message scheduled successfully", Toast.LENGTH_SHORT).show()
                    binding.editTextMessage.text?.clear()
                    cancelScheduling()
                    finish()
                }
            }
        }
    }

    private fun makePhoneCall() {
        if (address.isEmpty()) {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initiatePhoneCall()
        } else {
            // Request permission
            requestCallPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
        }
    }

    private fun lookupContactName(phoneNumber: String): String? {
        // Normalize phone number for matching
        fun normalizePhoneNumber(phone: String): String {
            var normalized = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
            if (normalized.startsWith("+")) {
                normalized = normalized.substring(1)
            }
            if (normalized.length > 10) {
                if (normalized.startsWith("91") && normalized.length == 12) {
                    normalized = normalized.substring(2)
                } else if (normalized.startsWith("0") && normalized.length == 11) {
                    normalized = normalized.substring(1)
                }
            }
            if (normalized.length > 10) {
                normalized = normalized.takeLast(10)
            }
            return normalized
        }
        
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val variations = listOf(
            normalizedNumber,
            phoneNumber,
            if (normalizedNumber.length > 10) normalizedNumber.takeLast(10) else null
        ).filterNotNull().distinct()
        
        for (number in variations) {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) {
                            return name
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    private fun initiatePhoneCall() {
        val phoneNumber = address.trim()
        if (phoneNumber.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to make call", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

