package com.text.messages.sms.messanger.ui.splash

import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.animation.ObjectAnimator
import android.view.View
import androidx.activity.enableEdgeToEdge
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityLandingBinding
import com.text.messages.sms.messanger.databinding.ActivityResumeBinding
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.ui.main.MainViewModel
import com.text.messages.sms.messanger.ui.welcome.WelcomeActivity
import com.text.messages.sms.messanger.util.AppOpenAdManager
import androidx.lifecycle.ViewModelProvider

class LandingActivity : BaseActivity() {

    companion object {
        /** Timestamp of cold start, used to enforce minimum delay before ads */
        @Volatile
        var coldStartTimestampMs: Long = 0L

        private const val PREF_HAS_SHOWN_SPLASH = "HAS_SHOWN_SPLASH"
    }

    private lateinit var binding: ActivityLandingBinding
    private var resumeBinding: ActivityResumeBinding? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val splashDuration = 1500L // 1.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Record cold-start timestamp for ad delay enforcement
        if (coldStartTimestampMs == 0L) {
            coldStartTimestampMs = android.os.SystemClock.elapsedRealtime()
        }

        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)

        if (sharedPreferences.getBoolean(PREF_HAS_SHOWN_SPLASH, false)) {
            Log.d("LandingActivity", "Splash already shown before - routing immediately")
            showRepeatOpenFlow()
            return
        }

        sharedPreferences.edit().putBoolean(PREF_HAS_SHOWN_SPLASH, true).apply()

        enableEdgeToEdge()

        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // GPU warm-up: force RenderThread to initialize early
        binding.root.post {
            binding.root.invalidate()
        }
        
        // Don't apply theme to splash screen - keep the gradient background
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Animate progress bar
        animateProgressBar()
        
        // Pre-load conversations in background (only if SMS permission is granted)
        // This will make MainActivity load instantly from cache
        handler.postDelayed({
            preloadConversations()
        }, 250)
        
        // After splash duration, navigate to next activity
        handler.postDelayed({
            redirectToActivity()
        }, splashDuration)
    }
    
    private fun animateProgressBar() {
        val progressBar = binding.progressBar
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100)
        animator.duration = splashDuration
        animator.start()
    }
    
    private fun preloadConversations() {
        // Check if we have SMS permission before pre-loading
        if (android.content.pm.PackageManager.PERMISSION_GRANTED == 
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)) {
            // Create ViewModel to pre-load conversations in background
            // This will populate the cache so MainActivity loads instantly
            // Use handler.post to ensure it runs asynchronously and doesn't block
            handler.post {
                try {
                    val viewModel = ViewModelProvider(this@LandingActivity)[MainViewModel::class.java]
                    // Pre-load "All" category conversations in background
                    viewModel.preloadConversations("All")
                    Log.d("LandingActivity", "Started pre-loading conversations")
                } catch (e: Exception) {
                    Log.w("LandingActivity", "Failed to start pre-loading conversations", e)
                }
            }
        } else {
            Log.d("LandingActivity", "SMS permission not granted, skipping pre-load")
        }
    }
    
    private fun redirectToActivity() {
        val hasSeenWelcome = sharedPreferences.getBoolean("HAS_SEEN_WELCOME", false)
        
        Log.d("LandingActivity", "redirectToActivity - hasSeenWelcome: $hasSeenWelcome")

        // Show a cold-start app open ad before navigating
        AppOpenAdManager.showColdStartAppOpenAd(this) {
            navigateToNextActivity(hasSeenWelcome, showResumeScreen = false)
        }
    }
    
    private fun navigateToNextActivity(hasSeenWelcome: Boolean, showResumeScreen: Boolean) {
        var staysOnLauncher = false
        if (!hasSeenWelcome) {
            // First time - show welcome screen
            Log.d("LandingActivity", "Redirecting to WelcomeActivity")
            startActivity(Intent(this, WelcomeActivity::class.java))
        } else {
            // Welcome screen already shown, proceed with normal flow
            val isLanguageSet = sharedPreferences.getBoolean("IS_LANGUAGE_SET", false)
            val isDefaultSmsApp = isDefaultSmsApp()
            
            Log.d("LandingActivity", "redirectToActivity - isDefaultSmsApp: $isDefaultSmsApp")
            Log.d("LandingActivity", "redirectToActivity - isLanguageSet: $isLanguageSet")
            
            if (!isLanguageSet) {
                // Show language selection
                Log.d("LandingActivity", "Redirecting to LanguageActivity")
                startActivity(Intent(this, LanguageActivity::class.java))
            } else if (!isDefaultSmsApp) {
                // App is not set as default SMS - show default SMS setup
                Log.d("LandingActivity", "Redirecting to DefaultSmsActivity")
                startActivity(Intent(this, com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity::class.java))
            } else {
                // App is default SMS app - continue to main flow
                // Also update SharedPreferences to reflect current state
                sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
                if (showResumeScreen) {
                    Log.d("LandingActivity", "App is default - showing in-place resume screen")
                    staysOnLauncher = true
                    showResumeScreenAndNavigate()
                } else {
                    Log.d("LandingActivity", "App is default - redirecting to MainActivity")
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
        }
        if (!staysOnLauncher) {
            finish()
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
    }

    private fun showRepeatOpenFlow() {
        val hasSeenWelcome = sharedPreferences.getBoolean("HAS_SEEN_WELCOME", false)
        navigateToNextActivity(hasSeenWelcome = hasSeenWelcome, showResumeScreen = true)
    }

    private fun showResumeScreenAndNavigate() {
        enableEdgeToEdge()

        val binding = ActivityResumeBinding.inflate(layoutInflater)
        resumeBinding = binding
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        preloadConversations()

        binding.root.post {
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        resumeBinding = null
        super.onDestroy()
    }
}
