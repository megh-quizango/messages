package com.text.messages.sms.messanger.ui.personalize

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityBubbleBinding
import android.content.BroadcastReceiver
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager

class BubbleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBubbleBinding
    private var selectedColorCircle: View? = null
    private var selectedIcon: ImageView? = null
    private var selectedColor: String = "#B3E5FC"
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    // Color values for each circle
    private val colors = listOf(
        "#B3E5FC",  // Light Blue
        "#CE93D8",  // Light Purple
        "#C5E1A5",  // Light Green
        "#FFCC80",  // Light Orange
        "#FFF59D",  // Light Yellow
        "#E0E0E0"   // Light Gray
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityBubbleBinding.inflate(layoutInflater)
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
        setupColorSelection()
        setupApplyButton()
        
        // Select first color by default
        binding.colorCircle1.performClick()
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
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
    
    private fun setupColorSelection() {
        val colorCircles = listOf(
            binding.colorCircle1,
            binding.colorCircle2,
            binding.colorCircle3,
            binding.colorCircle4,
            binding.colorCircle5,
            binding.colorCircle6
        )
        
        val icons = listOf(
            binding.iconSelected1,
            binding.iconSelected2,
            binding.iconSelected3,
            binding.iconSelected4,
            binding.iconSelected5,
            binding.iconSelected6
        )
        
        colorCircles.forEachIndexed { index, circle ->
            circle.setOnClickListener {
                // Hide previous selection
                selectedIcon?.visibility = View.GONE
                
                // Show current selection
                icons[index].visibility = View.VISIBLE
                selectedColorCircle = circle
                selectedIcon = icons[index]
                selectedColor = colors[index]
                
                // Update sent message bubble color
                updateSentBubbleColor(selectedColor)
            }
        }
    }
    
    private fun updateSentBubbleColor(colorHex: String) {
        // Create a new drawable with the selected color
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        val cornerRadius = 16f * resources.displayMetrics.density // Convert 16dp to pixels
        drawable.cornerRadius = cornerRadius
        drawable.setColor(Color.parseColor(colorHex))
        binding.textSentMessage.background = drawable
    }
    
    private fun setupApplyButton() {
        // Set background tint to null to prevent Material Design from overriding the background
        binding.buttonApply.backgroundTintList = null
        
        binding.buttonApply.setOnClickListener {
            // Save bubble color preference
            AppPreferences.setBubbleColor(this, selectedColor)
            
            // Broadcast bubble color change
            sendBroadcast(android.content.Intent("com.text.messages.sms.messanger.BUBBLE_COLOR_CHANGED"))
            
            finish()
        }
    }
}

