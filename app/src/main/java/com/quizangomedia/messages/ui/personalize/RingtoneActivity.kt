package com.quizangomedia.messages.ui.personalize

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityRingtoneBinding
import android.content.BroadcastReceiver
import com.quizangomedia.messages.util.ThemeChangeHelper
import com.quizangomedia.messages.util.ThemeManager

class RingtoneActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRingtoneBinding
    private var selectedCard: View? = null
    private var selectedIcon: ImageView? = null
    private var themeChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRingtoneBinding.inflate(layoutInflater)
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
        setupRingtoneSelection()
        
        // Select Default by default (as shown in image)
        binding.cardDefault.performClick()
        
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
    
    private fun setupRingtoneSelection() {
        // List of all ringtone cards with their corresponding icon IDs
        val ringtoneCards = listOf(
            Pair(binding.cardSoundOff, binding.iconSelectedSoundOff),
            Pair(binding.cardDefault, binding.iconSelectedDefault),
            Pair(binding.cardBling, binding.iconSelectedBling),
            Pair(binding.cardAndroid, binding.iconSelectedAndroid),
            Pair(binding.cardWhistle, binding.iconSelectedWhistle),
            Pair(binding.cardTing, binding.iconSelectedTing),
            Pair(binding.cardAlert, binding.iconSelectedAlert),
            Pair(binding.cardInstruments, binding.iconSelectedInstruments),
            Pair(binding.cardSoul, binding.iconSelectedSoul),
            Pair(binding.cardLove, binding.iconSelectedLove),
            Pair(binding.cardWater, binding.iconSelectedWater),
            Pair(binding.cardTick, binding.iconSelectedTick),
            Pair(binding.cardWindChimes, binding.iconSelectedWindChimes)
        )
        
        ringtoneCards.forEach { (card, icon) ->
            card.setOnClickListener {
                // Hide previous selection
                selectedIcon?.visibility = View.GONE
                
                // Show current selection
                icon.visibility = View.VISIBLE
                selectedCard = card
                selectedIcon = icon
                
                // TODO: Apply ringtone preference
                // Save selected ringtone
            }
        }
    }
}

