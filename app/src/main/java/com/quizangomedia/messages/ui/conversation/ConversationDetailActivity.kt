package com.quizangomedia.messages.ui.conversation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
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
        contactName = intent.getStringExtra("contact_name") ?: address
        isScheduling = intent.getBooleanExtra("is_scheduling", false)
        
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
    }
    
    private fun setupToolbar() {
        binding.textContactName.text = contactName.ifEmpty { address }
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
        binding.editTextMessage.highlightColor = 0x1A0C56CF.toInt() // #0C56CF with 10% opacity
        
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
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages)
            loadStarredMessages() // Refresh starred status when messages load
            if (messages.isNotEmpty()) {
                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
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

        datePicker.show(supportFragmentManager, "DATE_PICKER")
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

        timePicker.show(supportFragmentManager, "TIME_PICKER")
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

