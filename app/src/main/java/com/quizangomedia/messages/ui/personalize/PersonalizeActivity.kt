package com.quizangomedia.messages.ui.personalize

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.material.card.MaterialCardView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityPersonalizeBinding
import com.quizangomedia.messages.ui.contacts.ContactsActivity
import com.quizangomedia.messages.ui.main.MainActivity
import com.quizangomedia.messages.ui.settings.SettingsActivity

class PersonalizeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalizeBinding
    private var isSettingSelectedItem = false
    private var selectedTheme: MaterialCardView? = null
    private var selectedFont: MaterialCardView? = null
    private var selectedBubble: MaterialCardView? = null
    private var selectedRingtone: MaterialCardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityPersonalizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle window insets - same as MainActivity
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to root so ad view stays above system navigation
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Bottom navigation should not have extra padding from window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { view, insets ->
            // Don't add padding - we want it to be exactly the size of its content
            insets
        }
        
        // Fix bottom navigation padding
        binding.bottomNavigationView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.bottomNavigationView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                val topPadding = binding.bottomNavigationView.paddingTop
                val bottomPadding = binding.bottomNavigationView.paddingBottom
                binding.bottomNavigationView.setPadding(0, topPadding, 0, bottomPadding)
                binding.bottomNavigationView.minimumHeight = 0
                
                val menuView = binding.bottomNavigationView.getChildAt(0) as? ViewGroup
                menuView?.let {
                    it.setPadding(0, 0, 0, 0)
                    it.minimumHeight = 0
                    
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        child?.let { item ->
                            if (item is ViewGroup) {
                                item.setPadding(item.paddingLeft, 0, item.paddingRight, 0)
                                item.minimumHeight = 0
                            }
                        }
                    }
                }
            }
        })
        
        setupThemeSelection()
        setupFontSelection()
        setupBubbleSelection()
        setupRingtoneSelection()
        setupBottomNavigation()
        setupBannerAd()
        
        // Set Personalize as selected initially
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_personalize)
        }
    }
    
    private fun setupThemeSelection() {
        val themes = listOf(
            binding.cardTheme1,
            binding.cardTheme2,
            binding.cardTheme3
        )
        
        themes.forEachIndexed { index, card ->
            card.setOnClickListener {
                // Remove selection from previous
                selectedTheme?.strokeWidth = 0
                selectedTheme?.elevation = 2f
                
                // Select new theme
                card.strokeWidth = 4
                card.strokeColor = getColor(R.color.button_primary)
                card.elevation = 8f
                selectedTheme = card
                
                // TODO: Apply theme to chat activity
                // Save selected theme preference
            }
        }
        
        // Select first theme by default
        if (selectedTheme == null) {
            themes[0].performClick()
        }
    }
    
    private fun setupFontSelection() {
        val fonts = listOf(
            binding.cardFont1,
            binding.cardFont2
        )
        
        fonts.forEachIndexed { index, card ->
            card.setOnClickListener {
                // Remove selection from previous
                selectedFont?.strokeWidth = 0
                selectedFont?.elevation = 2f
                
                // Select new font
                card.strokeWidth = 4
                card.strokeColor = getColor(R.color.button_primary)
                card.elevation = 8f
                selectedFont = card
                
                // TODO: Apply font to chat activity
                // Save selected font preference
            }
        }
        
        // Select first font by default
        if (selectedFont == null) {
            fonts[0].performClick()
        }
    }
    
    private fun setupBubbleSelection() {
        val bubbles = listOf(
            binding.cardBubble1,
            binding.cardBubble2,
            binding.cardBubble3,
            binding.cardBubble4,
            binding.cardBubble5,
            binding.cardBubble6
        )
        
        bubbles.forEachIndexed { index, card ->
            card.setOnClickListener {
                // Remove selection from previous
                selectedBubble?.strokeWidth = 0
                selectedBubble?.elevation = 2f
                
                // Select new bubble
                card.strokeWidth = 4
                card.strokeColor = getColor(R.color.button_primary)
                card.elevation = 8f
                selectedBubble = card
                
                // TODO: Apply bubble style to chat activity
                // Save selected bubble preference
            }
        }
        
        // Select first bubble by default
        if (selectedBubble == null) {
            bubbles[0].performClick()
        }
    }
    
    private fun setupRingtoneSelection() {
        val ringtones = listOf(
            binding.cardRingtone1,
            binding.cardRingtone2,
            binding.cardRingtone3,
            binding.cardRingtone4
        )
        
        ringtones.forEachIndexed { index, card ->
            card.setOnClickListener {
                // Remove selection from previous
                selectedRingtone?.strokeWidth = 0
                selectedRingtone?.elevation = 2f
                
                // Select new ringtone
                card.strokeWidth = 4
                card.strokeColor = getColor(R.color.button_primary)
                card.elevation = 8f
                selectedRingtone = card
                
                // TODO: Apply ringtone for calls and messages
                // Save selected ringtone preference
            }
        }
        
        // Select first ringtone by default
        if (selectedRingtone == null) {
            ringtones[0].performClick()
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            // Ignore if we're programmatically setting the selected item
            if (isSettingSelectedItem) {
                return@setOnItemSelectedListener true
            }
            
            when (item.itemId) {
                R.id.nav_messages -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_contacts -> {
                    startActivity(Intent(this, ContactsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_personalize -> {
                    // Already on Personalize screen
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure Personalize tab is selected when activity is visible
        setSelectedNavigationItem(R.id.nav_personalize)
    }
    
    private fun setSelectedNavigationItem(itemId: Int) {
        if (binding.bottomNavigationView.selectedItemId != itemId) {
            isSettingSelectedItem = true
            binding.bottomNavigationView.selectedItemId = itemId
            binding.bottomNavigationView.post {
                isSettingSelectedItem = false
            }
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
}
