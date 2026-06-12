package com.text.messages.sms.messanger.ui.caller

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.text.messages.sms.messanger.BuildConfig
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityCallerSettingsBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.ui.conversation.EditQuickResponseActivity
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.CallStateTracker
import com.text.messages.sms.messanger.util.ThemeManager

class CallerSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityCallerSettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = CallStateTracker.PREFS_NAME
        private const val KEY_MISSED_CALL = CallStateTracker.KEY_MISSED_CALL
        private const val KEY_COMPLETED_CALL = CallStateTracker.KEY_COMPLETED_CALL
        private const val KEY_NO_ANSWER = CallStateTracker.KEY_NO_ANSWER
        private const val KEY_UNKNOWN_CALLER = CallStateTracker.KEY_UNKNOWN_CALLER
        private const val KEY_SHOW_CALL_INFO = CallStateTracker.KEY_SHOW_CALL_INFO
        private const val KEY_SHOW_REMINDERS = "show_reminders"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("CallerSettingsActivity", "CallerSettingsActivity")

        binding = ActivityCallerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        applyReferenceChrome()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.buttonBack.setOnClickListener { finish() }
        setupNavigationRows()
        setupSwitches()
        updatePermissionWarning()
    }

    override fun onResume() {
        super.onResume()
        applyReferenceChrome()
        updatePermissionWarning()
    }

    private fun setupNavigationRows() {
        binding.callerCadThemeMain.setOnClickListener {
            startActivity(Intent(this, CallerThemeActivity::class.java))
        }
        binding.callerCadDarkMode.setOnClickListener {
            startActivity(Intent(this, CallerThemeActivity::class.java))
        }
        binding.customizeInsideView.setOnClickListener {
            startActivity(Intent(this, CallerThemeActivity::class.java))
        }
        binding.quickRepliesView.setOnClickListener {
            startActivity(Intent(this, EditQuickResponseActivity::class.java))
        }
        binding.licenses.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.callercad_licenses)
                .setMessage("Open source licenses are available in the app dependencies and Android system components used by #Messages.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.sdkversion.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.callercad_version)
                .setMessage("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.contactPermission.setOnClickListener { openAppSettings() }
    }

    private fun setupSwitches() {
        bindSwitch(binding.switchNotificationReminder, KEY_SHOW_REMINDERS, true)
        bindSwitch(binding.switchMissedCall, KEY_MISSED_CALL, true)
        bindSwitch(binding.switchCompletedCall, KEY_COMPLETED_CALL, true)
        bindSwitch(binding.switchNoAnswer, KEY_NO_ANSWER, true)
        bindSwitch(binding.switchUnknownCaller, KEY_UNKNOWN_CALLER, true)
        bindSwitch(binding.switchExtraContact, KEY_SHOW_CALL_INFO, false) {
            updatePermissionWarning()
        }
    }

    private fun bindSwitch(
        switch: SwitchMaterial,
        key: String,
        defaultValue: Boolean,
        afterChange: (() -> Unit)? = null
    ) {
        switch.isChecked = prefs.getBoolean(key, defaultValue)
        ThemeManager.applyToggleTheme(switch, this)
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
            afterChange?.invoke()
        }
    }

    private fun updatePermissionWarning() {
        val wantsContactInfo = prefs.getBoolean(KEY_SHOW_CALL_INFO, false)
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        binding.contactPermission.visibility =
            if (wantsContactInfo && !hasContactsPermission) View.VISIBLE else View.GONE
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun applyReferenceChrome() {
        binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.callercad_screen_bg_light))
        binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.callercad_actionbar_bg))
        binding.textHeading.setTextColor(ContextCompat.getColor(this, R.color.callercad_text_color_black))
        window.statusBarColor = ContextCompat.getColor(this, R.color.callercad_actionbar_bg)
        window.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}
