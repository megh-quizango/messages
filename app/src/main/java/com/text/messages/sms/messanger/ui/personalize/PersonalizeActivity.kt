package com.text.messages.sms.messanger.ui.personalize

import android.content.BroadcastReceiver
import android.content.Context
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
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityPersonalizeBinding
import com.text.messages.sms.messanger.ui.contacts.ContactsActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.ui.personalize.BubbleActivity
import com.text.messages.sms.messanger.ui.personalize.FontSizeActivity
import com.text.messages.sms.messanger.ui.personalize.RingtoneActivity
import com.text.messages.sms.messanger.ui.personalize.ThemesActivity
import com.text.messages.sms.messanger.ui.settings.SettingsActivity
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class PersonalizeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalizeBinding
    private var isSettingSelectedItem = false
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var themeUpdateCallback: ((Context, View) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("PersonalizeActivity", "=== PersonalizeActivity.onCreate() ===")
        AnalyticsHelper.logScreenView("PersonalizeActivity", "PersonalizeActivity")
        
        binding = ActivityPersonalizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        android.util.Log.d("PersonalizeActivity", "PersonalizeActivity.onCreate(): Binding initialized")
        
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
        
        // Register theme change receiver with enhanced immediate updates
        val receiverFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        themeChangeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                // Apply theme IMMEDIATELY to all containers - multiple passes for reliability
                updatePersonalizeContainersTheme()
            }
        }
        
        // Also register for direct callback updates
        themeUpdateCallback = { ctx: Context, view: View ->
            if (ctx == this@PersonalizeActivity) {
                // Apply theme IMMEDIATELY to all containers
                updatePersonalizeContainersTheme()
            }
        }
        themeUpdateCallback?.let { ThemeManager.registerThemeUpdateCallback(it) }
        
        registerReceiver(
            themeChangeReceiver,
            android.content.IntentFilter("com.text.messages.sms.messanger.THEME_CHANGED"),
            receiverFlags
        )
    }
    
    /**
     * Update all PersonalizeActivity containers with current theme colors immediately
     * Called when theme changes to ensure containers reflect new colors instantly
     */
    private fun updatePersonalizeContainersTheme() {
        // Apply theme IMMEDIATELY to all containers - multiple passes
        ThemeManager.applyThemeImmediate(this, binding.root)
        ThemeManager.applyThemeImmediate(this, binding.layoutThemeSection)
        ThemeManager.applyThemeImmediate(this, binding.layoutFontSection)
        ThemeManager.applyThemeImmediate(this, binding.layoutBubbleSection)
        ThemeManager.applyThemeImmediate(this, binding.layoutRingtoneSection)
        
        // Force immediate invalidation and layout on all containers
        binding.layoutThemeSection.invalidate()
        binding.layoutThemeSection.requestLayout()
        binding.layoutFontSection.invalidate()
        binding.layoutFontSection.requestLayout()
        binding.layoutBubbleSection.invalidate()
        binding.layoutBubbleSection.requestLayout()
        binding.layoutRingtoneSection.invalidate()
        binding.layoutRingtoneSection.requestLayout()
        binding.root.invalidate()
        binding.root.requestLayout()
        
        // Also invalidate parent to force redraw
        (binding.layoutThemeSection.parent as? View)?.invalidate()
        (binding.layoutFontSection.parent as? View)?.invalidate()
        (binding.layoutBubbleSection.parent as? View)?.invalidate()
        (binding.layoutRingtoneSection.parent as? View)?.invalidate()
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
                    overridePendingTransition(0, 0)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_contacts -> {
                    overridePendingTransition(0, 0)
                    startActivity(Intent(this, ContactsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_personalize -> {
                    // Already on Personalize screen
                    true
                }
                R.id.nav_settings -> {
                    overridePendingTransition(0, 0)
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
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
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
        // Unregister theme callback
        themeUpdateCallback?.let {
            ThemeManager.unregisterThemeUpdateCallback(it)
        }
    }
}
