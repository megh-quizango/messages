package com.text.messages.sms.messanger.ui.caller

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.material.button.MaterialButton
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.util.AdLoadingShimmerHelper
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.AfterCallAdPreloader
import com.text.messages.sms.messanger.util.AfterCallNotificationHelper
import com.text.messages.sms.messanger.util.CallAfterLauncher
import com.text.messages.sms.messanger.util.ConversationCache
import com.text.messages.sms.messanger.util.RemoteConfigHelper
import com.text.messages.sms.messanger.util.ThemeManager
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CallAfterActivity : BaseActivity() {

    companion object {
        private const val TAG = "CallAfterActivity"
        private const val DEBOUNCE_DELAY_MS = 500L
        private const val PACKAGE_WHATSAPP = "com.whatsapp"
        private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val PACKAGE_GMAIL = "com.google.android.gm"
        private const val PACKAGE_INSTAGRAM = "com.instagram.android"
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
    }

    private enum class AfterCallTab {
        MESSAGES,
        QUICK_MESSAGES,
        REMINDERS,
        ACTIONS
    }

    private val viewModel: CallAfterViewModel by viewModels()

    private lateinit var imageAvatar: ImageView
    private lateinit var textAvatarLetter: TextView
    private lateinit var textContactName: TextView
    private lateinit var textCallTime: TextView
    private lateinit var textCallStatus: TextView
    private lateinit var buttonCall: ImageButton

    private lateinit var tabMessages: LinearLayout
    private lateinit var tabQuickMessages: LinearLayout
    private lateinit var tabReminders: LinearLayout
    private lateinit var tabActions: LinearLayout
    private lateinit var imageTabMessages: ImageView
    private lateinit var imageTabQuickMessages: ImageView
    private lateinit var imageTabReminders: ImageView
    private lateinit var imageTabActions: ImageView
    private lateinit var indicatorTabMessages: View
    private lateinit var indicatorTabQuickMessages: View
    private lateinit var indicatorTabReminders: View
    private lateinit var indicatorTabActions: View

    private lateinit var messagesContent: FrameLayout
    private lateinit var recyclerRecentMessages: RecyclerView
    private lateinit var textMessagesEmpty: TextView
    private lateinit var recyclerQuickResponses: RecyclerView
    private lateinit var remindersContent: View
    private lateinit var reminderEmptyState: LinearLayout
    private lateinit var reminderEditorState: LinearLayout
    private lateinit var reminderSavedState: LinearLayout
    private lateinit var buttonAddReminder: MaterialButton
    private lateinit var editReminderTitle: EditText
    private lateinit var pickerReminderDay: NumberPicker
    private lateinit var pickerReminderHour: NumberPicker
    private lateinit var pickerReminderMinute: NumberPicker
    private lateinit var textReminderTimeValue: TextView
    private lateinit var reminderColorRow: LinearLayout
    private lateinit var buttonCancelReminder: MaterialButton
    private lateinit var buttonSaveReminder: MaterialButton
    private lateinit var savedReminderColorDot: View
    private lateinit var textSavedReminderTitle: TextView
    private lateinit var textSavedReminderTime: TextView
    private lateinit var textSavedReminderDate: TextView
    private lateinit var buttonEditReminder: ImageButton
    private lateinit var buttonDeleteReminder: ImageButton
    private lateinit var recyclerQuickActions: RecyclerView

    private lateinit var nativeAdContainer: FrameLayout
    private lateinit var nativeAdView: NativeAdView
    private lateinit var adLoadingPlaceholder: LinearLayout
    private var adaptiveBannerView: AdView? = null

    private lateinit var quickResponseAdapter: QuickResponseAdapter
    private lateinit var recentConversationAdapter: AfterCallConversationAdapter
    private lateinit var quickActionAdapter: AfterCallActionAdapter

    private var currentNativeAd: NativeAd? = null
    private var selectedTab: AfterCallTab = AfterCallTab.MESSAGES
    private var lastClickTime: Long = 0

    private var callerNumber: String? = null
    private var callType: String = "completed"
    private var callEndTime: Long = 0
    private var callStartTime: Long = 0
    private var isIncoming: Boolean = false
    private val reminderColors = listOf(
        Color.parseColor("#36B5E8"),
        Color.parseColor("#AF49C9"),
        Color.parseColor("#5F6CC4"),
        Color.parseColor("#F25050"),
        Color.parseColor("#7E55C8"),
        Color.parseColor("#EA3D7C"),
        Color.parseColor("#42A5F5")
    )
    private var selectedReminderColor: Int = reminderColors[3]
    private var savedReminder: ReminderUiState? = null

    private data class ReminderUiState(
        val title: String,
        val dayOffset: Int,
        val hour: Int,
        val minute: Int,
        val color: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_after)

        ThemeManager.applyTheme(this, findViewById(R.id.rootLayout))
        ThemeManager.setupNavigationBar(this)

        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (intent.getBooleanExtra(CallAfterLauncher.EXTRA_FROM_CALL_END, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            AfterCallNotificationHelper.cancelPostCallNotification(this)
        }

        extractIntentExtras()
        initViews()
        setupUI()
        loadContactInfo()
        observeViewModel()
        loadRecentConversations()
        loadNativeAd()
    }

    private fun extractIntentExtras() {
        callerNumber =
            intent.getStringExtra(CallAfterLauncher.EXTRA_CALLER_NUMBER)
                ?: intent.getStringExtra("CALLER_NUMBER")
                ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                ?: intent.data?.schemeSpecificPart

        callerNumber = callerNumber
            ?.replace(Regex("[^\\d+]"), "")
            ?.trim()

        if (callerNumber.isNullOrEmpty()) {
            callerNumber = getLastCallNumber()
        }

        callType = intent.getStringExtra(CallAfterLauncher.EXTRA_CALL_TYPE)
            ?: intent.getStringExtra("CALL_TYPE")
            ?: "completed"
        callEndTime = intent.getLongExtra(
            CallAfterLauncher.EXTRA_CALL_END_TIME,
            intent.getLongExtra("CALL_END_TIME", System.currentTimeMillis())
        )
        callStartTime = intent.getLongExtra(
            CallAfterLauncher.EXTRA_CALL_START_TIME,
            intent.getLongExtra("CALL_START_TIME", callEndTime)
        )
        isIncoming = intent.getBooleanExtra(
            CallAfterLauncher.EXTRA_IS_INCOMING,
            intent.getBooleanExtra("IS_INCOMING", false)
        )

        val duration = if (callEndTime > callStartTime) callEndTime - callStartTime else 0L
        viewModel.setCallInfo(callerNumber, callType, duration, callEndTime, isIncoming)
    }

    private fun getLastCallNumber(): String? {
        return try {
            contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.NUMBER),
                null,
                null,
                android.provider.CallLog.Calls.DATE + " DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CallLog", e)
            null
        }
    }

    private fun initViews() {
        imageAvatar = findViewById(R.id.imageAvatar)
        textAvatarLetter = findViewById(R.id.textAvatarLetter)
        textContactName = findViewById(R.id.textContactName)
        textCallTime = findViewById(R.id.textCallTime)
        textCallStatus = findViewById(R.id.textCallStatus)
        buttonCall = findViewById(R.id.buttonCall)
        preserveHeaderDialerIcon()

        tabMessages = findViewById(R.id.tabMessages)
        tabQuickMessages = findViewById(R.id.tabQuickMessages)
        tabReminders = findViewById(R.id.tabReminders)
        tabActions = findViewById(R.id.tabActions)
        imageTabMessages = findViewById(R.id.imageTabMessages)
        imageTabQuickMessages = findViewById(R.id.imageTabQuickMessages)
        imageTabReminders = findViewById(R.id.imageTabReminders)
        imageTabActions = findViewById(R.id.imageTabActions)
        indicatorTabMessages = findViewById(R.id.indicatorTabMessages)
        indicatorTabQuickMessages = findViewById(R.id.indicatorTabQuickMessages)
        indicatorTabReminders = findViewById(R.id.indicatorTabReminders)
        indicatorTabActions = findViewById(R.id.indicatorTabActions)

        messagesContent = findViewById(R.id.messagesContent)
        recyclerRecentMessages = findViewById(R.id.recyclerRecentMessages)
        textMessagesEmpty = findViewById(R.id.textMessagesEmpty)
        recyclerQuickResponses = findViewById(R.id.recyclerQuickResponses)
        remindersContent = findViewById(R.id.remindersContent)
        reminderEmptyState = findViewById(R.id.reminderEmptyState)
        reminderEditorState = findViewById(R.id.reminderEditorState)
        reminderSavedState = findViewById(R.id.reminderSavedState)
        buttonAddReminder = findViewById(R.id.buttonAddReminder)
        editReminderTitle = findViewById(R.id.editReminderTitle)
        pickerReminderDay = findViewById(R.id.pickerReminderDay)
        pickerReminderHour = findViewById(R.id.pickerReminderHour)
        pickerReminderMinute = findViewById(R.id.pickerReminderMinute)
        textReminderTimeValue = findViewById(R.id.textReminderTimeValue)
        reminderColorRow = findViewById(R.id.reminderColorRow)
        buttonCancelReminder = findViewById(R.id.buttonCancelReminder)
        buttonSaveReminder = findViewById(R.id.buttonSaveReminder)
        savedReminderColorDot = findViewById(R.id.savedReminderColorDot)
        textSavedReminderTitle = findViewById(R.id.textSavedReminderTitle)
        textSavedReminderTime = findViewById(R.id.textSavedReminderTime)
        textSavedReminderDate = findViewById(R.id.textSavedReminderDate)
        buttonEditReminder = findViewById(R.id.buttonEditReminder)
        buttonDeleteReminder = findViewById(R.id.buttonDeleteReminder)
        recyclerQuickActions = findViewById(R.id.recyclerQuickActions)

        nativeAdContainer = findViewById(R.id.nativeAdContainer)
        nativeAdView = findViewById(R.id.nativeAdView)
        adLoadingPlaceholder = findViewById(R.id.adLoadingPlaceholder)
        adLoadingPlaceholder.visibility = View.GONE
        nativeAdView.visibility = View.GONE
        AdLoadingShimmerHelper.showNativeLoading(
            nativeAdContainer,
            nativeAdView,
            R.layout.layout_native_ad_shimmer_full_bleed
        )
    }

    private fun setupUI() {
        displayCallInfo()
        setupTabs()
        setupRecentMessages()
        setupQuickResponses()
        setupReminderUi()
        setupQuickActions()
        setupHeaderActions()
        buttonAddReminder.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            showReminderEditor()
        }
        selectTab(AfterCallTab.MESSAGES)
    }

    private fun displayCallInfo() {
        textCallTime.text = SimpleDateFormat("EEE, hh:mm a", Locale.getDefault()).format(Date(callEndTime))

        val durationText = viewModel.getCallDuration().takeIf { it > 0 }?.let { formatDuration(it) }
        textCallStatus.text = when (callType) {
            "missed" -> getString(R.string.missed_call)
            "no_answer" -> getString(R.string.no_answer)
            else -> {
                val direction = if (isIncoming) {
                    getString(R.string.call_type_incoming)
                } else {
                    getString(R.string.call_type_outgoing)
                }
                if (durationText != null) "$direction $durationText" else direction
            }
        }

        textContactName.text = getString(R.string.unknown_number)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun setupTabs() {
        tabMessages.setOnClickListener { if (isDebounced()) selectTab(AfterCallTab.MESSAGES) }
        tabQuickMessages.setOnClickListener { if (isDebounced()) selectTab(AfterCallTab.QUICK_MESSAGES) }
        tabReminders.setOnClickListener { if (isDebounced()) selectTab(AfterCallTab.REMINDERS) }
        tabActions.setOnClickListener { if (isDebounced()) selectTab(AfterCallTab.ACTIONS) }
    }

    private fun selectTab(tab: AfterCallTab) {
        selectedTab = tab
        messagesContent.visibility = if (tab == AfterCallTab.MESSAGES) View.VISIBLE else View.GONE
        recyclerQuickResponses.visibility = if (tab == AfterCallTab.QUICK_MESSAGES) View.VISIBLE else View.GONE
        remindersContent.visibility = if (tab == AfterCallTab.REMINDERS) View.VISIBLE else View.GONE
        recyclerQuickActions.visibility = if (tab == AfterCallTab.ACTIONS) View.VISIBLE else View.GONE

        updateTabState(
            imageTabMessages,
            indicatorTabMessages,
            tab == AfterCallTab.MESSAGES
        )
        updateTabState(
            imageTabQuickMessages,
            indicatorTabQuickMessages,
            tab == AfterCallTab.QUICK_MESSAGES
        )
        updateTabState(
            imageTabReminders,
            indicatorTabReminders,
            tab == AfterCallTab.REMINDERS
        )
        updateTabState(
            imageTabActions,
            indicatorTabActions,
            tab == AfterCallTab.ACTIONS
        )
    }

    private fun updateTabState(iconView: ImageView, indicator: View, isSelected: Boolean) {
        iconView.clearColorFilter()
        indicator.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        iconView.alpha = if (isSelected) 1f else 0.62f
    }

    private fun setupRecentMessages() {
        recentConversationAdapter = AfterCallConversationAdapter { conversation ->
            if (!isDebounced()) return@AfterCallConversationAdapter
            openConversationDetail(conversation.threadId, conversation.address)
        }

        recyclerRecentMessages.apply {
            layoutManager = LinearLayoutManager(this@CallAfterActivity)
            adapter = recentConversationAdapter
            itemAnimator = null
        }
    }

    private fun setupQuickResponses() {
        quickResponseAdapter = QuickResponseAdapter { response ->
            if (!isDebounced()) return@QuickResponseAdapter
            if (response.isCustom) {
                showCustomMessageDialog()
            } else {
                sendQuickMessage(response.text)
            }
        }

        recyclerQuickResponses.apply {
            layoutManager = object : LinearLayoutManager(this@CallAfterActivity) {
                override fun canScrollVertically(): Boolean = false
            }
            adapter = quickResponseAdapter
            itemAnimator = null
        }
    }

    private fun setupQuickActions() {
        quickActionAdapter = AfterCallActionAdapter { action ->
            if (!isDebounced()) return@AfterCallActionAdapter
            handleQuickAction(action)
        }

        recyclerQuickActions.apply {
            layoutManager = object : GridLayoutManager(this@CallAfterActivity, 3) {
                override fun canScrollVertically(): Boolean = true
            }
            adapter = quickActionAdapter
            itemAnimator = null
        }

        quickActionAdapter.submitList(
            listOf(
                AfterCallActionItem("add_contact", R.drawable.ic_person_add, getString(R.string.after_call_action_add_contact)),
                AfterCallActionItem("send_sms", R.drawable.ic_chat_bubble, getString(R.string.after_call_action_send_sms)),
                AfterCallActionItem("whatsapp", R.drawable.ic_send, getString(R.string.after_call_action_whatsapp)),
                AfterCallActionItem("set_alarm", R.drawable.ic_after_call_alarm, getString(R.string.after_call_action_set_alarm)),
                AfterCallActionItem("reminder", R.drawable.ic_after_call_bell, getString(R.string.after_call_action_reminder)),
                AfterCallActionItem("send_email", R.drawable.ic_after_call_mail, getString(R.string.after_call_action_send_email)),
                AfterCallActionItem("instagram", R.drawable.ic_camera, getString(R.string.after_call_action_instagram)),
                AfterCallActionItem("youtube", R.drawable.ic_after_call_play, getString(R.string.after_call_action_youtube)),
                AfterCallActionItem("web", R.drawable.ic_after_call_globe, getString(R.string.after_call_action_web))
            )
        )
    }

    private fun setupHeaderActions() {
        buttonCall.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            makeCall()
        }
    }

    private fun sendQuickMessage(message: String) {
        if (callerNumber.isNullOrBlank()) {
            navigateToMainActivity()
            return
        }
        viewModel.updateMessageText(message)
        viewModel.sendMessage()
    }

    private fun showCustomMessageDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.after_call_custom_message_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setPadding(48, 36, 48, 12)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.after_call_custom_message_title)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.send) { _, _ ->
                val customMessage = input.text?.toString()?.trim().orEmpty()
                if (customMessage.isNotEmpty()) {
                    sendQuickMessage(customMessage)
                }
            }
            .show()
    }

    private fun handleQuickAction(action: AfterCallActionItem) {
        when (action.id) {
            "add_contact" -> openDialerForNumber(callerNumber)
            "send_sms" -> openConversation()
            "whatsapp" -> openWhatsAppOrSearch()
            "set_alarm" -> openAlarmSetter()
            "reminder" -> {
                selectTab(AfterCallTab.REMINDERS)
                showReminderEditor()
            }
            "send_email" -> sendEmail()
            "instagram" -> openInstalledAppOrFallback(
                packageName = PACKAGE_INSTAGRAM,
                fallbackUri = Uri.parse("https://www.instagram.com/")
            )
            "youtube" -> openInstalledAppOrFallback(
                packageName = PACKAGE_YOUTUBE,
                fallbackUri = Uri.parse("https://www.youtube.com/")
            )
            "web" -> openBrowser()
        }
    }

    private fun setupReminderUi() {
        val now = Calendar.getInstance()
        pickerReminderDay.minValue = 0
        pickerReminderDay.maxValue = 2
        pickerReminderDay.displayedValues = arrayOf("Yesterday", "Today", "Tomorrow")
        pickerReminderDay.value = 1

        pickerReminderHour.minValue = 0
        pickerReminderHour.maxValue = 23
        pickerReminderHour.displayedValues = Array(24) { String.format(Locale.getDefault(), "%02d", it) }
        pickerReminderHour.value = now.get(Calendar.HOUR_OF_DAY)

        pickerReminderMinute.minValue = 0
        pickerReminderMinute.maxValue = 59
        pickerReminderMinute.displayedValues = Array(60) { String.format(Locale.getDefault(), "%02d", it) }
        pickerReminderMinute.value = now.get(Calendar.MINUTE)
        updateReminderTimeValue()

        findViewById<View>(R.id.buttonSelectReminderTime).setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            showReminderTimePicker()
        }

        renderReminderColorChoices()
        applyReminderButtonColors()

        buttonCancelReminder.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            showReminderDisplayState()
        }

        buttonSaveReminder.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            saveReminderFromEditor()
        }

        buttonEditReminder.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            showReminderEditor(savedReminder)
        }

        buttonDeleteReminder.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            savedReminder = null
            showReminderDisplayState()
        }

        showReminderDisplayState()
    }

    private fun applyReminderButtonColors() {
        val white = ColorStateList.valueOf(Color.WHITE)
        buttonAddReminder.setTextColor(Color.WHITE)
        buttonAddReminder.iconTint = white
        buttonSaveReminder.setTextColor(Color.WHITE)
        buttonAddReminder.post {
            buttonAddReminder.setTextColor(Color.WHITE)
            buttonAddReminder.iconTint = white
            buttonSaveReminder.setTextColor(Color.WHITE)
        }
    }

    private fun renderReminderColorChoices() {
        reminderColorRow.removeAllViews()
        reminderColors.forEach { color ->
            val swatch = View(this).apply {
                background = reminderSwatchDrawable(color, color == selectedReminderColor)
                setOnClickListener {
                    selectedReminderColor = color
                    renderReminderColorChoices()
                }
            }
            val size = (34 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (5 * resources.displayMetrics.density).toInt()
                marginEnd = (5 * resources.displayMetrics.density).toInt()
            }
            reminderColorRow.addView(swatch, params)
        }
    }

    private fun reminderSwatchDrawable(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) {
                setStroke((3 * resources.displayMetrics.density).toInt(), Color.WHITE)
            }
        }
    }

    private fun showReminderEditor(existing: ReminderUiState? = null) {
        val reminder = existing
        if (reminder != null) {
            editReminderTitle.setText(reminder.title)
            pickerReminderDay.value = (reminder.dayOffset + 1).coerceIn(0, 2)
            pickerReminderHour.value = reminder.hour
            pickerReminderMinute.value = reminder.minute
            selectedReminderColor = reminder.color
        } else {
            val target = textContactName.text?.toString()?.takeIf { it.isNotBlank() }
                ?: getString(R.string.after_call_reminder_default_title)
            editReminderTitle.setText(getString(R.string.after_call_reminder_label, target))
        }
        updateReminderTimeValue()
        renderReminderColorChoices()
        reminderEmptyState.visibility = View.GONE
        reminderSavedState.visibility = View.GONE
        reminderEditorState.visibility = View.VISIBLE
    }

    private fun showReminderDisplayState() {
        reminderEditorState.visibility = View.GONE
        if (savedReminder == null) {
            reminderSavedState.visibility = View.GONE
            reminderEmptyState.visibility = View.VISIBLE
        } else {
            reminderEmptyState.visibility = View.GONE
            reminderSavedState.visibility = View.VISIBLE
            renderSavedReminder()
        }
    }

    private fun saveReminderFromEditor() {
        val title = editReminderTitle.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.after_call_reminder_default_title)
        val reminder = ReminderUiState(
            title = title,
            dayOffset = pickerReminderDay.value - 1,
            hour = pickerReminderHour.value,
            minute = pickerReminderMinute.value,
            color = selectedReminderColor
        )
        savedReminder = reminder
        requestSystemAlarm(reminder)
        Toast.makeText(this, R.string.after_call_reminder_saved, Toast.LENGTH_SHORT).show()
        showReminderDisplayState()
    }

    private fun renderSavedReminder() {
        val reminder = savedReminder ?: return
        savedReminderColorDot.background = reminderDotDrawable(reminder.color)
        textSavedReminderTitle.text = reminder.title
        textSavedReminderTime.text = formatReminderTime(reminder.hour, reminder.minute)
        textSavedReminderDate.text = reminderDayLabel(reminder.dayOffset)
    }

    private fun showReminderTimePicker() {
        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                pickerReminderHour.value = selectedHour
                pickerReminderMinute.value = selectedMinute
                updateReminderTimeValue()
            },
            pickerReminderHour.value,
            pickerReminderMinute.value,
            android.text.format.DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun updateReminderTimeValue() {
        textReminderTimeValue.text = formatReminderTime(pickerReminderHour.value, pickerReminderMinute.value)
    }

    private fun formatReminderTime(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val pattern = if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "hh:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
    }

    private fun reminderDotDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun reminderDayLabel(dayOffset: Int): String {
        return when (dayOffset) {
            -1 -> "Yesterday"
            1 -> "Tomorrow"
            else -> "Today"
        }
    }

    private fun requestSystemAlarm(reminder: ReminderUiState) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, reminder.title)
            putExtra(AlarmClock.EXTRA_HOUR, reminder.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, reminder.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (intent.resolveActivity(packageManager) != null) {
            runCatching { startActivity(intent) }
        }
    }

    private fun observeViewModel() {
        viewModel.quickResponses.observe(this) { responses ->
            quickResponseAdapter.submitList(responses.toList())
        }

        viewModel.isSending.observe(this) { isSending ->
            recyclerQuickResponses.alpha = if (isSending) 0.72f else 1f
            recyclerQuickResponses.isEnabled = !isSending
        }

        viewModel.sendResult.observe(this) { result ->
            when (result) {
                is CallAfterViewModel.SendResult.Success -> {
                    Toast.makeText(this, R.string.sms_sent_success, Toast.LENGTH_SHORT).show()
                    viewModel.clearSendResult()
                    navigateToMainActivity()
                }
                is CallAfterViewModel.SendResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSendResult()
                }
                null -> Unit
            }
        }

        viewModel.contactInfo.observe(this) { info ->
            textContactName.text = info.name ?: if (info.number.isNotBlank()) info.number else getString(R.string.unknown_number)
            textAvatarLetter.visibility = View.GONE
            imageAvatar.visibility = View.VISIBLE
            imageAvatar.clearColorFilter()

            if (!info.photoUri.isNullOrBlank()) {
                Picasso.get()
                    .load(Uri.parse(info.photoUri))
                    .placeholder(R.drawable.avatar)
                    .error(R.drawable.avatar)
                    .fit()
                    .centerInside()
                    .into(imageAvatar)
            } else {
                imageAvatar.setImageResource(R.drawable.avatar)
            }
        }
    }

    private fun loadContactInfo() {
        val number = callerNumber
        if (number.isNullOrEmpty()) {
            viewModel.setContactInfo(
                CallAfterViewModel.ContactInfo(
                    name = null,
                    number = getString(R.string.unknown_number),
                    photoUri = null,
                    isKnownContact = false
                )
            )
            return
        }

        Thread {
            try {
                var contactFound = false
                var contactName: String? = null
                var photoUri: String? = null

                val normalizedNumber = normalizePhoneNumber(number)
                val last10 = normalizedNumber.takeLast(10)
                val variations = listOf(
                    normalizedNumber,
                    "+$normalizedNumber",
                    last10,
                    "+91$last10",
                    "91$last10"
                ).distinct()

                for (phoneNumber in variations) {
                    if (contactFound) break

                    val uri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(phoneNumber)
                    )
                    val projection = arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_URI
                    )

                    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                            val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)

                            contactName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                            photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null

                            if (!contactName.isNullOrEmpty()) {
                                contactFound = true
                            }
                        }
                    }
                }

                runOnUiThread {
                    viewModel.setContactInfo(
                        CallAfterViewModel.ContactInfo(
                            name = contactName,
                            number = number,
                            photoUri = photoUri,
                            isKnownContact = contactFound
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contact info", e)
                runOnUiThread {
                    viewModel.setContactInfo(
                        CallAfterViewModel.ContactInfo(
                            name = null,
                            number = number,
                            photoUri = null,
                            isKnownContact = false
                        )
                    )
                }
            }
        }.start()
    }

    private fun loadRecentConversations() {
        lifecycleScope.launch {
            val conversations = withContext(Dispatchers.IO) {
                loadRecentConversationsForAfterCall()
            }

            recentConversationAdapter.submitList(conversations)
            textMessagesEmpty.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private suspend fun loadRecentConversationsForAfterCall(): List<Conversation> {
        ConversationCache.getCached("All")
            ?.filter { it.threadId > 0 }
            ?.take(10)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val roomConversations = runCatching {
            MessagesApp.database.conversationDao()
                .getActiveConversations()
                .filter { it.threadId > 0 }
                .take(10)
        }.getOrElse {
            Log.e(TAG, "Failed to load recent conversations from Room", it)
            emptyList()
        }

        if (roomConversations.isNotEmpty()) {
            return roomConversations
        }

        return loadRecentConversationsFromSmsProvider()
    }

    private fun loadRecentConversationsFromSmsProvider(): List<Conversation> {
        val conversationsMap = linkedMapOf<Long, Conversation>()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )

        return runCatching {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                    if (threadId <= 0L) continue

                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty()
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val isUnreadInbox =
                        cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 0 &&
                            cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)) == Telephony.Sms.MESSAGE_TYPE_INBOX

                    val existing = conversationsMap[threadId]
                    if (existing == null) {
                        conversationsMap[threadId] = Conversation(
                            threadId = threadId,
                            address = address,
                            contactName = lookupContactName(address),
                            snippet = body,
                            date = date,
                            unreadCount = if (isUnreadInbox) 1 else 0
                        )
                    } else if (isUnreadInbox) {
                        conversationsMap[threadId] = existing.copy(unreadCount = existing.unreadCount + 1)
                    }
                }
            }

            conversationsMap.values
                .sortedByDescending { it.date }
                .take(10)
        }.getOrElse {
            Log.e(TAG, "Failed to load recent conversations from SMS provider", it)
            emptyList()
        }
    }

    private fun lookupContactName(address: String): String? {
        if (address.isBlank()) return null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )
        return contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            } else {
                null
            }
        }
    }

    private fun normalizePhoneNumber(number: String): String {
        val cleaned = number.replace(Regex("[^\\d+]"), "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.length > 10 -> "+$cleaned"
            else -> cleaned
        }
    }

    private fun makeCall() {
        try {
            openDialerForNumber(callerNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            Toast.makeText(this, R.string.call_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openConversation() {
        openConversationDetailForCaller(callerNumber)
    }

    private fun addToContacts() {
        try {
            val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, callerNumber ?: "")
                viewModel.contactInfo.value?.name?.takeIf { it.isNotBlank() }?.let {
                    putExtra(ContactsContract.Intents.Insert.NAME, it)
                }
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to contacts", e)
            Toast.makeText(this, R.string.add_contact_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDialerForNumber(number: String?) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            if (!number.isNullOrBlank()) {
                data = Uri.parse("tel:$number")
            }
        }
        startActivity(intent)
        finish()
    }

    private fun openConversationDetailForCaller(number: String?) {
        if (number.isNullOrBlank()) {
            navigateToMainActivity()
            return
        }

        lifecycleScope.launch {
            try {
                val threadId = withContext(Dispatchers.IO) {
                    Telephony.Threads.getOrCreateThreadId(this@CallAfterActivity, number)
                }
                openConversationDetail(threadId, number)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open conversation for caller", e)
                navigateToMainActivity()
            }
        }
    }

    private fun openConversationDetail(threadId: Long, address: String) {
        startActivity(
            Intent(this, ConversationDetailActivity::class.java).apply {
                putExtra("thread_id", threadId)
                putExtra("address", address)
            }
        )
        finish()
    }

    private fun navigateToMainActivity() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private fun openWhatsAppOrSearch() {
        val number = callerNumber
        if (number.isNullOrBlank()) {
            openInstalledAppOrFallback(PACKAGE_WHATSAPP, Uri.parse("https://www.whatsapp.com/"))
            return
        }

        val url = "https://wa.me/${number.replace("+", "")}"
        val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(PACKAGE_WHATSAPP)
        }
        val businessIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(PACKAGE_WHATSAPP_BUSINESS)
        }
        when {
            startExternalActivity(whatsappIntent) -> finish()
            startExternalActivity(businessIntent) -> finish()
            startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) -> finish()
            else -> Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmail() {
        val subjectTarget = textContactName.text?.toString()?.takeIf { it.isNotBlank() }
            ?: callerNumber
            ?: getString(R.string.unknown_number)
        val gmailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.after_call_reminder_label, subjectTarget))
            setPackage(PACKAGE_GMAIL)
        }
        val fallbackIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.after_call_reminder_label, subjectTarget))
        }
        when {
            startExternalActivity(gmailIntent) -> finish()
            startExternalActivity(fallbackIntent) -> finish()
            else ->
            Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAlarmSetter() {
        val labelTarget = textContactName.text?.toString()?.takeIf { it.isNotBlank() }
            ?: callerNumber
            ?: getString(R.string.unknown_number)
        val showAlarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val setAlarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, getString(R.string.after_call_reminder_label, labelTarget))
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        when {
            startExternalActivity(showAlarmIntent) -> finish()
            startExternalActivity(setAlarmIntent) -> finish()
            else ->
            Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBrowser() {
        val baseTerm = textContactName.text?.toString()?.takeIf { it.isNotBlank() }
            ?.takeUnless { it == getString(R.string.unknown_number) }
            ?: callerNumber
        val uri = if (baseTerm.isNullOrBlank()) {
            Uri.parse("https://www.google.com/")
        } else {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(baseTerm)}")
        }
        if (startExternalActivity(Intent(Intent.ACTION_VIEW, uri))) {
            finish()
        } else {
            Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInstalledAppOrFallback(packageName: String, fallbackUri: Uri) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        when {
            launchIntent != null && startExternalActivity(launchIntent) -> finish()
            startExternalActivity(Intent(Intent.ACTION_VIEW, fallbackUri)) -> finish()
            else -> Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startExternalActivity(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open quick action", e)
            false
        }
    }

    private fun searchCallerOnWeb(prefix: String?) {
        val baseTerm = textContactName.text?.toString()?.takeIf { it.isNotBlank() }
            ?.takeUnless { it == getString(R.string.unknown_number) }
            ?: callerNumber
            ?: return
        val query = listOfNotNull(prefix, baseTerm).joinToString(" ")
        val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDebounced(): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime < DEBOUNCE_DELAY_MS) {
            return false
        }
        lastClickTime = currentTime
        return true
    }

    private fun loadNativeAd() {
        if (RemoteConfigHelper.shouldUseAfterCallAdaptiveBannerOnly()) {
            AfterCallAdPreloader.clear()
            loadAdaptiveBanner()
            return
        }

        showAfterCallAdLoading()

        val preloaded = AfterCallAdPreloader.consumePreloadedAd()
        if (preloaded != null) {
            if (!isDestroyed && !isFinishing) {
                currentNativeAd?.destroy()
                currentNativeAd = preloaded
                adaptiveBannerView?.visibility = View.GONE
                populateNativeAdView(preloaded)
                return
            }
            preloaded.destroy()
        }

        val nativeAdUnitId = com.text.messages.sms.messanger.util.AdConfig.resolveAfterCallNativeAdUnitId(this)
        if (nativeAdUnitId.isBlank()) {
            loadAdaptiveBanner()
            return
        }

        val adLoader = AdLoader.Builder(this, nativeAdUnitId)
            .forNativeAd { nativeAd ->
                currentNativeAd?.destroy()
                currentNativeAd = nativeAd

                if (isDestroyed || isFinishing) {
                    nativeAd.destroy()
                    return@forNativeAd
                }

                adaptiveBannerView?.visibility = View.GONE
                populateNativeAdView(nativeAd)
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, true)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(
                        TAG,
                        "After-call native failed: code=${loadAdError.code} ${loadAdError.message} unit=$nativeAdUnitId"
                    )
                    AnalyticsHelper.logAdLoad("native", nativeAdUnitId, false)
                    AnalyticsHelper.logAdError("native", nativeAdUnitId, loadAdError.code.toString())
                    loadAdaptiveBanner()
                }

                override fun onAdClicked() {
                    AnalyticsHelper.logAdClick("native", nativeAdUnitId)
                }

                override fun onAdImpression() {
                    AnalyticsHelper.logAdImpression("native", nativeAdUnitId)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun showAfterCallAdLoading() {
        nativeAdView.visibility = View.GONE
        adaptiveBannerView?.visibility = View.GONE
        AdLoadingShimmerHelper.showNativeLoading(
            nativeAdContainer,
            nativeAdView,
            R.layout.layout_native_ad_shimmer_full_bleed
        )
    }

    private fun preserveHeaderDialerIcon() {
        buttonCall.clearColorFilter()
        buttonCall.imageTintList = null
        buttonCall.post {
            buttonCall.clearColorFilter()
            buttonCall.imageTintList = null
        }
    }

    private fun loadAdaptiveBanner() {
        val bannerAdUnitId = com.text.messages.sms.messanger.util.AdConfig.resolveAfterCallAdaptiveBannerAdUnitId(this)
        if (bannerAdUnitId.isBlank()) {
            nativeAdView.visibility = View.GONE
            adaptiveBannerView?.visibility = View.GONE
            AdLoadingShimmerHelper.hideNative(nativeAdContainer, nativeAdView)
            return
        }

        showAfterCallAdLoading()
        nativeAdContainer.post {
            if (isFinishing || isDestroyed) {
                return@post
            }

            currentNativeAd?.destroy()
            currentNativeAd = null
            nativeAdView.visibility = View.GONE

            val adWidthPx = nativeAdContainer.width
                .takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - nativeAdContainer.paddingLeft - nativeAdContainer.paddingRight)
            val adSize = getAfterCallAdaptiveAdSize(adWidthPx)
            val slotHeightPx = measureAfterCallAdSlotHeightPx(adWidthPx)
            val bannerView = getOrCreateAdaptiveBannerView(bannerAdUnitId)
            bannerView.setAdSize(adSize)
            bannerView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                slotHeightPx
            )
            bannerView.visibility = View.GONE
            bannerView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    bannerView.adSize?.let { loadedAdSize ->
                        bannerView.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            loadedAdSize.getHeightInPixels(this@CallAfterActivity)
                        )
                    }
                    nativeAdView.visibility = View.GONE
                    AdLoadingShimmerHelper.showNativeContent(nativeAdContainer, bannerView)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    nativeAdView.visibility = View.GONE
                    bannerView.visibility = View.GONE
                    AdLoadingShimmerHelper.hideNative(nativeAdContainer, bannerView)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, false)
                    AnalyticsHelper.logAdError("banner", bannerAdUnitId, loadAdError.code.toString())
                }

                override fun onAdClicked() {
                    AnalyticsHelper.logAdClick("banner", bannerAdUnitId)
                }

                override fun onAdImpression() {
                    AnalyticsHelper.logAdImpression("banner", bannerAdUnitId)
                }
            }
            bannerView.loadAd(AdRequest.Builder().build())
        }
    }

    private fun getOrCreateAdaptiveBannerView(adUnitId: String): AdView {
        val existing = adaptiveBannerView
        if (existing != null && existing.adUnitId == adUnitId) {
            return existing
        }

        existing?.let {
            nativeAdContainer.removeView(it)
            it.destroy()
        }

        return AdView(this).apply {
            this.adUnitId = adUnitId
            visibility = View.GONE
            nativeAdContainer.addView(
                this,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            adaptiveBannerView = this
        }
    }

    private fun getAfterCallAdaptiveAdSize(adWidthPx: Int): AdSize {
        val adWidthDp = (adWidthPx / resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val slotHeightDp = (measureAfterCallAdSlotHeightPx(adWidthPx) / resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(50)
        return AdSize.getInlineAdaptiveBannerAdSize(adWidthDp, slotHeightDp)
    }

    private fun measureAfterCallAdSlotHeightPx(adWidthPx: Int): Int {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(adWidthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        nativeAdView.measure(widthSpec, heightSpec)
        return nativeAdView.measuredHeight.coerceAtLeast((220 * resources.displayMetrics.density).toInt())
    }

    private fun populateNativeAdView(nativeAd: NativeAd) {
        val mediaView = nativeAdView.findViewById<MediaView>(R.id.adMedia)
        nativeAdView.mediaView = mediaView
        mediaView.mediaContent = nativeAd.mediaContent

        val headlineView = nativeAdView.findViewById<TextView>(R.id.adHeadline)
        nativeAdView.headlineView = headlineView
        headlineView.text = nativeAd.headline ?: "Sponsored"

        val iconView = nativeAdView.findViewById<ImageView>(R.id.adIcon)
        nativeAdView.iconView = iconView
        if (nativeAd.icon != null) {
            iconView.setImageDrawable(nativeAd.icon?.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }

        val ctaButton = nativeAdView.findViewById<MaterialButton>(R.id.adCta)
        nativeAdView.callToActionView = ctaButton
        ctaButton.text = nativeAd.callToAction ?: getString(R.string.open)

        nativeAdView.setNativeAd(nativeAd)
        AdLoadingShimmerHelper.showNativeContent(nativeAdContainer, nativeAdView)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentNativeAd?.destroy()
        currentNativeAd = null
        adaptiveBannerView?.destroy()
        adaptiveBannerView = null
    }
}
