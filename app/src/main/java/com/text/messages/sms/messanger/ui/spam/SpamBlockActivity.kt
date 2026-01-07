package com.text.messages.sms.messanger.ui.spam

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivitySpamBlockBinding
import com.text.messages.sms.messanger.ui.blocking.BlockedConversationsActivity
import com.text.messages.sms.messanger.ui.blocking.CustomBlockingActivity
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class SpamBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpamBlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("SpamBlockActivity", "SpamBlockActivity")
        
        enableEdgeToEdge()
        binding = ActivitySpamBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupOptions()
        setupBannerAd()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupOptions() {
        binding.cardKeywords.setOnClickListener {
            val intent = Intent(this, CustomBlockingActivity::class.java)
            intent.putExtra("from_numbers", false)
            startActivity(intent)
        }
        
        binding.cardNumbers.setOnClickListener {
            val intent = Intent(this, CustomBlockingActivity::class.java)
            intent.putExtra("from_numbers", true)
            startActivity(intent)
        }
        
        binding.cardMessageBlocked.setOnClickListener {
            startActivity(Intent(this, BlockedConversationsActivity::class.java))
        }
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
}

