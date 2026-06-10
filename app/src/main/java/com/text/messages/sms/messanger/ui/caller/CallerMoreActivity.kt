package com.text.messages.sms.messanger.ui.caller

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity
import com.text.messages.sms.messanger.util.AfterCallSuggestedAppsRepository
import com.text.messages.sms.messanger.util.AppOpenManager
import com.text.messages.sms.messanger.util.CallAfterLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallerMoreActivity : BaseActivity() {

    companion object {
        private const val TAG = "CallerMoreActivity"
        private const val AFTER_CALL_APP_OPEN_SUPPRESSION_MS = 30_000L
    }

    private var callerNumber: String? = null
    private var callerName: String? = null
    private var suggestedApps: List<AfterCallSuggestedAppsRepository.SuggestedApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        AppOpenManager.suppressAppOpenFor(AFTER_CALL_APP_OPEN_SUPPRESSION_MS)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caller_more)

        callerNumber = intent.getStringExtra(CallAfterLauncher.EXTRA_CALLER_NUMBER)
            ?.replace(Regex("[^\\d+]"), "")
            ?.trim()
        callerName = intent.getStringExtra("CALLER_NAME")
            ?.takeIf { it.isNotBlank() && it != getString(R.string.unknown_number) }

        val root = findViewById<ConstraintLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.buttonBack).setOnClickListener { finish() }
        setupSuggestedApps()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        AppOpenManager.suppressAppOpenFor(AFTER_CALL_APP_OPEN_SUPPRESSION_MS)
    }

    private fun setupSuggestedApps() {
        lifecycleScope.launch {
            suggestedApps = withContext(Dispatchers.IO) {
                AfterCallSuggestedAppsRepository.getSuggestedApps(this@CallerMoreActivity)
            }
            bindSuggestedApp(
                app = suggestedApps.getOrNull(0),
                containerId = R.id.suggestedCalendar,
                iconId = R.id.imageSuggestedAppOne,
                titleId = R.id.textSuggestedAppOne
            )
            bindSuggestedApp(
                app = suggestedApps.getOrNull(1),
                containerId = R.id.suggestedGallery,
                iconId = R.id.imageSuggestedAppTwo,
                titleId = R.id.textSuggestedAppTwo
            )
        }
    }

    private fun bindSuggestedApp(
        app: AfterCallSuggestedAppsRepository.SuggestedApp?,
        containerId: Int,
        iconId: Int,
        titleId: Int
    ) {
        val container = findViewById<View>(containerId)
        val icon = findViewById<ImageView>(iconId)
        val title = findViewById<TextView>(titleId)
        if (app == null) {
            container.visibility = View.INVISIBLE
            container.setOnClickListener(null)
            return
        }
        container.visibility = View.VISIBLE
        title.text = app.name
        val bitmap = app.iconFile?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmap != null) {
            icon.setImageBitmap(bitmap)
        }
        container.setOnClickListener {
            startExternalActivity(
                AfterCallSuggestedAppsRepository.createLaunchIntent(this, app.packageId)
            )
        }
    }

    private fun setupActions() {
        bindAction(
            R.id.actionReminder,
            R.drawable.ic_after_call_bell,
            getString(R.string.callercad_create_new_reminder)
        ) { openCalendar() }
        bindAction(
            R.id.actionAddCaller,
            R.drawable.ic_person_add,
            getString(R.string.after_call_action_add_caller_contacts)
        ) { addToContacts() }
        bindAction(
            R.id.actionMessages,
            R.drawable.ic_chat_bubble,
            getString(R.string.after_call_action_messages)
        ) { openConversation() }
        bindAction(
            R.id.actionSendMail,
            R.drawable.ic_after_call_mail,
            getString(R.string.after_call_action_send_email)
        ) { sendEmail() }
        bindAction(
            R.id.actionCalendar,
            R.drawable.ic_after_call_calendar,
            getString(R.string.after_call_action_calendar)
        ) { openCalendar() }
        bindAction(
            R.id.actionWeb,
            R.drawable.ic_after_call_globe,
            getString(R.string.after_call_action_web)
        ) { openBrowser() }
        bindAction(
            R.id.actionCallInfo,
            R.drawable.caller_ic_settings,
            getString(R.string.after_call_action_call_information_settings)
        ) { startActivity(Intent(this, CallerSettingsActivity::class.java)) }
    }

    private fun bindAction(rowId: Int, iconRes: Int, title: String, onClick: () -> Unit) {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.imageActionIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.textActionTitle).text = title
        row.setOnClickListener { onClick() }
    }

    private fun addToContacts() {
        try {
            startActivity(
                Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.PHONE, "")
                    callerName?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to add caller", e)
            Toast.makeText(this, R.string.add_contact_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openConversation() {
        val number = callerNumber
        if (number.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                val threadId = withContext(Dispatchers.IO) {
                    Telephony.Threads.getOrCreateThreadId(this@CallerMoreActivity, number)
                }
                startActivity(
                    Intent(this@CallerMoreActivity, ConversationDetailActivity::class.java).apply {
                        putExtra("thread_id", threadId)
                        putExtra("address", number)
                        putExtra("contact_name", callerName ?: number)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open conversation", e)
                Toast.makeText(this@CallerMoreActivity, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendEmail() {
        val subjectTarget = callerName ?: callerNumber ?: getString(R.string.unknown_number)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.after_call_reminder_label, subjectTarget))
        }
        startExternalActivity(intent)
    }

    private fun openCalendar() {
        val labelTarget = callerName ?: callerNumber ?: getString(R.string.unknown_number)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, getString(R.string.after_call_reminder_label, labelTarget))
        }
        startExternalActivity(intent)
    }

    private fun openBrowser() {
        val query = callerName ?: callerNumber
        val uri = if (query.isNullOrBlank()) {
            Uri.parse("https://www.google.com/")
        } else {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        }
        startExternalActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun startExternalActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open action", e)
            Toast.makeText(this, R.string.after_call_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
