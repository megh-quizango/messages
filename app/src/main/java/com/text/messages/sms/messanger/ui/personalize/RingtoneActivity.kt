package com.text.messages.sms.messanger.ui.personalize

import android.content.BroadcastReceiver
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.enableEdgeToEdge
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityRingtoneBinding
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeTransitionAdManager

class RingtoneActivity : BaseActivity() {

    private lateinit var binding: ActivityRingtoneBinding
    private var selectedCard: View? = null
    private var selectedIcon: ImageView? = null
    private var themeChangeReceiver: BroadcastReceiver? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var currentPreviewRingtone: Ringtone? = null
    private var currentPreviewMediaPlayer: MediaPlayer? = null
    private var selectedRingtoneName: String = "default"
    
    companion object {
        private const val PREFS_NAME = "ringtone_settings"
        private const val KEY_SELECTED_RINGTONE = "selected_ringtone"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupBackButton()
        setupRingtoneSelection()
        setupSaveButton()
        loadSelectedRingtone()
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
        ThemeTransitionAdManager.preload(applicationContext)
    }
    
    override fun onPause() {
        super.onPause()
        stopPreviewSound()
    }
    
    override fun onStop() {
        super.onStop()
        stopPreviewSound()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopPreviewSound()
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
        // Map of ringtone cards to their names and icon IDs
        val ringtoneCards = mapOf(
            binding.cardSoundOff to Pair("sound_off", binding.iconSelectedSoundOff),
            binding.cardDefault to Pair("default", binding.iconSelectedDefault),
            binding.cardBling to Pair("bling", binding.iconSelectedBling),
            binding.cardAndroid to Pair("android", binding.iconSelectedAndroid),
            binding.cardWhistle to Pair("whistle", binding.iconSelectedWhistle),
            binding.cardTing to Pair("ting", binding.iconSelectedTing),
            binding.cardAlert to Pair("alert", binding.iconSelectedAlert),
            binding.cardInstruments to Pair("instruments", binding.iconSelectedInstruments),
            binding.cardSoul to Pair("soul", binding.iconSelectedSoul),
            binding.cardLove to Pair("love", binding.iconSelectedLove),
            binding.cardWater to Pair("water", binding.iconSelectedWater),
            binding.cardTick to Pair("tick", binding.iconSelectedTick),
            binding.cardWindChimes to Pair("wind_chimes", binding.iconSelectedWindChimes)
        )
        
        ringtoneCards.forEach { (card, ringtoneInfo) ->
            val (ringtoneName, icon) = ringtoneInfo
            card.setOnClickListener {
                // Stop previous preview sound
                stopPreviewSound()
                
                // Hide previous selection
                selectedIcon?.visibility = View.GONE
                
                // Show current selection
                icon.visibility = View.VISIBLE
                selectedCard = card
                selectedIcon = icon
                selectedRingtoneName = ringtoneName
                
                // Play preview sound
                playPreviewSound(ringtoneName)
            }
        }
    }

    private fun setupSaveButton() {
        binding.buttonSave.backgroundTintList = null
        binding.buttonSave.setOnClickListener {
            saveRingtonePreference(selectedRingtoneName)
            PersonalizationSaveAdNavigator.showAdThenFinish(this)
        }
    }
    
