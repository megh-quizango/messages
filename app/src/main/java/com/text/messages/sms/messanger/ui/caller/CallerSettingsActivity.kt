package com.text.messages.sms.messanger.ui.caller

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
import androidx.annotation.StringRes
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityCallerSettingsBinding
import com.text.messages.sms.messanger.databinding.ItemCallerSettingBinding
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class CallerSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityCallerSettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "caller_settings"
        private const val KEY_MISSED_CALL = "missed_call"
        private const val KEY_COMPLETED_CALL = "completed_call"
        private const val KEY_NO_ANSWER = "no_answer"
        private const val KEY_UNKNOWN_CALLER = "unknown_caller"
        private const val KEY_SHOW_CALL_INFO = "show_call_info"
        private const val KEY_SHOW_REMINDERS = "show_reminders"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityCallerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupSettings()
        setupBannerAd()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupSettings() {
        // Helper function to setup a toggle with theme styling
        fun setupToggle(
            binding: ItemCallerSettingBinding,
            @StringRes titleResId: Int,
            @StringRes descriptionResId: Int?,
            key: String,
            defaultValue: Boolean,
            showDescription: Boolean = true
        ) {
            binding.textTitle.setText(titleResId)
            if (showDescription) {
                binding.textDescription.setText(descriptionResId ?: 0)
                binding.textDescription.visibility = View.VISIBLE
            } else {
                binding.textDescription.text = ""
                binding.textDescription.visibility = View.GONE
            }
            binding.switchToggle.visibility = View.VISIBLE
            binding.switchToggle.isChecked = prefs.getBoolean(key, defaultValue)
            binding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(key, isChecked).apply()
            }
            // Apply theme-based styling using utility function
            ThemeManager.applyToggleTheme(binding.switchToggle, this)
        }
        
        // Missed call
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemMissedCall.root),
            R.string.caller_settings_missed_call_title,
            R.string.caller_settings_missed_call_description,
            KEY_MISSED_CALL,
            true
        )
        
        // Completed call
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemCompletedCall.root),
            R.string.caller_settings_completed_call_title,
            R.string.caller_settings_completed_call_description,
            KEY_COMPLETED_CALL,
            true
        )
        
        // No answer
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemNoAnswer.root),
            R.string.caller_settings_no_answer_title,
            R.string.caller_settings_no_answer_description,
            KEY_NO_ANSWER,
            true
        )
        
        // Unknown caller
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemUnknownCaller.root),
            R.string.caller_settings_unknown_caller_title,
            R.string.caller_settings_unknown_caller_description,
            KEY_UNKNOWN_CALLER,
            true
        )
        
        // Show call info for contacts
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemShowCallInfo.root),
            R.string.caller_settings_show_call_info_title,
            null,
            KEY_SHOW_CALL_INFO,
            false,
            false
        )
        
        // Show reminders in notifications
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemShowReminders.root),
            R.string.caller_settings_show_reminders_title,
            null,
            KEY_SHOW_REMINDERS,
            true,
            false
        )
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
}

