package com.text.messages.sms.messanger.ui.notifications

import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.enableEdgeToEdge
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityNotificationsBinding
import com.text.messages.sms.messanger.databinding.DialogButtonActionBinding
import com.text.messages.sms.messanger.databinding.DialogNotificationPreviewBinding
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

enum class NotificationPreview(val displayName: String) {
    SHOW_NAME_AND_MESSAGE("Show name and message"),
    SHOW_NAME("Show Name"),
    HIDE_CONTENTS("Hide contents")
}

enum class ButtonAction(val displayName: String) {
    NONE("None"),
    ARCHIVE("Archive"),
    DELETE("Delete"),
    BLOCK("Block"),
    CALL("Call"),
    MARK_AS_READ("Mark as Read"),
    REPLY("Reply"),
    COPY_OTP("Copy OTP")
}

class NotificationsActivity : BaseActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: NotificationOptionAdapter

    companion object {
        private const val PREFS_NAME = "notifications_settings"
        private const val KEY_NOTIFICATION_PREVIEW = "notification_preview"
        private const val KEY_WAKE_SCREEN = "wake_screen"
        private const val KEY_BUTTON_1_ACTION = "button_1_action"
        private const val KEY_BUTTON_2_ACTION = "button_2_action"
        private const val KEY_BUTTON_3_ACTION = "button_3_action"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("NotificationsActivity", "NotificationsActivity")
        
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
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
        val options = listOf(
            NotificationOption(
                type = NotificationOptionType.NOTIFICATIONS,
                iconRes = R.drawable.noti,
                title = "Notifications",
                detail = "Tap to customize"
            ),
            NotificationOption(
                type = NotificationOptionType.NOTIFICATION_PREVIEWS,
                iconRes = R.drawable.preview,
                title = "Notification Previews",
                detail = getNotificationPreviewDetail()
            ),
            NotificationOption(
                type = NotificationOptionType.WAKE_SCREEN,
                iconRes = R.drawable.wake,
                title = "Wake Screen",
                detail = null,
                hasToggle = true,
                toggleState = prefs.getBoolean(KEY_WAKE_SCREEN, false)
            ),
            NotificationOption(
                type = NotificationOptionType.ACTIONS_HEADING,
                iconRes = null,
                title = "Actions",
                detail = null
            ),
            NotificationOption(
                type = NotificationOptionType.BUTTON_1,
                iconRes = R.drawable.button1,
                title = "Button 1",
                detail = getButtonActionDetail(KEY_BUTTON_1_ACTION)
            ),
            NotificationOption(
                type = NotificationOptionType.BUTTON_2,
                iconRes = R.drawable.button2,
                title = "Button 2",
                detail = getButtonActionDetail(KEY_BUTTON_2_ACTION)
            ),
            NotificationOption(
                type = NotificationOptionType.BUTTON_3,
                iconRes = R.drawable.button3,
                title = "Button 3",
                detail = getButtonActionDetail(KEY_BUTTON_3_ACTION)
            )
        )
        
        adapter = NotificationOptionAdapter(options) { option ->
            when (option.type) {
                NotificationOptionType.NOTIFICATIONS -> {
                    openDeviceNotificationSettings()
                }
                NotificationOptionType.NOTIFICATION_PREVIEWS -> {
                    showNotificationPreviewDialog()
                }
                NotificationOptionType.WAKE_SCREEN -> {
                    // Toggle is handled in adapter
                }
                NotificationOptionType.BUTTON_1 -> {
                    showButtonActionDialog(KEY_BUTTON_1_ACTION, "Button 1")
                }
                NotificationOptionType.BUTTON_2 -> {
                    showButtonActionDialog(KEY_BUTTON_2_ACTION, "Button 2")
                }
                NotificationOptionType.BUTTON_3 -> {
                    showButtonActionDialog(KEY_BUTTON_3_ACTION, "Button 3")
                }
                else -> {}
            }
        }
        
        adapter.setOnWakeScreenToggleChangedListener { isChecked ->
            onWakeScreenToggleChanged(isChecked)
        }
        
        binding.recyclerViewOptions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewOptions.adapter = adapter
        adapter.submitList(options)
    }
    
    private fun openDeviceNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to app settings if notification settings not available
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    private fun showNotificationPreviewDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notification_preview, null)
        val radioShowNameAndMessage = dialogView.findViewById<RadioButton>(R.id.radioShowNameAndMessage)
        val radioShowName = dialogView.findViewById<RadioButton>(R.id.radioShowName)
        val radioHideContents = dialogView.findViewById<RadioButton>(R.id.radioHideContents)
        
        // Get current selection
        val currentPreview = prefs.getString(KEY_NOTIFICATION_PREVIEW, NotificationPreview.SHOW_NAME_AND_MESSAGE.name)
        when (currentPreview) {
            NotificationPreview.SHOW_NAME_AND_MESSAGE.name -> radioShowNameAndMessage.isChecked = true
            NotificationPreview.SHOW_NAME.name -> radioShowName.isChecked = true
            NotificationPreview.HIDE_CONTENTS.name -> radioHideContents.isChecked = true
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Apply theme to dialog
        ThemeManager.applyThemeToDialog(this, dialogView)

        // Set window background to transparent to show rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Apply theme after dialog is shown
        dialog.setOnShowListener {
            ThemeManager.applyTheme(this, dialogView)
        }
        
        radioShowNameAndMessage.setOnClickListener {
            saveNotificationPreview(NotificationPreview.SHOW_NAME_AND_MESSAGE)
            updateNotificationPreviewDetail()
            dialog.dismiss()
        }
        
        radioShowName.setOnClickListener {
            saveNotificationPreview(NotificationPreview.SHOW_NAME)
            updateNotificationPreviewDetail()
            dialog.dismiss()
        }
        
        radioHideContents.setOnClickListener {
            saveNotificationPreview(NotificationPreview.HIDE_CONTENTS)
            updateNotificationPreviewDetail()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showButtonActionDialog(key: String, @Suppress("UNUSED_PARAMETER") buttonTitle: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_button_action, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewActions)
        
        val actions = ButtonAction.values().toList()
        val currentAction = prefs.getString(key, ButtonAction.NONE.name)
        val selectedAction = ButtonAction.values().find { it.name == currentAction } ?: ButtonAction.NONE
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Apply theme to dialog
        ThemeManager.applyThemeToDialog(this, dialogView)
        
        // Set window background to transparent to show rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Apply theme after dialog is shown
        dialog.setOnShowListener {
            ThemeManager.applyTheme(this, dialogView)
        }
        
        val actionAdapter = ButtonActionAdapter(actions, selectedAction) { _: ButtonAction ->
            // This is called when action is selected
        }
        
        actionAdapter.setOnActionSelectedListener { action: ButtonAction ->
            saveButtonAction(key, action)
            updateButtonActionDetail(key)
            dialog.dismiss()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = actionAdapter
        actionAdapter.submitList(actions) // Submit the list to populate the RecyclerView
        
        dialog.show()
    }
    
    private fun saveNotificationPreview(preview: NotificationPreview) {
        prefs.edit().putString(KEY_NOTIFICATION_PREVIEW, preview.name).apply()
    }
    
    private fun saveButtonAction(key: String, action: ButtonAction) {
        prefs.edit().putString(key, action.name).apply()
    }
    
    private fun getNotificationPreviewDetail(): String {
        val previewName = prefs.getString(KEY_NOTIFICATION_PREVIEW, NotificationPreview.SHOW_NAME_AND_MESSAGE.name)
        return NotificationPreview.values().find { it.name == previewName }?.displayName ?: NotificationPreview.SHOW_NAME_AND_MESSAGE.displayName
    }
    
    private fun getButtonActionDetail(key: String): String {
        val actionName = prefs.getString(key, ButtonAction.NONE.name)
        return ButtonAction.values().find { it.name == actionName }?.displayName ?: ButtonAction.NONE.displayName
    }
    
    private fun updateNotificationPreviewDetail() {
        val options = adapter.currentList.toMutableList()
        val index = options.indexOfFirst { it.type == NotificationOptionType.NOTIFICATION_PREVIEWS }
        if (index >= 0) {
            options[index] = options[index].copy(detail = getNotificationPreviewDetail())
            adapter.submitList(options)
        }
    }
    
    private fun updateButtonActionDetail(key: String) {
        val options = adapter.currentList.toMutableList()
        val optionType = when (key) {
            KEY_BUTTON_1_ACTION -> NotificationOptionType.BUTTON_1
            KEY_BUTTON_2_ACTION -> NotificationOptionType.BUTTON_2
            KEY_BUTTON_3_ACTION -> NotificationOptionType.BUTTON_3
            else -> return
        }
        val index = options.indexOfFirst { it.type == optionType }
        if (index >= 0) {
            options[index] = options[index].copy(detail = getButtonActionDetail(key))
            adapter.submitList(options)
        }
    }
    
    fun onWakeScreenToggleChanged(isChecked: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_SCREEN, isChecked).apply()
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
}

