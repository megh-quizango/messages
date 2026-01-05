package com.quizangomedia.messages.ui.feedback

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityFeedbackBinding
import com.quizangomedia.messages.util.ThemeManager

class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private var selectedCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupCategoryTabs()
        setupTextInput()
        setupSubmitButton()
        setupBannerAd()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupCategoryTabs() {
        val tabs = listOf(
            binding.tabViewMessage,
            binding.tabBackup,
            binding.tabBugs,
            binding.tabSendMessage,
            binding.tabTooSlow,
            binding.tabOthers
        )
        
        tabs.forEach { tab ->
            tab.setOnClickListener {
                // Deselect all tabs
                tabs.forEach { t -> setTabSelected(t, false) }
                // Select clicked tab
                setTabSelected(tab, true)
                selectedCategory = tab.text.toString()
                updateSubmitButtonState()
            }
        }
    }
    
    private fun setTabSelected(tab: TextView, isSelected: Boolean) {
        val themeColor = ThemeManager.getThemeColor(this)
        val themeColorLight = ThemeManager.getThemeColorLight(this)
        
        if (isSelected) {
            // Create new drawable with theme color dynamically
            val selectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * resources.displayMetrics.density
                setColor(themeColor)
            }
            tab.background = selectedDrawable
            tab.setTextColor(getColor(R.color.white))
        } else {
            // Create new drawable with theme light color dynamically
            val unselectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * resources.displayMetrics.density
                setColor(themeColorLight)
            }
            tab.background = unselectedDrawable
            tab.setTextColor(getColor(R.color.black))
        }
    }
    
    private fun setupTextInput() {
        binding.editTextFeedback.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSubmitButtonState()
            }
        })
    }
    
    private fun setupSubmitButton() {
        // Set backgroundTint to null for submit button and apply theme color directly
        binding.buttonSubmit.backgroundTintList = null
        val themeColor = ThemeManager.getThemeColor(this)
        binding.buttonSubmit.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        binding.buttonSubmit.setOnClickListener {
            // TODO: Implement feedback submission
            val category = selectedCategory ?: ""
            val feedback = binding.editTextFeedback.text.toString()
            // Submit feedback
        }
        updateSubmitButtonState()
    }
    
    private fun updateSubmitButtonState() {
        val hasText = binding.editTextFeedback.text.toString().trim().isNotEmpty()
        val hasCategory = selectedCategory != null
        
        binding.buttonSubmit.isEnabled = hasText && hasCategory
        binding.buttonSubmit.alpha = if (hasText && hasCategory) 1f else 0.5f
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
}

