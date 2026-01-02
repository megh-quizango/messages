package com.quizangomedia.messages.ui.caller

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityCallerSettingsBinding
import com.quizangomedia.messages.databinding.ItemCallerSettingBinding

class CallerSettingsActivity : AppCompatActivity() {

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
        super.onCreate(savedInstanceState)
        
        binding = ActivityCallerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
        // Missed call
        val missedCallBinding = ItemCallerSettingBinding.bind(binding.itemMissedCall.root)
        missedCallBinding.textTitle.text = "Missed call"
        missedCallBinding.textDescription.text = "After a missed call, get details and choose what to do with the contact information."
        missedCallBinding.switchToggle.isChecked = prefs.getBoolean(KEY_MISSED_CALL, true)
        missedCallBinding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_MISSED_CALL, isChecked).apply()
            // TODO: Implement missed call handling logic
        }
        
        // Completed call
        val completedCallBinding = ItemCallerSettingBinding.bind(binding.itemCompletedCall.root)
        completedCallBinding.textTitle.text = "Completed call"
        completedCallBinding.textDescription.text = "After a call ends, see details and choose what to do with the contact information."
        completedCallBinding.switchToggle.isChecked = prefs.getBoolean(KEY_COMPLETED_CALL, true)
        completedCallBinding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_COMPLETED_CALL, isChecked).apply()
            // TODO: Implement completed call handling logic
        }
        
        // No answer
        val noAnswerBinding = ItemCallerSettingBinding.bind(binding.itemNoAnswer.root)
        noAnswerBinding.textTitle.text = "No answer"
        noAnswerBinding.textDescription.text = "After a call goes unanswered, see details and choose what to do with the contact information."
        noAnswerBinding.switchToggle.isChecked = prefs.getBoolean(KEY_NO_ANSWER, true)
        noAnswerBinding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NO_ANSWER, isChecked).apply()
            // TODO: Implement no answer handling logic
        }
        
        // Unknown caller
        val unknownCallerBinding = ItemCallerSettingBinding.bind(binding.itemUnknownCaller.root)
        unknownCallerBinding.textTitle.text = "Unknown caller"
        unknownCallerBinding.textDescription.text = "After getting a call from an unknown number, view details and choose how to handle the contact information."
        unknownCallerBinding.switchToggle.isChecked = prefs.getBoolean(KEY_UNKNOWN_CALLER, true)
        unknownCallerBinding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_UNKNOWN_CALLER, isChecked).apply()
            // TODO: Implement unknown caller handling logic
        }
        
        // Show call info for contacts
        val showCallInfoBinding = ItemCallerSettingBinding.bind(binding.itemShowCallInfo.root)
        showCallInfoBinding.textTitle.text = "Show call info for contacts"
        showCallInfoBinding.textDescription.text = ""
        showCallInfoBinding.textDescription.visibility = android.view.View.GONE
        showCallInfoBinding.switchToggle.isChecked = prefs.getBoolean(KEY_SHOW_CALL_INFO, false)
        showCallInfoBinding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SHOW_CALL_INFO, isChecked).apply()
            // TODO: Implement show call info logic
        }
        
        // Show reminders in notifications
        val showRemindersBinding = ItemCallerSettingBinding.bind(binding.itemShowReminders.root)
        showRemindersBinding.textTitle.text = "Show reminders in notifications"
        showRemindersBinding.textDescription.text = ""
        showRemindersBinding.textDescription.visibility = android.view.View.GONE
        showRemindersBinding.switchToggle.isChecked = prefs.getBoolean(KEY_SHOW_REMINDERS, true)
        showRemindersBinding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SHOW_REMINDERS, isChecked).apply()
            // TODO: Implement show reminders logic
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
}

