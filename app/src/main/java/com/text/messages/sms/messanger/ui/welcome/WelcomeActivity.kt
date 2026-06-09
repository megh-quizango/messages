package com.text.messages.sms.messanger.ui.welcome

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.animation.ObjectAnimator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityWelcomeBinding
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.util.ButtonShimmerAnimator

class WelcomeActivity : BaseActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var buttonShimmerAnimator: ObjectAnimator? = null
    private var welcomePermissionIndex = 0

    private val welcomePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestNextWelcomePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableEdgeToEdge()
        }
        
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureStatusBar()

        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingRight = binding.root.paddingRight
        val initialPaddingBottom = binding.root.paddingBottom
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )
            insets
        }
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.buttonAgreeContinue.setOnClickListener {
            requestWelcomePermissionsThenContinue()
        }
        
        binding.textPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/quizangomedia.com/quizango-media-private-limited/messages-sms-texting-app"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }
    }

    private fun configureStatusBar() {
        window.statusBarColor = getColor(android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun requestWelcomePermissionsThenContinue() {
        welcomePermissionIndex = 0
        requestNextWelcomePermission()
    }

    private fun requestNextWelcomePermission() {
        val permissions = getWelcomePermissions()
        while (welcomePermissionIndex < permissions.size) {
            val permission = permissions[welcomePermissionIndex++]
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                welcomePermissionLauncher.launch(permission)
                return
            }
        }
        continueToLanguage()
    }

    private fun getWelcomePermissions(): List<String> {
        return buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun continueToLanguage() {
        sharedPreferences.edit().putBoolean("HAS_SEEN_WELCOME", true).apply()
        startActivity(Intent(this, LanguageActivity::class.java))
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        binding.viewAgreeContinueShimmer.post {
            buttonShimmerAnimator = ButtonShimmerAnimator.start(
                binding.viewAgreeContinueShimmer,
                buttonShimmerAnimator
            )
        }
    }

    override fun onPause() {
        ButtonShimmerAnimator.stop(binding.viewAgreeContinueShimmer, buttonShimmerAnimator)
        buttonShimmerAnimator = null
        super.onPause()
    }
}
