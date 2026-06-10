package com.text.messages.sms.messanger.ui.caller

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import com.text.messages.sms.messanger.util.AppOpenManager
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.CallAfterLauncher
import com.text.messages.sms.messanger.util.ConversationCache
import com.text.messages.sms.messanger.util.NextGenAdHelper
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
        private const val AFTER_CALL_APP_OPEN_SUPPRESSION_MS = 30_000L
        private const val ROTATING_CARD_INTERVAL_MS = 5_000L
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

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var stickyTopContainer: LinearLayout
    private lateinit var headerSection: View
    private lateinit var callerAppOpen: View
    private lateinit var callerSetting: View
    private lateinit var textCallerViewMore: View
    private lateinit var callerDemoGallery: View
    private lateinit var quickReplyPreviewRow: View
    private lateinit var scheduleActionRow: View
    private lateinit var quickReplyInput: EditText
    private lateinit var quickReplySend: View
    private lateinit var textCallerAppTitle: TextView
    private lateinit var buttonScheduleCard: View
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

    private lateinit var contentContainer: FrameLayout
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
    private val rotatingCardHandler = Handler(Looper.getMainLooper())
    private var rotatingCardIndex = 0
    private val rotatingCardRunnable = object : Runnable {
        override fun run() {
            rotatingCardIndex = (rotatingCardIndex + 1) % 2
            updateRotatingCard()
            rotatingCardHandler.postDelayed(this, ROTATING_CARD_INTERVAL_MS)
        }
    }

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
        AppOpenManager.suppressAppOpenFor(AFTER_CALL_APP_OPEN_SUPPRESSION_MS)
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

    override fun onResume() {
        super.onResume()
        AppOpenManager.suppressAppOpenFor(AFTER_CALL_APP_OPEN_SUPPRESSION_MS)
        if (::headerSection.isInitialized) {
            applyCallerCardTheme()
        }
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
        rootLayout = findViewById(R.id.rootLayout)
        stickyTopContainer = findViewById(R.id.stickyTopContainer)
        headerSection = findViewById(R.id.headerSection)
        callerAppOpen = findViewById(R.id.callerAppOpen)
        callerSetting = findViewById(R.id.callerSetting)
        textCallerViewMore = findViewById(R.id.textCallerViewMore)
        callerDemoGallery = findViewById(R.id.callerDemoGallery)
        quickReplyPreviewRow = findViewById(R.id.quickReplyPreviewRow)
        scheduleActionRow = findViewById(R.id.scheduleActionRow)
        quickReplyInput = findViewById(R.id.quickReplyInput)
        quickReplySend = findViewById(R.id.quickReplySend)
        textCallerAppTitle = findViewById(R.id.textCallerAppTitle)
        buttonScheduleCard = findViewById(R.id.buttonScheduleCard)
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

        contentContainer = findViewById(R.id.contentContainer)
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
        applyCallerCardTheme()
        setupTabs()
        setupRecentMessages()
        setupQuickResponses()
        setupReminderUi()
        setupQuickActions()
        setupHeaderActions()
        startRotatingCard()
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

    private fun applyCallerCardTheme() {
        val callerTheme = AppPreferences.getCallerTheme(this)
        val themeColor = parseThemeColor(callerTheme.topColor, Color.parseColor("#479DA2"))
        val darkerColor = parseThemeColor(callerTheme.bottomColor, Color.parseColor("#034D57"))
        val buttonColor = parseThemeColor(callerTheme.buttonColor, Color.parseColor("#469CA1"))
        headerSection.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(themeColor, darkerColor)
        ).apply {
            val radius = 6f * resources.displayMetrics.density
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
        listOf(callerAppOpen, callerSetting).forEach { view ->
            view.background = GradientDrawable().apply {
                setColor(buttonColor)
                cornerRadius = 6f * resources.displayMetrics.density
            }
        }
        textCallerViewMore.background = GradientDrawable().apply {
            setColor(buttonColor)
            cornerRadius = 22f * resources.displayMetrics.density
        }
        if (viewModel.contactInfo.value?.photoUri.isNullOrBlank()) {
            applyDefaultCallerAvatar(buttonColor)
        }
    }

    private fun applyDefaultCallerAvatar(themeColor: Int = parseThemeColor(AppPreferences.getCallerTheme(this).buttonColor, Color.parseColor("#469CA1"))) {
        imageAvatar.visibility = View.VISIBLE
        imageAvatar.background = ContextCompat.getDrawable(this, R.drawable.bg_caller_avatar_circle)
        imageAvatar.setImageResource(R.drawable.ic_caller_avatar_person)
        imageAvatar.imageTintList = ColorStateList.valueOf(themeColor)
        imageAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
        imageAvatar.setPadding(9.dpToPx(), 9.dpToPx(), 9.dpToPx(), 9.dpToPx())
    }

    private fun parseThemeColor(value: String?, fallback: Int): Int {
        return try {
            Color.parseColor(value)
        } catch (e: Exception) {
            fallback
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        return Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupTabs() {
        tabMessages.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            makeCall()
        }
        tabQuickMessages.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            showQuickRepliesSheet()
        }
        tabReminders.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            addToContacts()
        }
        tabActions.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            showMoreActionsSheet()
        }
    }

    private fun selectTab(tab: AfterCallTab) {
        selectedTab = tab
        contentContainer.visibility = if (tab == AfterCallTab.MESSAGES) View.GONE else View.VISIBLE
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
            layoutManager = object : LinearLayoutManager(this@CallAfterActivity) {
                override fun canScrollVertically(): Boolean = true
            }
            adapter = quickActionAdapter
            itemAnimator = null
        }

        quickActionAdapter.submitList(
            listOf(
                AfterCallActionItem("add_contact", R.drawable.ic_person_add, getString(R.string.after_call_action_add_caller_contacts)),
                AfterCallActionItem("send_sms", R.drawable.ic_chat_bubble, getString(R.string.after_call_action_messages)),
                AfterCallActionItem("send_email", R.drawable.ic_after_call_mail, getString(R.string.after_call_action_send_email)),
                AfterCallActionItem("calendar", R.drawable.ic_after_call_calendar, getString(R.string.after_call_action_calendar)),
                AfterCallActionItem("web", R.drawable.ic_after_call_globe, getString(R.string.after_call_action_web)),
                AfterCallActionItem("call_settings", R.drawable.caller_ic_settings, getString(R.string.after_call_action_call_information_settings))
            )
        )
    }

    private fun setupHeaderActions() {
        buttonCall.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            makeCall()
        }
        findViewById<View>(R.id.popupClose).setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            finish()
        }
        findViewById<View>(R.id.popupThemeIcon).setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            openThemeSelection()
        }
        callerAppOpen.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            openCurrentApp()
        }
        callerSetting.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            openCallerSettings()
        }
        textCallerViewMore.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            showQuickRepliesSheet()
        }
        callerDemoGallery.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            if (rotatingCardIndex == 0) {
                showQuickRepliesSheet()
            } else {
                openScheduledForCaller()
            }
        }
        buttonScheduleCard.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            openScheduledForCaller()
        }
        quickReplySend.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            val message = quickReplyInput.text?.toString()?.trim().orEmpty()
            if (message.isBlank()) {
                quickReplyInput.requestFocus()
            } else {
                sendQuickMessage(message)
            }
        }
    }

    private fun openThemeSelection() {
        startActivity(Intent(this, CallerThemeActivity::class.java))
    }

    private fun openCallerSettings() {
        startActivity(Intent(this, CallerSettingsActivity::class.java))
    }

    private fun openCurrentApp() {
        navigateToMainActivity()
    }

    private fun startRotatingCard() {
        rotatingCardIndex = ((callEndTime / 1000L) % 2L).toInt()
        updateRotatingCard()
        rotatingCardHandler.removeCallbacks(rotatingCardRunnable)
        rotatingCardHandler.postDelayed(rotatingCardRunnable, ROTATING_CARD_INTERVAL_MS)
    }

    private fun showQuickReplyInputCard() {
        rotatingCardIndex = 0
        updateRotatingCard()
        quickReplyInput.requestFocus()
    }

    private fun updateRotatingCard() {
        val showQuickReplies = rotatingCardIndex == 0
        quickReplyPreviewRow.visibility = if (showQuickReplies) View.VISIBLE else View.GONE
        textCallerViewMore.visibility = View.GONE
        textCallerAppTitle.visibility = View.GONE
        scheduleActionRow.visibility = if (showQuickReplies) View.GONE else View.VISIBLE
        callerDemoGallery.setBackgroundResource(
            if (showQuickReplies) {
                R.drawable.bg_after_call_quick_reply_panel
            } else {
                R.drawable.bg_after_call_promo_card
            }
        )
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

    private fun showQuickRepliesSheet() {
        val sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_after_call_quick_replies, null, false)
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(sheetView)
        bottomSheet.setOnShowListener {
            bottomSheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val responses = viewModel.quickResponses.value.orEmpty().filterNot { it.isCustom }
        val radioButtons = listOf<RadioButton>(
            sheetView.findViewById(R.id.radioQuickReplyOne),
            sheetView.findViewById(R.id.radioQuickReplyTwo),
            sheetView.findViewById(R.id.radioQuickReplyThree)
        )
        radioButtons.forEachIndexed { index, radioButton ->
            val response = responses.getOrNull(index)
            if (response == null) {
                radioButton.visibility = View.GONE
                return@forEachIndexed
            }
            radioButton.text = response.text
            radioButton.setOnClickListener {
                radioButtons.forEach { it.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_radio_unselected, 0) }
                radioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_radio_selected, 0)
                bottomSheet.dismiss()
                sendQuickMessage(response.text)
            }
        }

        val customInput = sheetView.findViewById<EditText>(R.id.editQuickReplyCustom)
        val sendButton = sheetView.findViewById<TextView>(R.id.buttonQuickReplySend)
        customInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sendButton.visibility = if (s?.toString()?.trim().isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        sendButton.setOnClickListener {
            val message = customInput.text?.toString()?.trim().orEmpty()
            if (message.isNotEmpty()) {
                bottomSheet.dismiss()
                sendQuickMessage(message)
            }
        }

        bottomSheet.show()
    }

    private fun showMoreActionsSheet() {
        startActivity(
            Intent(this, CallerMoreActivity::class.java).apply {
                putExtra(CallAfterLauncher.EXTRA_CALLER_NUMBER, callerNumber)
                putExtra("CALLER_NAME", textContactName.text?.toString())
            }
        )
    }

    private fun handleQuickAction(action: AfterCallActionItem) {
        when (action.id) {
            "add_contact" -> addToContacts()
            "send_sms" -> openConversation()
            "send_email" -> sendEmail()
            "calendar" -> openCalendar()
            "web" -> openBrowser()
            "call_settings" -> openCallerSettings()
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

            if (!info.photoUri.isNullOrBlank()) {
                imageAvatar.background = null
                imageAvatar.imageTintList = null
                imageAvatar.clearColorFilter()
                imageAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                imageAvatar.setPadding(0, 0, 0, 0)
                Picasso.get()
                    .load(Uri.parse(info.photoUri))
                    .placeholder(R.drawable.avatar)
                    .error(R.drawable.avatar)
                    .fit()
                    .centerInside()
                    .into(imageAvatar)
            } else {
                applyDefaultCallerAvatar()
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
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, "")
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

    private fun openScheduledForCaller() {
        val number = callerNumber
        if (number.isNullOrBlank()) {
            startActivity(Intent(this, com.text.messages.sms.messanger.ui.scheduled.ScheduledMessagesActivity::class.java))
            return
        }

        lifecycleScope.launch {
            try {
                val threadId = withContext(Dispatchers.IO) {
                    Telephony.Threads.getOrCreateThreadId(this@CallAfterActivity, number)
                }
                startActivity(
                    Intent(this@CallAfterActivity, ConversationDetailActivity::class.java).apply {
                        putExtra("thread_id", threadId)
                        putExtra("address", number)
                        putExtra("contact_name", viewModel.contactInfo.value?.name ?: number)
                        putExtra("is_scheduling", true)
                    }
                )
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open scheduled message flow", e)
                startActivity(Intent(this@CallAfterActivity, com.text.messages.sms.messanger.ui.scheduled.ScheduledMessagesActivity::class.java))
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

    private fun openCalendar() {
        val labelTarget = textContactName.text?.toString()?.takeIf { it.isNotBlank() }
            ?: callerNumber
            ?: getString(R.string.unknown_number)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, getString(R.string.after_call_reminder_label, labelTarget))
        }
        if (startExternalActivity(intent)) {
            finish()
        } else {
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

        NextGenAdHelper.loadNative(
            adUnitId = nativeAdUnitId,
            preferLandscape = true,
            onLoaded = { nativeAd ->
                nativeAd.adEventCallback = object : NativeAdEventCallback {
                    override fun onAdClicked() {
                        AnalyticsHelper.logAdClick("native", nativeAdUnitId)
                    }

                    override fun onAdImpression() {
                        AnalyticsHelper.logAdImpression("native", nativeAdUnitId)
                    }
                }
                currentNativeAd?.destroy()
                currentNativeAd = nativeAd

                if (isDestroyed || isFinishing) {
                    nativeAd.destroy()
                    return@loadNative
                }

                adaptiveBannerView?.visibility = View.GONE
                populateNativeAdView(nativeAd)
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, true)
            },
            onFailed = { loadAdError ->
                Log.e(
                    TAG,
                    "After-call native failed: code=${loadAdError.code} ${loadAdError.message} unit=$nativeAdUnitId"
                )
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, false)
                AnalyticsHelper.logAdError("native", nativeAdUnitId, loadAdError.code.toString())
                loadAdaptiveBanner()
            }
        )
    }

    private fun showAfterCallAdLoading() {
        setAfterCallAdSlotVisible(true)
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
            setAfterCallAdSlotVisible(false)
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
            bannerView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                slotHeightPx
            )
            bannerView.visibility = View.GONE
            NextGenAdHelper.loadBanner(
                activity = this,
                adView = bannerView,
                adUnitId = bannerAdUnitId,
                adSize = adSize,
                onLoaded = { bannerAd ->
                    bannerView.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        bannerAd.getAdSize().getHeightInPixels(this@CallAfterActivity)
                    )
                    nativeAdView.visibility = View.GONE
                    setAfterCallAdSlotVisible(true)
                    AdLoadingShimmerHelper.showNativeContent(nativeAdContainer, bannerView)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, true)
                },
                onFailed = { loadAdError ->
                    nativeAdView.visibility = View.GONE
                    bannerView.visibility = View.GONE
                    AdLoadingShimmerHelper.hideNative(nativeAdContainer, bannerView)
                    setAfterCallAdSlotVisible(false)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, false)
                    AnalyticsHelper.logAdError("banner", bannerAdUnitId, loadAdError.code.toString())
                },
                onClicked = {
                    AnalyticsHelper.logAdClick("banner", bannerAdUnitId)
                },
                onImpression = {
                    AnalyticsHelper.logAdImpression("banner", bannerAdUnitId)
                }
            )
        }
    }

    private fun getOrCreateAdaptiveBannerView(adUnitId: String): AdView {
        val existing = adaptiveBannerView
        if (existing != null && existing.getTag(R.id.ad_unit_id_tag) == adUnitId) {
            return existing
        }

        existing?.let {
            nativeAdContainer.removeView(it)
            it.destroy()
        }

        return AdView(this).apply {
            setTag(R.id.ad_unit_id_tag, adUnitId)
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
        setAfterCallAdSlotVisible(true)
        val mediaView = nativeAdView.findViewById<MediaView>(R.id.adMedia)
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

        nativeAdView.registerNativeAd(nativeAd, mediaView)
        AdLoadingShimmerHelper.showNativeContent(nativeAdContainer, nativeAdView)
    }

    private fun setAfterCallAdSlotVisible(visible: Boolean) {
        val stickyParams = stickyTopContainer.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val contentParams = contentContainer.layoutParams as? ConstraintLayout.LayoutParams

        if (visible) {
            nativeAdContainer.visibility = View.VISIBLE
            stickyParams.topToTop = ConstraintLayout.LayoutParams.UNSET
            stickyParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            stickyParams.bottomToTop = R.id.nativeAdContainer
            stickyParams.verticalBias = 0.5f
            contentParams?.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            contentParams?.bottomToTop = R.id.nativeAdContainer
        } else {
            nativeAdContainer.visibility = View.GONE
            stickyParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            stickyParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
            stickyParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            stickyParams.verticalBias = 0.62f
            contentParams?.bottomToTop = ConstraintLayout.LayoutParams.UNSET
            contentParams?.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }

        stickyTopContainer.layoutParams = stickyParams
        contentParams?.let { contentContainer.layoutParams = it }
        rootLayout.requestLayout()
    }

    override fun onDestroy() {
        super.onDestroy()
        rotatingCardHandler.removeCallbacks(rotatingCardRunnable)
        currentNativeAd?.destroy()
        currentNativeAd = null
        adaptiveBannerView?.destroy()
        adaptiveBannerView = null
    }
}
