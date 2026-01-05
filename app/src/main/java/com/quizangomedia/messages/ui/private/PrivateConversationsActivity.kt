package com.quizangomedia.messages.ui.private

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.databinding.ActivityPrivateConversationsBinding
import com.quizangomedia.messages.util.ThemeManager

class PrivateConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateConversationsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityPrivateConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupIcons()
        setupBannerAd()
        showEmptyState()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupIcons() {
        binding.imageAdd.setOnClickListener {
            // TODO: Implement add private conversation
        }

        binding.imageSettings.setOnClickListener {
            // TODO: Implement settings
        }
    }

    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }

    private fun showEmptyState() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.recyclerViewConversations.visibility = View.GONE
    }
}
