package com.text.messages.sms.messanger.ui.caller

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
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
        fun setupToggle(binding: ItemCallerSettingBinding, title: String, description: String, key: String, defaultValue: Boolean, showDescription: Boolean = true) {
            binding.textTitle.text = title
            if (showDescription) {
                binding.textDescription.text = description
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
            "Missed call",
            "After a missed call, get details and choose what to do with the contact information.",
            KEY_MISSED_CALL,
            true
        )
        
        // Completed call
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemCompletedCall.root),
            "Completed call",
            "After a call ends, see details and choose what to do with the contact information.",
            KEY_COMPLETED_CALL,
            true
        )
        
        // No answer
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemNoAnswer.root),
            "No answer",
            "After a call goes unanswered, see details and choose what to do with the contact information.",
            KEY_NO_ANSWER,
            true
        )
        
        // Unknown caller
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemUnknownCaller.root),
            "Unknown caller",
            "After getting a call from an unknown number, view details and choose how to handle the contact information.",
            KEY_UNKNOWN_CALLER,
            true
        )
        
        // Show call info for contacts
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemShowCallInfo.root),
            "Show call info for contacts",
            "",
            KEY_SHOW_CALL_INFO,
            false,
            false
        )
        
        // Show reminders in notifications
        setupToggle(
            ItemCallerSettingBinding.bind(binding.itemShowReminders.root),
            "Show reminders in notifications",
            "",
            KEY_SHOW_REMINDERS,
            true,
            false
        )
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
}

