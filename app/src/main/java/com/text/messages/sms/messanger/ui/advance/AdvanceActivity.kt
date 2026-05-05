package com.text.messages.sms.messanger.ui.advance

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityAdvanceBinding
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class AdvanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAdvanceBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: AdvanceOptionAdapter

    companion object {
        private const val PREFS_NAME = "advance_settings"
        private const val KEY_DELAY = "delay_seconds"
        private const val KEY_DELIVERY_CONFIRMATIONS = "delivery_confirmations"
        private const val KEY_STRIP_ACCENTS = "strip_accents"
        private const val KEY_MOBILE_NUMBER_ONLY = "mobile_number_only"
        private const val KEY_SEND_LONG_AS_MMS = "send_long_as_mms"
        
        enum class DelayOption(val displayName: String, val seconds: Int) {
            NO_DELAY("No delay", 0),
            THREE_SECONDS("3 seconds", 3),
            FIVE_SECONDS("5 seconds", 5)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("AdvanceActivity", "AdvanceActivity")
        
        binding = ActivityAdvanceBinding.inflate(layoutInflater)
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
        setupRecyclerView()
        setupBannerAd()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        val delaySeconds = prefs.getInt(KEY_DELAY, 0)
        val delayOption = DelayOption.values().find { it.seconds == delaySeconds } ?: DelayOption.NO_DELAY
        
        val options = listOf(
            AdvanceOption(
                type = AdvanceOptionType.DELAYED_SENDING,
                iconRes = R.drawable.delay,
                title = "Delayed sending",
                detail = delayOption.displayName
            ),
            AdvanceOption(
                type = AdvanceOptionType.DELETE_OLD_MESSAGES,
                iconRes = R.drawable.delete_old,
                title = "Delete old messages automatically",
                detail = null
            ),
            AdvanceOption(
                type = AdvanceOptionType.DELIVERY_CONFIRMATIONS,
                iconRes = R.drawable.delivery,
                title = "Delivery confirmations",
                detail = "Confirm that messages were sent successfully",
                hasToggle = true,
                toggleState = prefs.getBoolean(KEY_DELIVERY_CONFIRMATIONS, false)
            ),
            AdvanceOption(
                type = AdvanceOptionType.STRIP_ACCENTS,
                iconRes = R.drawable.accents,
                title = "Strip accents",
                detail = "Remove accents from characters in outgoing SMS messages",
                hasToggle = true,
                toggleState = prefs.getBoolean(KEY_STRIP_ACCENTS, false)
            ),
            AdvanceOption(
                type = AdvanceOptionType.MOBILE_NUMBER_ONLY,
                iconRes = R.drawable.number,
                title = "Mobile number only",
                detail = "When composing a message, only show mobile numbers",
                hasToggle = true,
                toggleState = prefs.getBoolean(KEY_MOBILE_NUMBER_ONLY, false)
            ),
            AdvanceOption(
                type = AdvanceOptionType.SEND_LONG_AS_MMS,
                iconRes = R.drawable.long_sms,
                title = "Send long messages as MMS",
                detail = "If your longer text messages are failing to send, or sending in the wrong order, you can send them as MMS messages instead. Additional charges may apply",
                hasToggle = true,
                toggleState = prefs.getBoolean(KEY_SEND_LONG_AS_MMS, false)
            )
        )
        
        adapter = AdvanceOptionAdapter(options) { option ->
            when (option.type) {
                AdvanceOptionType.DELAYED_SENDING -> {
                    showDelaySelectionDialog()
                }
                AdvanceOptionType.DELETE_OLD_MESSAGES -> {
                    // TODO: Implement delete old messages functionality
                }
                AdvanceOptionType.DELIVERY_CONFIRMATIONS -> {
                    // Toggle is handled in adapter
                }
                AdvanceOptionType.STRIP_ACCENTS -> {
                    // Toggle is handled in adapter
                }
                AdvanceOptionType.MOBILE_NUMBER_ONLY -> {
                    // Toggle is handled in adapter
                }
                AdvanceOptionType.SEND_LONG_AS_MMS -> {
                    // Toggle is handled in adapter
                }
            }
        }
        
        adapter.setOnToggleChangedListener { optionType, isChecked ->
            when (optionType) {
                AdvanceOptionType.DELIVERY_CONFIRMATIONS -> {
                    prefs.edit().putBoolean(KEY_DELIVERY_CONFIRMATIONS, isChecked).apply()
                }
                AdvanceOptionType.STRIP_ACCENTS -> {
                    prefs.edit().putBoolean(KEY_STRIP_ACCENTS, isChecked).apply()
                }
                AdvanceOptionType.MOBILE_NUMBER_ONLY -> {
                    prefs.edit().putBoolean(KEY_MOBILE_NUMBER_ONLY, isChecked).apply()
                }
                AdvanceOptionType.SEND_LONG_AS_MMS -> {
                    prefs.edit().putBoolean(KEY_SEND_LONG_AS_MMS, isChecked).apply()
                }
                else -> {}
            }
        }
        
        binding.recyclerViewOptions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewOptions.adapter = adapter
        adapter.submitList(options)
    }
    
    private fun showDelaySelectionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delay_selection, null)
        val radioNoDelay = dialogView.findViewById<RadioButton>(R.id.radioNoDelay)
        val radioThreeSeconds = dialogView.findViewById<RadioButton>(R.id.radioThreeSeconds)
        val radioFiveSeconds = dialogView.findViewById<RadioButton>(R.id.radioFiveSeconds)
        
        // Get current selection
        val currentDelay = prefs.getInt(KEY_DELAY, 0)
        when (currentDelay) {
            0 -> radioNoDelay.isChecked = true
            3 -> radioThreeSeconds.isChecked = true
            5 -> radioFiveSeconds.isChecked = true
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Apply theme to dialog
        ThemeManager.applyTheme(this, dialogView)
        
        // Set window background to transparent to show rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        radioNoDelay.setOnClickListener {
            saveDelay(DelayOption.NO_DELAY)
            updateDelayDetail()
            dialog.dismiss()
        }
        
        radioThreeSeconds.setOnClickListener {
            saveDelay(DelayOption.THREE_SECONDS)
            updateDelayDetail()
            dialog.dismiss()
        }
        
        radioFiveSeconds.setOnClickListener {
            saveDelay(DelayOption.FIVE_SECONDS)
            updateDelayDetail()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveDelay(option: DelayOption) {
        prefs.edit().putInt(KEY_DELAY, option.seconds).apply()
    }
    
    private fun updateDelayDetail() {
        val delaySeconds = prefs.getInt(KEY_DELAY, 0)
        val delayOption = DelayOption.values().find { it.seconds == delaySeconds } ?: DelayOption.NO_DELAY
        
        val options = adapter.currentList.toMutableList()
        val index = options.indexOfFirst { it.type == AdvanceOptionType.DELAYED_SENDING }
        if (index >= 0) {
            options[index] = options[index].copy(detail = delayOption.displayName)
            adapter.submitList(options)
        }
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
}

