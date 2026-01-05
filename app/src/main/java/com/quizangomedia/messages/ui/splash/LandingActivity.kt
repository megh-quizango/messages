package com.quizangomedia.messages.ui.splash

import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.MobileAds
import com.quizangomedia.messages.R
import com.quizangomedia.messages.ui.language.LanguageActivity
import com.quizangomedia.messages.ui.main.MainActivity
import com.quizangomedia.messages.util.ThemeManager

class LandingActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableEdgeToEdge()
        }
        
        setContentView(R.layout.activity_landing)
        
        // Apply theme
        ThemeManager.applyTheme(this, findViewById(android.R.id.content))
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        // Initialize AdMob SDK
        initializeMobileAdsSdk()
        
        // Redirect after delay
        handler.postDelayed({
            redirectToActivity()
        }, 2000) // 2 second splash
    }
    
    private fun initializeMobileAdsSdk() {
        MobileAds.initialize(this) { }
    }
    
    private fun redirectToActivity() {
        val isLanguageSet = sharedPreferences.getBoolean("IS_LANGUAGE_SET", false)
        
        // Check if app is actually the default SMS app
        // Use RoleManager for Android 10+ (more reliable), fallback to Telephony for older versions
        val isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Use RoleManager
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            // Android 9 and below - Use Telephony
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
        
        Log.d("LandingActivity", "redirectToActivity - isDefaultSmsApp: $isDefaultSmsApp")
        Log.d("LandingActivity", "redirectToActivity - isLanguageSet: $isLanguageSet")
        
        if (!isLanguageSet) {
            // First time - show language selection
            Log.d("LandingActivity", "Redirecting to LanguageActivity")
            startActivity(Intent(this, LanguageActivity::class.java))
        } else if (!isDefaultSmsApp) {
            // App is not set as default SMS - show default SMS setup
            Log.d("LandingActivity", "Redirecting to DefaultSmsActivity")
            startActivity(Intent(this, com.quizangomedia.messages.ui.defaultsms.DefaultSmsActivity::class.java))
        } else {
            // App is default SMS app - go to main
            Log.d("LandingActivity", "App is default - redirecting to MainActivity")
            // Also update SharedPreferences to reflect current state
            sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}

