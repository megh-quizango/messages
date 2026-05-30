package com.text.messages.sms.messanger.ui.personalize

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityFontSizeBinding
import android.content.BroadcastReceiver
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeTransitionAdManager

class FontSizeActivity : BaseActivity() {

    private lateinit var binding: ActivityFontSizeBinding
    private var selectedFontPill: TextView? = null
    private var currentFontSize: Float = 14f
    private var currentFontFamily: Typeface = Typeface.DEFAULT
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    // Font families for different pills
    private val fontFamilies = listOf(
        Typeface.DEFAULT,                    // Font 1 - Default
        Typeface.SANS_SERIF,                  // Font 2 - Sans Serif
        Typeface.SERIF,                       // Font 3 - Serif
        Typeface.MONOSPACE,                   // Font 4 - Monospace
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),  // Font 5 - Bold
        Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC), // Font 6 - Italic
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC), // Font 7 - Bold Italic
        Typeface.create(Typeface.SERIF, Typeface.BOLD),       // Font 8 - Serif Bold
        Typeface.create(Typeface.SERIF, Typeface.ITALIC),     // Font 9 - Serif Italic
        Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),   // Font 10 - Monospace Bold
        Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC), // Font 11 - Monospace Italic
        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)      // Font 12 - Default Bold
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityFontSizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupFontSizeSlider()
        setupFontSelection()
        setupApplyButton()
        
        // Select first font by default
        binding.pillFont1.performClick()
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
        ThemeTransitionAdManager.preload(applicationContext)
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
    
    private fun setupFontSizeSlider() {
        // Map slider progress (0-100) to font size (10sp - 24sp)
        binding.seekBarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Convert progress (0-100) to font size (10-24)
                    currentFontSize = 10f + (progress / 100f) * 14f
                    updateChatBubbleTextSize()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Set initial progress to 30 (which gives ~14.2sp)
        binding.seekBarFontSize.progress = 30
        currentFontSize = 14.2f
    }
    
    private fun setupFontSelection() {
        val fontPills = listOf(
            binding.pillFont1,
            binding.pillFont2,
            binding.pillFont3,
            binding.pillFont4,
            binding.pillFont5,
            binding.pillFont6,
            binding.pillFont7,
            binding.pillFont8,
            binding.pillFont9,
            binding.pillFont10,
            binding.pillFont11,
            binding.pillFont12
        )
        
        fontPills.forEachIndexed { index, pill ->
            // Set different font families for each pill
            pill.typeface = fontFamilies[index]
            
            pill.setOnClickListener {
                // Remove selection from previous
                selectedFontPill?.let { previousPill ->
                    previousPill.background = getDrawable(R.drawable.bg_font_pill)
                    previousPill.setTextColor(getColor(R.color.black))
                }
                
                // Select new font
                pill.background = getDrawable(R.drawable.bg_font_pill_selected)
                pill.setTextColor(getColor(R.color.white))
                selectedFontPill = pill
                currentFontFamily = fontFamilies[index]
                
                // Update chat bubble font
                updateChatBubbleFont()
            }
        }
    }
    
    private fun updateChatBubbleTextSize() {
        binding.textReceivedMessage.textSize = currentFontSize
        binding.textSentMessage.textSize = currentFontSize
    }
    
    private fun updateChatBubbleFont() {
        binding.textReceivedMessage.typeface = currentFontFamily
        binding.textSentMessage.typeface = currentFontFamily
    }
    
    private fun setupApplyButton() {
        // Set background tint to null to prevent Material Design from overriding the background
        binding.buttonApply.backgroundTintList = null
        
        binding.buttonApply.setOnClickListener {
            // Save font size and font family preferences
            AppPreferences.setFontSize(this, currentFontSize)
            AppPreferences.setFontFamily(this, fontFamilies.indexOf(currentFontFamily))
            
            // Broadcast font change
            sendBroadcast(android.content.Intent("com.text.messages.sms.messanger.FONT_CHANGED"))
            
            PersonalizationSaveAdNavigator.showAdThenFinish(this)
        }
    }
}

