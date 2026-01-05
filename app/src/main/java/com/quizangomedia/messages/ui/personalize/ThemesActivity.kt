package com.quizangomedia.messages.ui.personalize

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityThemesBinding
import com.quizangomedia.messages.util.AppPreferences
import com.quizangomedia.messages.util.ThemeManager

class ThemesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemesBinding
    private var selectedCardId: Int? = null
    private var selectedIconId: Int? = null
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    // Theme colors mapping - will be initialized after binding
    private val themeColors: Map<Int, String> by lazy {
        mapOf(
            binding.cardThemeBlue.id to "#2196F3",
            binding.cardThemeGreen.id to "#4CAF50",
            binding.cardThemePink.id to "#E91E63",
            binding.cardThemeYellow.id to "#FFEB3B",
            binding.cardThemeOral.id to "#FF6B6B",
            binding.cardThemeNavyBlue.id to "#1976D2",
            binding.cardThemeCeruleanBlue.id to "#03A9F4",
            binding.cardThemeBlackBlue.id to "#000000",
            binding.cardThemePurple.id to "#9C27B0",
            binding.cardThemeTeal.id to "#009688",
            binding.cardThemeOrange.id to "#FF9800",
            binding.cardThemeRed.id to "#F44336"
        )
    }
    
    // Reverse mapping: color -> card ID
    private val colorToCardId: Map<String, Int> by lazy {
        themeColors.entries.associate { (cardId, color) -> color to cardId }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityThemesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupThemeSelection()
        
        // Load and restore previously selected theme
        restoreSelectedTheme()
        
        // Register receiver for theme changes
        registerThemeChangeReceiver()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        themeChangeReceiver?.let {
            unregisterReceiver(it)
        }
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupThemeSelection() {
        // List of all theme cards with their corresponding icon IDs
        val themeCards = listOf(
            Pair(binding.cardThemeBlue, binding.iconSelectedBlue),
            Pair(binding.cardThemeGreen, binding.iconSelectedGreen),
            Pair(binding.cardThemePink, binding.iconSelectedPink),
            Pair(binding.cardThemeYellow, binding.iconSelectedYellow),
            Pair(binding.cardThemeOral, binding.iconSelectedOral),
            Pair(binding.cardThemeNavyBlue, binding.iconSelectedNavyBlue),
            Pair(binding.cardThemeCeruleanBlue, binding.iconSelectedCeruleanBlue),
            Pair(binding.cardThemeBlackBlue, binding.iconSelectedBlackBlue),
            Pair(binding.cardThemePurple, binding.iconSelectedPurple),
            Pair(binding.cardThemeTeal, binding.iconSelectedTeal),
            Pair(binding.cardThemeOrange, binding.iconSelectedOrange),
            Pair(binding.cardThemeRed, binding.iconSelectedRed)
        )
        
        themeCards.forEach { (card, icon) ->
            card.setOnClickListener {
                // Hide previous selection
                selectedIconId?.let { previousIconId ->
                    findViewById<View>(previousIconId)?.visibility = View.GONE
                }
                
                // Show current selection
                icon.visibility = View.VISIBLE
                selectedCardId = card.id
                selectedIconId = icon.id
                
                // Save theme color
                val themeColor = themeColors[card.id] ?: AppPreferences.getThemeColor(this)
                AppPreferences.setThemeColor(this, themeColor)
                AppPreferences.setThemeColorLight(this, AppPreferences.getLighterColor(themeColor))
                
                // Apply theme immediately to this activity (multiple times to ensure it's applied)
                ThemeManager.applyTheme(this, binding.root)
                binding.root.post {
                    ThemeManager.applyTheme(this@ThemesActivity, binding.root)
                    binding.root.post {
                        ThemeManager.applyTheme(this@ThemesActivity, binding.root)
                    }
                }
                
                // Broadcast theme change to update entire app immediately
                val intent = android.content.Intent("com.quizangomedia.messages.THEME_CHANGED")
                intent.setPackage(packageName) // Ensure it's sent to this app only
                
                // Send broadcast multiple ways to ensure delivery
                sendBroadcast(intent)
                sendOrderedBroadcast(intent, null)
                
                // Also send with a small delay to catch any late listeners
                binding.root.postDelayed({
                    sendBroadcast(intent)
                }, 100)
            }
        }
    }
    
    private fun registerThemeChangeReceiver() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Re-apply theme when it changes
                ThemeManager.applyTheme(this@ThemesActivity, binding.root)
            }
        }
        registerReceiver(themeChangeReceiver, IntentFilter("com.quizangomedia.messages.THEME_CHANGED"), receiverFlags)
    }
    
    private fun restoreSelectedTheme() {
        // Get the currently saved theme color
        val savedThemeColor = AppPreferences.getThemeColor(this)
        
        // Find the card ID that corresponds to this color
        val cardId = colorToCardId[savedThemeColor]
        
        if (cardId != null) {
            // Find the corresponding icon
            val iconId = when (cardId) {
                binding.cardThemeBlue.id -> binding.iconSelectedBlue.id
                binding.cardThemeGreen.id -> binding.iconSelectedGreen.id
                binding.cardThemePink.id -> binding.iconSelectedPink.id
                binding.cardThemeYellow.id -> binding.iconSelectedYellow.id
                binding.cardThemeOral.id -> binding.iconSelectedOral.id
                binding.cardThemeNavyBlue.id -> binding.iconSelectedNavyBlue.id
                binding.cardThemeCeruleanBlue.id -> binding.iconSelectedCeruleanBlue.id
                binding.cardThemeBlackBlue.id -> binding.iconSelectedBlackBlue.id
                binding.cardThemePurple.id -> binding.iconSelectedPurple.id
                binding.cardThemeTeal.id -> binding.iconSelectedTeal.id
                binding.cardThemeOrange.id -> binding.iconSelectedOrange.id
                binding.cardThemeRed.id -> binding.iconSelectedRed.id
                else -> null
            }
            
            if (iconId != null) {
                // Hide all icons first
                listOf(
                    binding.iconSelectedBlue,
                    binding.iconSelectedGreen,
                    binding.iconSelectedPink,
                    binding.iconSelectedYellow,
                    binding.iconSelectedOral,
                    binding.iconSelectedNavyBlue,
                    binding.iconSelectedCeruleanBlue,
                    binding.iconSelectedBlackBlue,
                    binding.iconSelectedPurple,
                    binding.iconSelectedTeal,
                    binding.iconSelectedOrange,
                    binding.iconSelectedRed
                ).forEach { it.visibility = View.GONE }
                
                // Show the selected icon
                findViewById<View>(iconId)?.visibility = View.VISIBLE
                selectedCardId = cardId
                selectedIconId = iconId
            }
        } else {
            // If no saved theme or color doesn't match, default to Green
            binding.iconSelectedGreen.visibility = View.VISIBLE
            selectedCardId = binding.cardThemeGreen.id
            selectedIconId = binding.iconSelectedGreen.id
        }
    }
}

