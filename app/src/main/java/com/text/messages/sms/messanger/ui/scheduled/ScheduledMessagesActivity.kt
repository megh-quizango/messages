package com.text.messages.sms.messanger.ui.scheduled

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.databinding.ActivityScheduledMessagesBinding
import com.text.messages.sms.messanger.ui.blocking.overlay.SingleContactSelectionActivity
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class ScheduledMessagesActivity : BaseActivity() {

    private lateinit var binding: ActivityScheduledMessagesBinding

    private val contactSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val phoneNumber = result.data?.getStringExtra("phone_number") ?: ""
            val contactName = result.data?.getStringExtra("contact_name") ?: ""
            
            if (phoneNumber.isNotEmpty()) {
                // Open conversation detail activity with scheduling intent
                val intent = Intent(this, com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity::class.java)
                intent.putExtra("address", phoneNumber)
                intent.putExtra("thread_id", 0L)
                intent.putExtra("contact_name", contactName)
                intent.putExtra("is_scheduling", true)
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("ScheduledMessagesActivity", "ScheduledMessagesActivity")
        
        enableEdgeToEdge()
        binding = ActivityScheduledMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply theme
        ThemeManager.applyTheme(this, binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBackButton()
        setupFAB()
        setupBannerAd()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupFAB() {
        binding.fabAddSchedule.setOnClickListener {
            val intent = Intent(this, SingleContactSelectionActivity::class.java)
            contactSelectionLauncher.launch(intent)
        }
    }

    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
}

