package com.text.messages.sms.messanger.ui.personalize

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityRingtoneBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.RingtoneSoundResolver
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeTransitionAdManager

class RingtoneActivity : BaseActivity() {

    private lateinit var binding: ActivityRingtoneBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var ringtoneAdapter: RingtoneAdapter
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var currentPreviewRingtone: Ringtone? = null
    private var currentPreviewMediaPlayer: MediaPlayer? = null
    private var selectedRingtoneName: String = RingtoneSoundResolver.DEFAULT

    private val systemRingtoneLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val pickedUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                pickedUri?.let { applyPickedUri(RingtoneSoundResolver.SYSTEM, it) }
            }
        }

    private val customRingtoneLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                try {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers do not expose persistable permissions; the Uri still works now.
                }
                applyPickedUri(RingtoneSoundResolver.CUSTOMIZE, it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityRingtoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ThemeManager.applyTheme(this, binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences(RingtoneSoundResolver.PREFS_NAME, Context.MODE_PRIVATE)

        setupBackButton()
        setupRingtoneSelection()
        loadSelectedRingtone()

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
        themeChangeReceiver?.let(::unregisterReceiver)
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRingtoneSelection() {
        ringtoneAdapter = RingtoneAdapter { option ->
            when (option.key) {
                RingtoneSoundResolver.SYSTEM -> openSystemRingtonePicker()
                RingtoneSoundResolver.CUSTOMIZE -> customRingtoneLauncher.launch(arrayOf("audio/*"))
                else -> applyRingtone(option.key)
            }
        }
        binding.recyclerViewRingtones.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerViewRingtones.adapter = ringtoneAdapter
        ringtoneAdapter.submitList(referenceRingtoneOptions())
    }

    private fun loadSelectedRingtone() {
        val selectedRingtone = sharedPreferences
            .getString(RingtoneSoundResolver.KEY_SELECTED_RINGTONE, RingtoneSoundResolver.DEFAULT)
            ?.takeUnless { it == "wind_chimes" }
            ?: RingtoneSoundResolver.DEFAULT

        selectedRingtoneName = selectedRingtone
        ringtoneAdapter.setSelectedKey(selectedRingtone)
    }

    private fun applyRingtone(ringtoneName: String, pickedUri: Uri? = null, playPreview: Boolean = true) {
        stopPreviewSound()
        selectedRingtoneName = ringtoneName
        ringtoneAdapter.setSelectedKey(ringtoneName)
        saveRingtonePreference(ringtoneName, pickedUri)

        if (playPreview && ringtoneName != RingtoneSoundResolver.SOUND_OFF) {
            val previewUri = RingtoneSoundResolver.getPreviewSoundUri(
                context = this,
                ringtoneName = ringtoneName,
                pickedUri = pickedUri?.toString()
                    ?: sharedPreferences.getString(RingtoneSoundResolver.KEY_SELECTED_RINGTONE_URI, null)
            )
            if (previewUri != null && previewUri != Uri.EMPTY) {
                playWithMediaPlayer(previewUri)
            }
        }

        Toast.makeText(this, R.string.ringtone_applied_successfully, Toast.LENGTH_SHORT).show()
    }

    private fun applyPickedUri(ringtoneName: String, uri: Uri) {
        applyRingtone(ringtoneName, uri)
    }

    private fun saveRingtonePreference(ringtoneName: String, pickedUri: Uri? = null) {
        sharedPreferences.edit()
            .putString(RingtoneSoundResolver.KEY_SELECTED_RINGTONE, ringtoneName)
            .apply {
                if (pickedUri != null) {
                    putString(RingtoneSoundResolver.KEY_SELECTED_RINGTONE_URI, pickedUri.toString())
                } else if (ringtoneName != RingtoneSoundResolver.SYSTEM && ringtoneName != RingtoneSoundResolver.CUSTOMIZE) {
                    remove(RingtoneSoundResolver.KEY_SELECTED_RINGTONE_URI)
                }
            }
            .apply()
    }

    private fun openSystemRingtonePicker() {
        val existingUri = sharedPreferences
            .getString(RingtoneSoundResolver.KEY_SELECTED_RINGTONE_URI, null)
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        }
        systemRingtoneLauncher.launch(intent)
    }

    private fun playWithMediaPlayer(uri: Uri) {
        try {
            val mediaPlayer = MediaPlayer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
            }

            mediaPlayer.setDataSource(this, uri)
            mediaPlayer.setVolume(1f, 1f)
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener { mp ->
                if (currentPreviewMediaPlayer == mp) {
                    currentPreviewMediaPlayer = null
                }
                mp.release()
            }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                if (currentPreviewMediaPlayer == mp) {
                    currentPreviewMediaPlayer = null
                }
                mp.release()
                true
            }
            currentPreviewMediaPlayer = mediaPlayer
            mediaPlayer.start()
        } catch (e: Exception) {
            android.util.Log.e("RingtoneActivity", "Error playing with MediaPlayer, trying Ringtone", e)
            playWithRingtoneFallback(uri)
        }
    }

    private fun playWithRingtoneFallback(uri: Uri) {
        try {
            val ringtone = RingtoneManager.getRingtone(this, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                ringtone.setStreamType(AudioManager.STREAM_MUSIC)
            }
            currentPreviewRingtone = ringtone
            ringtone.play()
        } catch (e: Exception) {
            android.util.Log.e("RingtoneActivity", "Error with Ringtone fallback", e)
        }
    }

    private fun stopPreviewSound() {
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

    private fun referenceRingtoneOptions(): List<RingtoneOption> = listOf(
        RingtoneOption(RingtoneSoundResolver.SOUND_OFF, getString(R.string.gen_activity_ringtone_text_3), R.drawable.ref_ic_ringtone_bg_sound_off),
        RingtoneOption(RingtoneSoundResolver.DEFAULT, getString(R.string.gen_activity_ringtone_text_4), R.drawable.ref_ic_ringtone_bg_default),
        RingtoneOption("bling", getString(R.string.gen_activity_ringtone_text_5), R.drawable.ref_ic_ringtone_bg_bling),
        RingtoneOption("android", getString(R.string.gen_activity_ringtone_text_6), R.drawable.ref_ic_ringtone_bg_android),
        RingtoneOption("whistle", getString(R.string.gen_activity_ringtone_text_7), R.drawable.ref_ic_ringtone_bg_whistle),
        RingtoneOption("ting", getString(R.string.gen_activity_ringtone_text_8), R.drawable.ref_ic_ringtone_bg_ting),
        RingtoneOption("alert", getString(R.string.gen_activity_ringtone_text_9), R.drawable.ref_ic_ringtone_bg_alert),
        RingtoneOption("instruments", getString(R.string.gen_activity_ringtone_text_10), R.drawable.ref_ic_ringtone_bg_instruments),
        RingtoneOption("soul", getString(R.string.gen_activity_ringtone_text_11), R.drawable.ref_ic_ringtone_bg_soul),
        RingtoneOption("love", getString(R.string.gen_activity_ringtone_text_12), R.drawable.ref_ic_ringtone_bg_love),
        RingtoneOption("water", getString(R.string.gen_activity_ringtone_text_13), R.drawable.ref_ic_ringtone_bg_water),
        RingtoneOption("tick", getString(R.string.gen_activity_ringtone_text_14), R.drawable.ref_ic_ringtone_bg_tick),
        RingtoneOption(RingtoneSoundResolver.SYSTEM, getString(R.string.gen_activity_ringtone_text_15), R.drawable.ref_ic_ringtone_bg_system),
        RingtoneOption(RingtoneSoundResolver.CUSTOMIZE, getString(R.string.gen_activity_ringtone_text_16), R.drawable.ref_ic_ringtone_bg_customize)
    )
}
