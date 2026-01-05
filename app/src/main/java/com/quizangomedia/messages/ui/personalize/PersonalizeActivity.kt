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
import com.quizangomedia.messages.ui.personalize.BubbleActivity
import com.quizangomedia.messages.ui.personalize.FontSizeActivity
import com.quizangomedia.messages.ui.personalize.RingtoneActivity
import com.quizangomedia.messages.ui.personalize.ThemesActivity
import com.quizangomedia.messages.ui.settings.SettingsActivity
import com.quizangomedia.messages.util.ThemeManager
import com.quizangomedia.messages.util.ThemeChangeHelper
import android.content.BroadcastReceiver

class PersonalizeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalizeBinding
    private var isSettingSelectedItem = false
    private var themeChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityPersonalizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
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
        
        setupThemeSection()
        setupFontSection()
        setupBubbleSection()
        setupRingtoneSection()
        setupBottomNavigation()
        setupBannerAd()
        setupThemeMoreButton()
        setupFontMoreButton()
        setupBubbleMoreButton()
        setupRingtoneMoreButton()
        
        // Set Personalize as selected initially
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_personalize)
        }
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
    }
    
    private fun setupThemeSection() {
        // Make entire theme section clickable (same as More text)
        binding.layoutThemeSection.setOnClickListener {
            val intent = Intent(this, ThemesActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupFontSection() {
        // Make entire font section clickable (same as More text)
        binding.layoutFontSection.setOnClickListener {
            val intent = Intent(this, FontSizeActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupBubbleSection() {
        // Make entire bubble section clickable (same as More text)
        binding.layoutBubbleSection.setOnClickListener {
            val intent = Intent(this, BubbleActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupRingtoneSection() {
        // Make entire ringtone section clickable (same as More text)
        binding.layoutRingtoneSection.setOnClickListener {
            val intent = Intent(this, RingtoneActivity::class.java)
            startActivity(intent)
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
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_personalize)
        }
    }
    
    private fun setSelectedNavigationItem(itemId: Int) {
        isSettingSelectedItem = true
        
        // First, uncheck all menu items
        for (i in 0 until binding.bottomNavigationView.menu.size()) {
            binding.bottomNavigationView.menu.getItem(i).isChecked = false
        }
        
        // Then check the selected item
        binding.bottomNavigationView.menu.findItem(itemId)?.isChecked = true
        binding.bottomNavigationView.selectedItemId = itemId
        
        // Force refresh
        binding.bottomNavigationView.invalidate()
        binding.bottomNavigationView.post {
            // Force refresh after layout
            binding.bottomNavigationView.invalidate()
            binding.bottomNavigationView.postDelayed({
                isSettingSelectedItem = false
                // One more refresh to ensure tint is applied
                binding.bottomNavigationView.invalidate()
            }, 50)
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
    
    private fun setupThemeMoreButton() {
        binding.textThemeMore.setOnClickListener {
            val intent = Intent(this, ThemesActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupFontMoreButton() {
        binding.textFontMore.setOnClickListener {
            val intent = Intent(this, FontSizeActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupBubbleMoreButton() {
        binding.textBubbleMore.setOnClickListener {
            val intent = Intent(this, BubbleActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupRingtoneMoreButton() {
        binding.textRingtoneMore.setOnClickListener {
            val intent = Intent(this, RingtoneActivity::class.java)
            startActivity(intent)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        themeChangeReceiver?.let {
            unregisterReceiver(it)
        }
    }
}