    private fun saveRingtonePreference(ringtoneName: String) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_RINGTONE, ringtoneName)
            .apply()
    }
    
    private fun loadSelectedRingtone() {
        val selectedRingtone = sharedPreferences.getString(KEY_SELECTED_RINGTONE, "default") ?: "default"
        
        // Map ringtone names to their corresponding cards and icons
        val ringtoneMap = mapOf(
            "sound_off" to Pair(binding.cardSoundOff, binding.iconSelectedSoundOff),
            "default" to Pair(binding.cardDefault, binding.iconSelectedDefault),
            "bling" to Pair(binding.cardBling, binding.iconSelectedBling),
            "android" to Pair(binding.cardAndroid, binding.iconSelectedAndroid),
            "whistle" to Pair(binding.cardWhistle, binding.iconSelectedWhistle),
            "ting" to Pair(binding.cardTing, binding.iconSelectedTing),
            "alert" to Pair(binding.cardAlert, binding.iconSelectedAlert),
            "instruments" to Pair(binding.cardInstruments, binding.iconSelectedInstruments),
            "soul" to Pair(binding.cardSoul, binding.iconSelectedSoul),
            "love" to Pair(binding.cardLove, binding.iconSelectedLove),
            "water" to Pair(binding.cardWater, binding.iconSelectedWater),
            "tick" to Pair(binding.cardTick, binding.iconSelectedTick),
            "wind_chimes" to Pair(binding.cardWindChimes, binding.iconSelectedWindChimes)
        )
        
        ringtoneMap[selectedRingtone]?.let { (card, icon) ->
            selectedCard = card
            selectedIcon = icon
            selectedRingtoneName = selectedRingtone
            icon.visibility = View.VISIBLE
        } ?: run {
            // Default to "default" if saved preference is invalid
            binding.iconSelectedDefault.visibility = View.VISIBLE
            selectedCard = binding.cardDefault
            selectedIcon = binding.iconSelectedDefault
            selectedRingtoneName = "default"
        }
    }
    
    private fun playPreviewSound(ringtoneName: String) {
        // Don't play sound for "sound_off"
        if (ringtoneName == "sound_off") {
            android.util.Log.d("RingtoneActivity", "Sound off selected, not playing")
            return
        }
        
        try {
            val soundUri = getNotificationSoundUri(ringtoneName)
            android.util.Log.d("RingtoneActivity", "Playing preview for $ringtoneName, URI: $soundUri")
            
            if (soundUri != null && soundUri != Uri.EMPTY) {
                // Stop any currently playing ringtone
                stopPreviewSound()
                
                // Use MediaPlayer for more reliable playback
                playWithMediaPlayer(soundUri)
            } else {
                android.util.Log.w("RingtoneActivity", "Invalid sound URI: $soundUri")
            }
        } catch (e: Exception) {
            android.util.Log.e("RingtoneActivity", "Error playing preview sound", e)
            e.printStackTrace()
        }
    }
    
    private fun playWithMediaPlayer(uri: Uri) {
        try {
            val mediaPlayer = MediaPlayer()
            
            // Set audio attributes for API 21+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                mediaPlayer.setAudioAttributes(audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
            }
            
            mediaPlayer.setDataSource(this, uri)
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener { mp ->
                try {
                    if (currentPreviewMediaPlayer == mp) {
                        currentPreviewMediaPlayer = null
                    }
                    mp.release()
                    android.util.Log.d("RingtoneActivity", "MediaPlayer completed and released")
                } catch (e: Exception) {
                    android.util.Log.w("RingtoneActivity", "Error in completion listener", e)
                }
            }
            mediaPlayer.setOnErrorListener { mp, what, extra ->
                android.util.Log.e("RingtoneActivity", "MediaPlayer error: what=$what, extra=$extra")
                try {
                    if (currentPreviewMediaPlayer == mp) {
                        currentPreviewMediaPlayer = null
                    }
                    mp.release()
                } catch (e: Exception) {
                    android.util.Log.w("RingtoneActivity", "Error releasing MediaPlayer on error", e)
                }
                true // Error handled
            }
            mediaPlayer.start()
            android.util.Log.d("RingtoneActivity", "MediaPlayer started playing")
            
            // Store reference to stop if needed
            currentPreviewMediaPlayer = mediaPlayer
        } catch (e: Exception) {
            android.util.Log.e("RingtoneActivity", "Error playing with MediaPlayer, trying Ringtone", e)
            // Fallback to Ringtone
            try {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                if (ringtone != null) {
                    // Set audio attributes for API 26+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        ringtone.audioAttributes = audioAttributes
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        @Suppress("DEPRECATION")
                        ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION)
                    }
                    
                    currentPreviewRingtone = ringtone
                    ringtone.play()
                    android.util.Log.d("RingtoneActivity", "Ringtone started playing (fallback)")
                }
            } catch (e2: Exception) {
                android.util.Log.e("RingtoneActivity", "Error with Ringtone fallback", e2)
            }
        }
    }
    
    private fun stopPreviewSound() {
        // Stop MediaPlayer if playing
        currentPreviewMediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                android.util.Log.w("RingtoneActivity", "Error stopping MediaPlayer", e)
            }
            currentPreviewMediaPlayer = null
        }
        
        // Stop Ringtone if playing
        currentPreviewRingtone?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {
                android.util.Log.w("RingtoneActivity", "Error stopping Ringtone", e)
            }
            currentPreviewRingtone = null
        }
    }
    
    /**
     * Maps ringtone name to Android system notification sound URI
     * Returns null for default system notification sound
     * Returns Uri.EMPTY for sound_off (silent)
     */
    private fun getNotificationSoundUri(ringtoneName: String): Uri? {
        return when (ringtoneName) {
            "sound_off" -> Uri.EMPTY // Silent - no sound
            "default" -> {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                android.util.Log.d("RingtoneActivity", "Default notification URI: $defaultUri")
                defaultUri
            }
            else -> {
                // For all other ringtones, try to get a different notification sound from system
                try {
                    val ringtoneManager = RingtoneManager(this)
                    ringtoneManager.setType(RingtoneManager.TYPE_NOTIFICATION)
                    
                    // Get available notification sounds
                    val cursor = ringtoneManager.cursor
                    val availableCount = cursor?.count ?: 0
                    android.util.Log.d("RingtoneActivity", "Available notification sounds: $availableCount")
                    
                    if (availableCount > 0) {
                        // Use a hash of the ringtone name to select a consistent sound
                        val index = Math.abs(ringtoneName.hashCode()) % availableCount
                        android.util.Log.d("RingtoneActivity", "Selected index $index for $ringtoneName")
                        
                        cursor?.let { c ->
                            try {
                                if (c.moveToPosition(index)) {
                                    val uri = ringtoneManager.getRingtoneUri(index)
                                    android.util.Log.d("RingtoneActivity", "Got URI for $ringtoneName: $uri")
                                    uri?.let { return it }
                                } else {
                                    android.util.Log.w("RingtoneActivity", "Failed to move cursor to position $index")
                                }
                            } finally {
                                // Cursor is managed by RingtoneManager, but we can close it if needed
                                // Note: Don't close it as RingtoneManager manages it
                            }
                        }
                    } else {
                        android.util.Log.w("RingtoneActivity", "No notification sounds available on device")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RingtoneActivity", "Error getting custom notification sound, using default", e)
                    e.printStackTrace()
                }
                // Fallback to default notification sound
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                android.util.Log.d("RingtoneActivity", "Using fallback default URI: $fallbackUri")
                fallbackUri
            }
        }
    }
}

