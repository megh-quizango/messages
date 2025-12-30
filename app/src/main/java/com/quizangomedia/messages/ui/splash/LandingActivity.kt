package com.quizangomedia.messages.ui.splash

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.MobileAds
import com.quizangomedia.messages.R
import com.quizangomedia.messages.ui.language.LanguageActivity
import com.quizangomedia.messages.ui.main.MainActivity

class LandingActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableEdgeToEdge()
        }
        
        setContentView(R.layout.activity_landing)
        
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
        val isDefaultSmsSet = sharedPreferences.getBoolean("IS_DEFAULT_SMS_SET", false)
        
        if (!isLanguageSet) {
            // First time - show language selection
            startActivity(Intent(this, LanguageActivity::class.java))
        } else if (!isDefaultSmsSet) {
            // Show default SMS setup
            startActivity(Intent(this, com.quizangomedia.messages.ui.defaultsms.DefaultSmsActivity::class.java))
        } else {
            // Existing user - go to main
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}

