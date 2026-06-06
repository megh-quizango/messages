package com.text.messages.sms.messanger.ui.personalize

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
import android.widget.ImageView
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityBubbleBinding
import android.content.BroadcastReceiver
import android.content.res.ColorStateList
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeTransitionAdManager

class BubbleActivity : BaseActivity() {

    private lateinit var binding: ActivityBubbleBinding
    private var selectedColorCircle: View? = null
    private var selectedIcon: ImageView? = null
    private var selectedColor: String = "#B3E5FC"
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    // Color values for each circle
    private val colors = listOf(
        "#B3E5FC", "#CE93D8", "#C5E1A5", "#FFCC80",
        "#FFF59D", "#E0E0E0", "#90CAF9", "#F48FB1",
        "#A5D6A7", "#FFAB91", "#FFF176", "#B0BEC5"
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityBubbleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        applyBubbleScreenChrome()
        binding.root.post { applyBubbleScreenChrome() }
        
        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupColorSelection()
        setupApplyButton()
        
        restoreSelectedColor()
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
        ThemeTransitionAdManager.preload(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        applyBubbleScreenChrome()
        binding.root.post { applyBubbleScreenChrome() }
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
            findViewById<View>(R.id.colorCircle1),
            findViewById<View>(R.id.colorCircle2),
            findViewById<View>(R.id.colorCircle3),
            findViewById<View>(R.id.colorCircle4),
            findViewById<View>(R.id.colorCircle5),
            findViewById<View>(R.id.colorCircle6),
            findViewById<View>(R.id.colorCircle7),
            findViewById<View>(R.id.colorCircle8),
            findViewById<View>(R.id.colorCircle9),
            findViewById<View>(R.id.colorCircle10),
            findViewById<View>(R.id.colorCircle11),
            findViewById<View>(R.id.colorCircle12)
        )
        
        colorCircles.forEachIndexed { index, circle ->
            circle.findViewById<View>(R.id.viewColor)?.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(colors[index]))
            circle.setOnClickListener {
                selectColor(circle, colors[index])
            }
        }
    }

    private fun restoreSelectedColor() {
        val savedColor = AppPreferences.getBubbleColor(this)
        val selectedIndex = colors.indexOfFirst { it.equals(savedColor, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: 0
        findViewById<View>(
            listOf(
                R.id.colorCircle1,
                R.id.colorCircle2,
                R.id.colorCircle3,
                R.id.colorCircle4,
                R.id.colorCircle5,
                R.id.colorCircle6,
                R.id.colorCircle7,
                R.id.colorCircle8,
                R.id.colorCircle9,
                R.id.colorCircle10,
                R.id.colorCircle11,
                R.id.colorCircle12
            )[selectedIndex]
        )?.performClick()
    }

    private fun selectColor(circle: View, colorHex: String) {
        selectedIcon?.visibility = View.GONE

        val icon = circle.findViewById<ImageView>(R.id.imgCheck)
        icon.visibility = View.VISIBLE
        selectedColorCircle = circle
        selectedIcon = icon
        selectedColor = colorHex
        updateSentBubbleColor(selectedColor)
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
        binding.buttonApply.setTextColor(Color.WHITE)
        
        binding.buttonApply.setOnClickListener {
            // Save bubble color preference
            AppPreferences.setBubbleColor(this, selectedColor)
            
            // Broadcast bubble color change
            sendBroadcast(android.content.Intent("com.text.messages.sms.messanger.BUBBLE_COLOR_CHANGED"))
            
            PersonalizationSaveAdNavigator.showAdThenFinish(this)
        }
    }

    private fun applyBubbleScreenChrome() {
        val headingColor = Color.parseColor("#111111")
        binding.textHeading.text = getString(R.string.gen_activity_bubble_text_2)
        binding.textHeading.setTextColor(headingColor)
        binding.buttonBack.imageTintList = ColorStateList.valueOf(headingColor)
        binding.buttonApply.backgroundTintList = null
        binding.buttonApply.setTextColor(Color.WHITE)
    }
}

