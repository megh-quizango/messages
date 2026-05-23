package com.text.messages.sms.messanger.ui.caller

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.material.button.MaterialButton
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity
import com.text.messages.sms.messanger.util.AdLoadingShimmerHelper
import com.text.messages.sms.messanger.util.AvatarHelper
import com.text.messages.sms.messanger.util.ThemeManager
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallAfterActivity : BaseActivity() {

    companion object {
        private const val TAG = "CallAfterActivity"
        private const val DEBOUNCE_DELAY_MS = 500L
    }

    private val viewModel: CallAfterViewModel by viewModels()

    // Views
    private lateinit var imageAvatar: CircleImageView
    private lateinit var textAvatarLetter: TextView
    private lateinit var textContactName: TextView
    private lateinit var textCallTime: TextView
    private lateinit var textCallStatus: TextView
    private lateinit var textCallDuration: TextView
    private lateinit var buttonCall: LinearLayout
    private lateinit var buttonMessage: LinearLayout
    private lateinit var buttonAddContact: LinearLayout
    private lateinit var recyclerQuickResponses: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var nativeAdContainer: FrameLayout
    private lateinit var nativeAdView: NativeAdView
    private lateinit var adLoadingPlaceholder: LinearLayout

    private lateinit var quickResponseAdapter: QuickResponseAdapter
    private var currentNativeAd: NativeAd? = null

    // Debounce
    private var lastClickTime: Long = 0

    // Call info
    private var callerNumber: String? = null
    private var callType: String = "completed"
    private var callEndTime: Long = 0
    private var callStartTime: Long = 0
    private var isIncoming: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_after)

        // Apply theme
        ThemeManager.applyTheme(this, findViewById(R.id.rootLayout))
        ThemeManager.setupNavigationBar(this)

        // Handle window insets
        val rootLayout = findViewById<View>(R.id.rootLayout)
        val initialTopPadding = rootLayout.paddingTop
        val initialBottomPadding = rootLayout.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top + initialTopPadding,
                v.paddingRight,
                systemBars.bottom + initialBottomPadding
            )
            insets
        }

        // Get intent extras
        extractIntentExtras()

        // Initialize views
        initViews()

        // Setup UI
        setupUI()

        // Load contact info
        loadContactInfo()

        // Observe ViewModel
        observeViewModel()

        // Load native ad
        loadNativeAd()
    }

    private fun extractIntentExtras() {

        // 1️⃣ Try all known sources
        callerNumber =
            intent.getStringExtra("CALLER_NUMBER")
                ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                        ?: intent.data?.schemeSpecificPart

        // 2️⃣ Normalize
        callerNumber = callerNumber
            ?.replace(Regex("[^\\d+]"), "")
            ?.trim()

        // 3️⃣ Absolute fallback (CRITICAL)
        if (callerNumber.isNullOrEmpty()) {
            callerNumber = getLastCallNumber()
        }

        // 4️⃣ If still null → STOP
        if (callerNumber.isNullOrEmpty()) {
            Log.e(TAG, "❌ Caller number STILL NULL after all fallbacks")
        }

        // call info
        callType = intent.getStringExtra("CALL_TYPE") ?: "completed"
        callEndTime = intent.getLongExtra("CALL_END_TIME", System.currentTimeMillis())
        callStartTime = intent.getLongExtra("CALL_START_TIME", callEndTime)
        isIncoming = intent.getBooleanExtra("IS_INCOMING", false)

        val duration =
            if (callEndTime > callStartTime) callEndTime - callStartTime else 0L

        viewModel.setCallInfo(
            callerNumber,
            callType,
            duration,
            callEndTime,
            isIncoming
        )

        Log.d(TAG, "✅ FINAL callerNumber = $callerNumber")
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
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CallLog", e)
            null
        }
    }



    private fun initViews() {
        // Header views
        imageAvatar = findViewById(R.id.imageAvatar)
        textAvatarLetter = findViewById(R.id.textAvatarLetter)
        textContactName = findViewById(R.id.textContactName)
        textCallTime = findViewById(R.id.textCallTime)
        textCallStatus = findViewById(R.id.textCallStatus)
        textCallDuration = findViewById(R.id.textCallDuration)

        // Action buttons
        buttonCall = findViewById(R.id.buttonCall)
        buttonMessage = findViewById(R.id.buttonMessage)
        buttonAddContact = findViewById(R.id.buttonAddContact)

        // Quick response
        recyclerQuickResponses = findViewById(R.id.recyclerQuickResponses)

        // Message input
        editMessage = findViewById(R.id.editMessage)
        buttonSend = findViewById(R.id.buttonSend)

        // Native ad
        nativeAdContainer = findViewById(R.id.nativeAdContainer)
        nativeAdView = findViewById(R.id.nativeAdView)
        adLoadingPlaceholder = findViewById(R.id.adLoadingPlaceholder)
        adLoadingPlaceholder.visibility = View.GONE
        nativeAdView.visibility = View.GONE
        AdLoadingShimmerHelper.showNativeLoading(nativeAdContainer, nativeAdView)
    }

    private fun setupUI() {
        // Display call info
        displayCallInfo()

        // Setup quick responses RecyclerView
        setupQuickResponses()

        // Setup action buttons
        setupActionButtons()

        // Setup message input
        setupMessageInput()
    }

    private fun displayCallInfo() {
        // Display call time
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        textCallTime.text = timeFormat.format(Date(callEndTime))

        // Display call status
        val status = when (callType) {
            "missed" -> getString(R.string.missed_call)
            "no_answer" -> getString(R.string.no_answer)
            else -> getString(R.string.completed_call)
        }
        textCallStatus.text = status

        // Display call duration if completed call
        val duration = viewModel.getCallDuration()
        if (callType == "completed" && duration > 0) {
            textCallDuration.visibility = View.VISIBLE
            textCallDuration.text = formatDuration(duration)
        } else {
            textCallDuration.visibility = View.GONE
        }

        // Default contact name
        textContactName.text = getString(R.string.contacts)

    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }

    private fun setupQuickResponses() {
        quickResponseAdapter = QuickResponseAdapter { position ->
            viewModel.selectQuickResponse(position)
        }

        recyclerQuickResponses.apply {
            layoutManager = LinearLayoutManager(this@CallAfterActivity)
            adapter = quickResponseAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupActionButtons() {
        // Call button
        buttonCall.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            makeCall()
        }

        // Message button - opens in-app conversation
        buttonMessage.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            openConversation()
        }

        // Add contact button
        buttonAddContact.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            addToContacts()
        }
    }

    private fun setupMessageInput() {
        // Text watcher for EditText
        editMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                viewModel.updateMessageText(text)
                updateSendButtonState(text.isNotEmpty())
            }
        })

        // IME action for send
        editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Send button click
        buttonSend.setOnClickListener {
            if (!isDebounced()) return@setOnClickListener
            sendMessage()
        }
    }

    private fun updateSendButtonState(hasText: Boolean) {
        if (hasText) {
            buttonSend.setImageResource(R.drawable.ic_send)
        } else {
            buttonSend.setImageResource(R.drawable.ic_edit_pencil)
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

    private fun observeViewModel() {
        // Quick responses
        viewModel.quickResponses.observe(this) { responses ->
            quickResponseAdapter.submitList(responses.toList())
        }

        // Message text
        viewModel.messageText.observe(this) { text ->
            if (editMessage.text.toString() != text) {
                editMessage.setText(text)
                editMessage.setSelection(text.length)
            }
            updateSendButtonState(text.isNotEmpty())
        }

        // Sending state
        viewModel.isSending.observe(this) { isSending ->
            buttonSend.isEnabled = !isSending
            editMessage.isEnabled = !isSending
        }

        // Send result
        viewModel.sendResult.observe(this) { result ->
            when (result) {
                is CallAfterViewModel.SendResult.Success -> {
                    Toast.makeText(this, R.string.sms_sent_success, Toast.LENGTH_SHORT).show()
                    viewModel.clearSendResult()
                }
                is CallAfterViewModel.SendResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSendResult()
                }
                null -> { /* Ignored */ }
            }
        }

        // Contact info
        viewModel.contactInfo.observe(this) { info ->
            updateContactUI(info)
        }
    }

    private fun updateContactUI(info: CallAfterViewModel.ContactInfo) {
        // Update name
        textContactName.text = info.name ?: info.number

        // Load avatar
        AvatarHelper.loadAvatar(
            imageView = imageAvatar,
            textView = textAvatarLetter,
            photoUri = info.photoUri,
            contactName = info.name,
            address = info.number,
            context = this
        )

        // Hide add contact button if already a contact
//        if (info.isKnownContact) {
//            buttonAddContact.visibility = View.GONE
//        } else {
//            buttonAddContact.visibility = View.VISIBLE
//        }
        buttonAddContact.visibility = View.VISIBLE
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

                // Try to find contact
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
                                Log.d(TAG, "Contact found: $contactName for number: $number")
                            }
                        }
                    }
                }

                val info = CallAfterViewModel.ContactInfo(
                    name = contactName,
                    number = number,
                    photoUri = photoUri,
                    isKnownContact = contactFound
                )

                runOnUiThread {
                    viewModel.setContactInfo(info)
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

    private fun normalizePhoneNumber(number: String): String {
        val cleaned = number.replace(Regex("[^\\d+]"), "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.length > 10 -> "+${cleaned}"
            else -> cleaned
        }
    }


    private fun makeCall() {
        if (callerNumber.isNullOrEmpty()) {
            Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            openDialerForNumber(callerNumber!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            Toast.makeText(this, R.string.call_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openConversation() {
        val number = callerNumber
        if (number.isNullOrEmpty()) {
            Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Get thread ID for the phone number
            val threadId = android.provider.Telephony.Threads.getOrCreateThreadId(this, number)

            // Get contact name if available
            val contactName = viewModel.contactInfo.value?.name ?: number

            val intent = Intent(this, ConversationDetailActivity::class.java).apply {
                putExtra("thread_id", threadId)
                putExtra("address", number)
                putExtra("contact_name", contactName)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening conversation", e)
            Toast.makeText(this, R.string.message_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToContacts() {
        val number = callerNumber
        if (number.isNullOrEmpty()) {
            Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            openDialerForNumber(number)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to contacts", e)
            Toast.makeText(this, R.string.add_contact_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDialerForNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        startActivity(intent)
        finish()
    }


    private fun sendMessage() {
        val message = editMessage.text.toString().trim()
        if (message.isEmpty()) {
            return
        }
        viewModel.sendMessage()
    }

    // ================== NATIVE AD ==================

    private fun loadNativeAd() {
        val nativeAdUnitId = com.text.messages.sms.messanger.util.RemoteConfigHelper.getNativeAdUnitId()
        if (nativeAdUnitId.isBlank()) {
            AdLoadingShimmerHelper.hideNative(nativeAdContainer, nativeAdView)
            return
        }
        val adLoader = AdLoader.Builder(this, nativeAdUnitId)
            .forNativeAd { nativeAd ->
                // Destroy old ad if exists
                currentNativeAd?.destroy()
                currentNativeAd = nativeAd

                if (isDestroyed || isFinishing) {
                    nativeAd.destroy()
                    return@forNativeAd
                }

                populateNativeAdView(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Native ad failed to load: ${loadAdError.message}")
                    AdLoadingShimmerHelper.hideNative(nativeAdContainer, nativeAdView)
                }

                override fun onAdLoaded() {
                    Log.d(TAG, "Native ad loaded successfully")
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

    private fun populateNativeAdView(nativeAd: NativeAd) {
        // Media
        val mediaView = nativeAdView.findViewById<MediaView>(R.id.adMedia)
        nativeAdView.mediaView = mediaView
        mediaView.mediaContent = nativeAd.mediaContent

        // Headline
        val headlineView = nativeAdView.findViewById<TextView>(R.id.adHeadline)
        nativeAdView.headlineView = headlineView
        headlineView.text = nativeAd.headline ?: "Sponsored"

        // Icon
        val iconView = nativeAdView.findViewById<ImageView>(R.id.adIcon)
        nativeAdView.iconView = iconView
        if (nativeAd.icon != null) {
            iconView.setImageDrawable(nativeAd.icon?.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }

        // CTA
        val ctaButton = nativeAdView.findViewById<MaterialButton>(R.id.adCta)
        nativeAdView.callToActionView = ctaButton
        ctaButton.text = nativeAd.callToAction ?: getString(R.string.open)

        // Register the native ad view
        nativeAdView.setNativeAd(nativeAd)
        AdLoadingShimmerHelper.showNativeContent(nativeAdContainer, nativeAdView)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentNativeAd?.destroy()
        currentNativeAd = null
    }
}
