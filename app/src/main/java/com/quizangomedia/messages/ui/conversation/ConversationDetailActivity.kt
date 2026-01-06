package com.quizangomedia.messages.ui.conversation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.quizangomedia.messages.observer.SmsContentObserver
import com.quizangomedia.messages.util.ThemeManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.BottomSheetAttachmentsBinding
import com.quizangomedia.messages.databinding.BottomSheetQuickNoteBinding
import com.quizangomedia.messages.databinding.ItemAttachmentImageBinding
import com.quizangomedia.messages.databinding.ItemAttachmentContactBinding
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.databinding.ActivityConversationDetailBinding
import com.quizangomedia.messages.receiver.ScheduledMessageReceiver
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
    
    // Attachment related
    private var selectedImageUri: Uri? = null
    private var selectedContactName: String? = null
    private var selectedContactNumber: String? = null
    private var currentPhotoPath: String? = null

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
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            val file = File(currentPhotoPath!!)
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            selectedImageUri = uri
            showImageAttachment(uri)
        }
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImageAttachment(it)
        }
    }
    
    private val contactSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "contactSelectionLauncher: resultCode=${result.resultCode}, data=${result.data}")
        if (result.resultCode == RESULT_OK) {
            val name = result.data?.getStringExtra("contact_name") ?: ""
            val number = result.data?.getStringExtra("phone_number") ?: ""
            Log.d(TAG, "Received contact data - name: '$name', number: '$number'")
            if (name.isNotEmpty() && number.isNotEmpty()) {
                selectedContactName = name
                selectedContactNumber = number
                showContactAttachment(name, number)
                updateSendButtonState()
            } else {
                Log.e(TAG, "Contact data is empty! name='$name', number='$number'")
            }
        } else {
            Log.w(TAG, "Contact selection cancelled or failed, resultCode=${result.resultCode}")
        }
    }
    
    private val scheduledContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val phoneNumber = result.data?.getStringExtra("phone_number") ?: ""
            val contactName = result.data?.getStringExtra("contact_name") ?: ""
            if (phoneNumber.isNotEmpty()) {
                // Navigate to conversation detail with scheduling
                val intent = Intent(this, ConversationDetailActivity::class.java)
                intent.putExtra("address", phoneNumber)
                intent.putExtra("thread_id", threadId)
                intent.putExtra("contact_name", contactName)
                intent.putExtra("is_scheduling", true)
                startActivity(intent)
            }
        }
    }
    
    private val editQuickResponseLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh quick messages if needed
            showQuickNoteBottomSheet()
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
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
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
            // Open conversation details screen
            val intent = Intent(this, ConversationDetailsActivity::class.java)
            intent.putExtra("thread_id", threadId)
            intent.putExtra("address", address)
            intent.putExtra("contact_name", contactName)
            startActivity(intent)
        }
        binding.imageAttachment.setOnClickListener {
            showAttachmentBottomSheet()
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
            onFailedMessageClick = { message ->
                showFailedMessageDialog(message)
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
                updateSendButtonState()
            }
        })
        
        // Initial state - check for attachments
        updateSendButtonState()
    }
    
    private fun updateSendButtonState() {
        val messageText = binding.editTextMessage.text.toString()
        val hasText = messageText.isNotEmpty()
        val hasImage = selectedImageUri != null
        val hasContact = selectedContactName != null && selectedContactNumber != null
        val enabled = hasText || hasImage || hasContact
        
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
            val hasImage = selectedImageUri != null
            val hasContact = selectedContactName != null && selectedContactNumber != null
            
            if (messageText.isNotEmpty() || hasImage || hasContact) {
                if (scheduledDate != null && scheduledTime != null && scheduledMinute != null) {
                    scheduleMessage(messageText, hasImage, hasContact)
                } else {
                    if (hasImage) {
                        viewModel.sendMMS(threadId, address, messageText, selectedImageUri!!)
                    } else if (hasContact) {
                        // Send contact as MMS with vCard file
                        val vCardFile = createContactVCardFile(selectedContactName!!, selectedContactNumber!!)
                        if (vCardFile != null) {
                            val vCardUri = FileProvider.getUriForFile(
                                this,
                                "${packageName}.fileprovider",
                                vCardFile
                            )
                            viewModel.sendMMSWithContact(threadId, address, messageText, vCardUri)
                        } else {
                            // Fallback to text vCard if file creation fails
                            val contactText = createContactVCard(selectedContactName!!, selectedContactNumber!!)
                            val finalText = if (messageText.isNotEmpty()) "$messageText\n$contactText" else contactText
                            viewModel.sendMessage(threadId, address, finalText)
                        }
                    } else {
                        viewModel.sendMessage(threadId, address, messageText)
                    }
                    
                    // Clear attachments and text
                    binding.editTextMessage.text?.clear()
                    selectedImageUri = null
                    selectedContactName = null
                    selectedContactNumber = null
                    binding.layoutAttachments.removeAllViews()
                    binding.scrollViewAttachments.visibility = View.GONE
                    updateSendButtonState()
                }
            }
        }
    }
    
    private fun createContactVCard(name: String, number: String): String {
        return "BEGIN:VCARD\nVERSION:3.0\nFN:$name\nTEL:$number\nEND:VCARD"
    }
    
    private fun createContactVCardFile(name: String, number: String): File? {
        return try {
            val vCardContent = createContactVCard(name, number)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val vCardFileName = "contact_${timeStamp}.vcf"
            val vCardFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), vCardFileName)
            
            // Ensure directory exists
            vCardFile.parentFile?.mkdirs()
            
            // Write vCard content to file
            vCardFile.writeText(vCardContent)
            Log.d(TAG, "Created vCard file: ${vCardFile.absolutePath}")
            vCardFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creating vCard file", e)
            e.printStackTrace()
            null
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
        
        // Observe error messages
        viewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                // Clear error message after showing
                viewModel.clearErrorMessage()
            }
        }
        
        // Load messages from device SMS for this contact
        viewModel.loadMessages(threadId, address)
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .build()

        datePicker.addOnPositiveButtonClickListener { selectedDate ->
            scheduledDate = selectedDate
            showTimePicker()
        }

        // Function to apply theme to picker - apply to both decor view and content view
        fun applyPickerTheme() {
            datePicker.dialog?.let { dialog ->
                // Apply to decor view (entire dialog window)
                dialog.window?.decorView?.let { decorView ->
                    ThemeManager.applyThemeToMaterialPicker(this, decorView)
                }
                
                // Also apply to content view (dialog content)
                dialog.findViewById<View>(android.R.id.content)?.let { contentView ->
                    ThemeManager.applyThemeToMaterialPicker(this, contentView)
                }
                
                // Also apply to dialog view directly
                dialog.window?.decorView?.rootView?.let { rootView ->
                    ThemeManager.applyThemeToMaterialPicker(this, rootView)
                }
            }
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
        
        // Apply theme after picker is shown with multiple attempts
        supportFragmentManager.executePendingTransactions()
        
        // Apply immediately after showing
        Handler(Looper.getMainLooper()).post {
            applyPickerTheme()
        }
        
        // Use ViewTreeObserver to catch views as they're added
        datePicker.dialog?.window?.decorView?.let { decorView ->
            var layoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
            layoutListener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    applyPickerTheme()
                    // Keep listening for layout changes
                }
            }
            decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        
        // Additional delayed attempts to ensure theme is applied
        val delays = listOf(50, 100, 200, 300, 500, 800, 1000, 1500, 2000)
        delays.forEach { delay ->
            Handler(Looper.getMainLooper()).postDelayed({
                applyPickerTheme()
            }, delay.toLong())
        }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
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

        // Function to apply theme to picker - apply to both decor view and content view
        fun applyPickerTheme() {
            timePicker.dialog?.let { dialog ->
                // Apply to decor view (entire dialog window)
                dialog.window?.decorView?.let { decorView ->
                    ThemeManager.applyThemeToMaterialPicker(this, decorView)
                }
                
                // Also apply to content view (dialog content)
                dialog.findViewById<View>(android.R.id.content)?.let { contentView ->
                    ThemeManager.applyThemeToMaterialPicker(this, contentView)
                }
                
                // Also apply to dialog view directly
                dialog.window?.decorView?.rootView?.let { rootView ->
                    ThemeManager.applyThemeToMaterialPicker(this, rootView)
                }
            }
        }

        timePicker.show(supportFragmentManager, "TIME_PICKER")
        
        // Apply theme after picker is shown with multiple attempts
        supportFragmentManager.executePendingTransactions()
        
        // Apply immediately after showing
        Handler(Looper.getMainLooper()).post {
            applyPickerTheme()
        }
        
        // Use ViewTreeObserver to catch views as they're added and when user interacts
        timePicker.dialog?.window?.decorView?.let { decorView ->
            var layoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
            layoutListener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    applyPickerTheme()
                    // Keep listening for layout changes (e.g., when user changes hour/minute)
                }
            }
            decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        
        // Additional delayed attempts to ensure theme is applied
        val delays = listOf(50, 100, 200, 300, 500, 800, 1000, 1500, 2000)
        delays.forEach { delay ->
            Handler(Looper.getMainLooper()).postDelayed({
                applyPickerTheme()
            }, delay.toLong())
        }
        
        // Continuously reapply theme while dialog is visible (catches user interactions)
        val themeRunnable = object : Runnable {
            override fun run() {
                if (timePicker.dialog?.isShowing == true) {
                    applyPickerTheme()
                    // Reapply every 200ms while dialog is visible
                    Handler(Looper.getMainLooper()).postDelayed(this, 200)
                }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(themeRunnable, 100)
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

    private fun scheduleMessage(messageText: String, hasImage: Boolean = false, hasContact: Boolean = false) {
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
                    var messageBody = if (signature.isNotEmpty() && messageText.isNotEmpty()) {
                        "$messageText\n$signature"
                    } else if (signature.isNotEmpty()) {
                        signature
                    } else {
                        messageText
                    }
                    
                    // Add contact if present
                    if (hasContact && selectedContactName != null && selectedContactNumber != null) {
                        val contactText = createContactVCard(selectedContactName!!, selectedContactNumber!!)
                        messageBody = if (messageBody.isNotEmpty()) "$messageBody\n$contactText" else contactText
                    }

                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val intent = Intent(this, ScheduledMessageReceiver::class.java).apply {
                        putExtra("address", address)
                        putExtra("message", messageBody)
                        putExtra("thread_id", threadId)
                        if (hasImage && selectedImageUri != null) {
                            putExtra("image_uri", selectedImageUri.toString())
                        }
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
                    selectedImageUri = null
                    selectedContactName = null
                    selectedContactNumber = null
                    binding.layoutAttachments.removeAllViews()
                    binding.scrollViewAttachments.visibility = View.GONE
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
    
    // ================= ATTACHMENT FUNCTIONALITY =================
    
    private var attachmentPopupWindow: android.widget.PopupWindow? = null
    
    private fun showAttachmentBottomSheet() {
        // Dismiss existing popup if any
        attachmentPopupWindow?.dismiss()
        
        val popupView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_attachments, null)
        
        // Apply theme
        ThemeManager.applyTheme(this, popupView)
        
        // Create PopupWindow with full screen width (with small margins for rounded corners)
        val screenWidth = resources.displayMetrics.widthPixels
        val horizontalMargin = (8 * resources.displayMetrics.density).toInt() // 8dp margin on each side
        val popupWidth = screenWidth - (horizontalMargin * 2)
        
        val popupWindow = android.widget.PopupWindow(
            popupView,
            popupWidth,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        
        attachmentPopupWindow = popupWindow
        
        // Set click listeners
        popupView.findViewById<View>(R.id.optionCamera)?.setOnClickListener {
            popupWindow.dismiss()
            openCamera()
        }
        
        popupView.findViewById<View>(R.id.optionGallery)?.setOnClickListener {
            popupWindow.dismiss()
            openGallery()
        }
        
        popupView.findViewById<View>(R.id.optionScheduled)?.setOnClickListener {
            popupWindow.dismiss()
            openScheduled()
        }
        
        popupView.findViewById<View>(R.id.optionContacts)?.setOnClickListener {
            popupWindow.dismiss()
            openContacts()
        }
        
        popupView.findViewById<View>(R.id.optionQuickNote)?.setOnClickListener {
            popupWindow.dismiss()
            showQuickNoteBottomSheet()
        }
        
        // Set dismiss listener
        popupWindow.setOnDismissListener {
            attachmentPopupWindow = null
        }
        
        // Calculate position above input area
        binding.layoutInputRow.post {
            // Measure popup to get actual dimensions
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            
            val inputRowLocation = IntArray(2)
            binding.layoutInputRow.getLocationInWindow(inputRowLocation)
            val inputRowY = inputRowLocation[1]
            
            val marginAboveInput = (8 * resources.displayMetrics.density).toInt() // 8dp margin
            
            // Center popup horizontally with margin
            val popupX = horizontalMargin
            
            // Position above input row
            val popupY = inputRowY - popupView.measuredHeight - marginAboveInput
            
            Log.d(TAG, "Attachment popup positioning - x: $popupX, y: $popupY, width: $popupWidth")
            
            // Show popup
            popupWindow.showAtLocation(
                binding.root,
                android.view.Gravity.NO_GRAVITY,
                popupX,
                popupY
            )
        }
    }
    
    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            val photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            currentPhotoPath = photoFile.absolutePath
            cameraLauncher.launch(photoURI)
        } catch (e: IOException) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }
    
    private fun openScheduled() {
        // Directly show date/time picker for current conversation
        showDatePicker()
    }
    
    private fun openContacts() {
        val intent = Intent(this, com.quizangomedia.messages.ui.blocking.overlay.SingleContactSelectionActivity::class.java)
        contactSelectionLauncher.launch(intent)
    }
    
    private fun showQuickNoteBottomSheet() {
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_quick_note, null)
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(bottomSheetView)
        
        // Apply theme
        ThemeManager.applyTheme(this, bottomSheetView)
        
        // Set behavior to load higher - use 70% of screen height
        val screenHeight = resources.displayMetrics.heightPixels
        bottomSheet.behavior.peekHeight = (screenHeight * 0.7).toInt()
        bottomSheet.behavior.isDraggable = true
        bottomSheet.behavior.skipCollapsed = false
        bottomSheet.behavior.maxHeight = screenHeight
        bottomSheet.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val recyclerView = bottomSheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewQuickMessages)
        val editQuickResponse = bottomSheetView.findViewById<TextView>(R.id.textEditQuickResponse)
        
        // Load quick messages
        val quickMessages = loadQuickMessages()
        Log.d(TAG, "showQuickNoteBottomSheet: Loaded ${quickMessages.size} quick messages")
        
        if (quickMessages.isEmpty()) {
            Log.w(TAG, "No quick messages found, using defaults")
        }
        
        val adapter = QuickMessageAdapter(quickMessages) { message ->
            bottomSheet.dismiss()
            binding.editTextMessage.setText(message)
            binding.editTextMessage.setSelection(message.length)
        }
        
        recyclerView?.let { rv ->
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = adapter
            rv.visibility = View.VISIBLE
            rv.setHasFixedSize(false)
            Log.d(TAG, "RecyclerView setup complete with ${adapter.itemCount} items, messages: $quickMessages")
            
            // Ensure RecyclerView is visible and has content
            if (adapter.itemCount == 0) {
                Log.w(TAG, "Adapter has 0 items, but messages list has ${quickMessages.size} items")
            }
        } ?: run {
            Log.e(TAG, "RecyclerView not found in bottom sheet!")
        }
        
        editQuickResponse?.setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(this, EditQuickResponseActivity::class.java)
            editQuickResponseLauncher.launch(intent)
        }
        
        bottomSheet.show()
        
        // Force layout update after showing
        bottomSheetView.post {
            recyclerView?.let { rv ->
                rv.requestLayout()
                rv.invalidate()
                adapter.notifyDataSetChanged()
                Log.d(TAG, "RecyclerView layout updated, item count: ${adapter.itemCount}, visible: ${rv.visibility}")
            }
        }
    }
    
    private fun showImageAttachment(uri: Uri) {
        binding.scrollViewAttachments.visibility = View.VISIBLE
        val attachmentView = LayoutInflater.from(this).inflate(R.layout.item_attachment_image, binding.layoutAttachments, false)
        val imageView = attachmentView.findViewById<ImageView>(R.id.imageAttachment)
        val removeButton = attachmentView.findViewById<ImageView>(R.id.buttonRemove)
        
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Create circular bitmap
            val circularBitmap = createCircularBitmap(bitmap)
            imageView.setImageBitmap(circularBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image: ${e.message}")
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            return
        }
        
        removeButton.setOnClickListener {
            binding.layoutAttachments.removeView(attachmentView)
            selectedImageUri = null
            if (binding.layoutAttachments.childCount == 0) {
                binding.scrollViewAttachments.visibility = View.GONE
            }
            updateSendButtonState()
        }
        
        binding.layoutAttachments.addView(attachmentView)
        updateSendButtonState()
    }
    
    private fun createCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = (48 * resources.displayMetrics.density).toInt()
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true
        
        val rect = android.graphics.Rect(0, 0, size, size)
        val radius = size / 2f
        
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
        canvas.drawBitmap(scaledBitmap, rect, rect, paint)
        
        return output
    }
    
    private fun showContactAttachment(name: String, number: String) {
        binding.scrollViewAttachments.visibility = View.VISIBLE
        val attachmentView = LayoutInflater.from(this).inflate(R.layout.item_attachment_contact, binding.layoutAttachments, false)
        
        Log.d(TAG, "showContactAttachment: name='$name', number='$number'")
        
        // Find views immediately - they should be findable even before adding to parent
        val imageContact = attachmentView.findViewById<ImageView>(R.id.imageContact)
        val textName = attachmentView.findViewById<TextView>(R.id.textContactName)
        val textNumber = attachmentView.findViewById<TextView>(R.id.textContactNumber)
        val textContactCard = attachmentView.findViewById<TextView>(R.id.textContactCard)
        val removeButton = attachmentView.findViewById<ImageView>(R.id.buttonRemove)
        val layoutContactInfo = attachmentView.findViewById<LinearLayout>(R.id.layoutContactInfo)
        
        Log.d(TAG, "Finding views - textName: ${textName != null}, textNumber: ${textNumber != null}, imageContact: ${imageContact != null}, layoutContactInfo: ${layoutContactInfo != null}")
        
        // Ensure parent container is visible
        layoutContactInfo?.visibility = View.VISIBLE
        
        // Set contact details immediately
        if (textName != null) {
            textName.text = name
            textName.visibility = View.VISIBLE
            textName.alpha = 1.0f
            textName.setTextColor(ContextCompat.getColor(this, R.color.black))
            Log.d(TAG, "Set contact name: '$name', view now shows: '${textName.text}', visibility: ${textName.visibility}, alpha: ${textName.alpha}, textColor: ${textName.currentTextColor}")
        } else {
            Log.e(TAG, "textContactName view is null!")
        }
        
        if (textNumber != null) {
            textNumber.text = number
            textNumber.visibility = View.VISIBLE
            textNumber.alpha = 1.0f
            textNumber.setTextColor(ContextCompat.getColor(this, R.color.gray_dark))
            Log.d(TAG, "Set contact number: '$number', view now shows: '${textNumber.text}', visibility: ${textNumber.visibility}, alpha: ${textNumber.alpha}, textColor: ${textNumber.currentTextColor}")
        } else {
            Log.e(TAG, "textContactNumber view is null!")
        }
        
        if (textContactCard != null) {
            textContactCard.text = "• Contact card"
            textContactCard.visibility = View.VISIBLE
            textContactCard.alpha = 1.0f
        }
        
        // Ensure attachment view itself is visible
        attachmentView.visibility = View.VISIBLE
        attachmentView.alpha = 1.0f
        
        Log.d(TAG, "Contact details set - name: '${textName?.text}', number: '${textNumber?.text}', attachmentView visibility: ${attachmentView.visibility}")
        
        // Add view to parent
        binding.layoutAttachments.addView(attachmentView)
        
        // Force layout update after adding to parent
        attachmentView.post {
            attachmentView.requestLayout()
            layoutContactInfo?.requestLayout()
            textName?.requestLayout()
            textNumber?.requestLayout()
            
            // Log dimensions after layout
            Log.d(TAG, "After layout - layoutContactInfo width: ${layoutContactInfo?.width}, textName width: ${textName?.width}, textNumber width: ${textNumber?.width}")
        }
        
        // Set up remove button listener
        removeButton?.setOnClickListener {
            binding.layoutAttachments.removeView(attachmentView)
            selectedContactName = null
            selectedContactNumber = null
            if (binding.layoutAttachments.childCount == 0) {
                binding.scrollViewAttachments.visibility = View.GONE
            }
            updateSendButtonState()
        }
        
        // Try to load contact photo in background
        attachmentView.post {
            try {
                val contactUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
                val selectionArgs = arrayOf(number)
                
                contentResolver.query(contactUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                        val lookupUri = ContactsContract.Contacts.getLookupUri(contactId, null)
                        val photoStream = lookupUri?.let { 
                            ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, it)
                        }
                        
                        if (photoStream != null) {
                            val bitmap = BitmapFactory.decodeStream(photoStream)
                            photoStream.close()
                            if (bitmap != null) {
                                // Create circular bitmap
                                val circularBitmap = createCircularBitmap(bitmap)
                                imageContact?.setImageBitmap(circularBitmap)
                                imageContact?.background = null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contact photo: ${e.message}")
                e.printStackTrace()
            }
            
            // Force layout update after setting all data
            attachmentView.requestLayout()
            attachmentView.invalidate()
            textName?.requestLayout()
            textNumber?.requestLayout()
            
            // Verify text is still set after layout and check visibility
            Log.d(TAG, "After layout - name: '${textName?.text}', number: '${textNumber?.text}'")
            Log.d(TAG, "After layout - textName visibility: ${textName?.visibility}, alpha: ${textName?.alpha}, width: ${textName?.width}, height: ${textName?.height}")
            Log.d(TAG, "After layout - textNumber visibility: ${textNumber?.visibility}, alpha: ${textNumber?.alpha}, width: ${textNumber?.width}, height: ${textNumber?.height}")
            Log.d(TAG, "After layout - layoutContactInfo visibility: ${layoutContactInfo?.visibility}, width: ${layoutContactInfo?.width}, height: ${layoutContactInfo?.height}")
            Log.d(TAG, "After layout - attachmentView visibility: ${attachmentView.visibility}, width: ${attachmentView.width}, height: ${attachmentView.height}")
            
            // Double-check text is set and visible
            textName?.let {
                if (it.text != name) {
                    Log.w(TAG, "Text was changed! Setting it again. Expected: '$name', Got: '${it.text}'")
                    it.text = name
                    it.visibility = View.VISIBLE
                    it.alpha = 1.0f
                }
            }
            
            textNumber?.let {
                if (it.text != number) {
                    Log.w(TAG, "Number was changed! Setting it again. Expected: '$number', Got: '${it.text}'")
                    it.text = number
                    it.visibility = View.VISIBLE
                    it.alpha = 1.0f
                }
            }
        }
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    
    private fun loadQuickMessages(): List<String> {
        val prefs = getSharedPreferences("quick_messages", MODE_PRIVATE)
        val messagesJson = prefs.getString("quick_messages_list", null)
        return if (messagesJson != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(messagesJson, type) ?: getDefaultQuickMessages()
        } else {
            getDefaultQuickMessages()
        }
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
    
    private fun showFailedMessageDialog(message: Message) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_failed_message_preview, null)
        val imagePreview: ImageView = dialogView.findViewById(R.id.imagePreview)
        val layoutContactPreview: LinearLayout = dialogView.findViewById(R.id.layoutContactPreview)
        val textContactName: TextView = dialogView.findViewById(R.id.textContactName)
        val textContactNumber: TextView = dialogView.findViewById(R.id.textContactNumber)
        val buttonResend: MaterialButton = dialogView.findViewById(R.id.buttonResend)
        val buttonDelete: MaterialButton = dialogView.findViewById(R.id.buttonDelete)
        
        // Hide both previews initially
        imagePreview.visibility = View.GONE
        layoutContactPreview.visibility = View.GONE
        
        // Show appropriate preview based on attachment type
        val attachmentPath = message.attachmentPath
        val mimeType = message.mimeType
        
        if (!attachmentPath.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(attachmentPath)
                
                // Check if it's an image
                if (mimeType?.startsWith("image/") == true || 
                    attachmentPath.contains(".jpg", ignoreCase = true) ||
                    attachmentPath.contains(".jpeg", ignoreCase = true) ||
                    attachmentPath.contains(".png", ignoreCase = true) ||
                    attachmentPath.contains(".gif", ignoreCase = true)) {
                    // Show image preview
                    imagePreview.visibility = View.VISIBLE
                    try {
                        Picasso.get()
                            .load(uri)
                            .placeholder(R.drawable.ic_gallery)
                            .error(R.drawable.ic_gallery)
                            .into(imagePreview)
                    } catch (e: Exception) {
                        // Fallback: try to load from file
                        try {
                            val file = File(uri.path ?: "")
                            if (file.exists()) {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                imagePreview.setImageBitmap(bitmap)
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error loading image preview", ex)
                        }
                    }
                } 
                // Check if it's a contact card
                else if (mimeType == "text/x-vCard" || 
                         attachmentPath.contains(".vcf", ignoreCase = true)) {
                    // Show contact card preview
                    layoutContactPreview.visibility = View.VISIBLE
                    // Parse contact info from message body
                    val body = message.body
                    if (body.contains("BEGIN:VCARD")) {
                        val nameMatch = Regex("FN:([^\\r\\n]+)").find(body)
                        val telMatch = Regex("TEL:([^\\r\\n]+)").find(body)
                        
                        val name = nameMatch?.groupValues?.get(1) ?: "Contact"
                        val number = telMatch?.groupValues?.get(1) ?: ""
                        
                        textContactName.text = name
                        textContactNumber.text = number
                    } else {
                        textContactName.text = "Contact card"
                        textContactNumber.text = ""
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing preview", e)
            }
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Failed Message")
            .create()
        
        buttonResend.setOnClickListener {
            dialog.dismiss()
            resendFailedMessage(message)
        }
        
        buttonDelete.setOnClickListener {
            dialog.dismiss()
            deleteFailedMessage(message)
        }
        
        dialog.show()
    }
    
    private fun resendFailedMessage(message: Message) {
        val attachmentPath = message.attachmentPath
        val mimeType = message.mimeType
        
        if (!attachmentPath.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(attachmentPath)
                
                // Check if it's an image
                if (mimeType?.startsWith("image/") == true || 
                    attachmentPath.contains(".jpg", ignoreCase = true) ||
                    attachmentPath.contains(".jpeg", ignoreCase = true) ||
                    attachmentPath.contains(".png", ignoreCase = true) ||
                    attachmentPath.contains(".gif", ignoreCase = true)) {
                    // Resend as image MMS
                    viewModel.sendMMS(threadId, address, message.body, uri)
                } 
                // Check if it's a contact card
                else if (mimeType == "text/x-vCard" || 
                         attachmentPath.contains(".vcf", ignoreCase = true)) {
                    // Resend as contact card MMS
                    viewModel.sendMMSWithContact(threadId, address, message.body, uri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resending message", e)
                Toast.makeText(this, "Error resending message", Toast.LENGTH_SHORT).show()
            }
        } else {
            // No attachment, resend as regular SMS
            viewModel.sendMessage(threadId, address, message.body)
        }
    }
    
    private fun deleteFailedMessage(message: Message) {
        viewModel.deleteMessage(message.id)
        Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
    }
}

