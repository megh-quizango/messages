package com.text.messages.sms.messanger.ui.welcome

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityWelcomeBinding
import com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import android.graphics.Paint

class WelcomeActivity : BaseActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var permissionsRequested = false
    
    // Permission launcher for Notification and Phone permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // All permissions granted, proceed to default SMS setup
            onPermissionsGranted()
        } else {
            // Some permissions denied
            val deniedPermissions = permissions.filter { !it.value }
            val firstDenied = deniedPermissions.keys.firstOrNull() ?: ""
            
            if (firstDenied.isNotEmpty() && 
                ActivityCompat.shouldShowRequestPermissionRationale(this, firstDenied)) {
                // User denied but can still grant, show dialog again
                permissionsRequested = false
                requestNonSmsPermissions()
            } else {
                // User permanently denied, show settings dialog
                showPermissionSettingsDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableEdgeToEdge()
        }
        
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        setupUI()
        setupBannerAd()
    }
    
    private fun setupUI() {
        // Initially disable the button
        binding.buttonAgreeContinue.isEnabled = false
        
        // Set checkbox listener
        binding.checkboxPrivacy.setOnCheckedChangeListener { _, isChecked ->
            binding.buttonAgreeContinue.isEnabled = isChecked
        }
        
        // Set button click listener
        binding.buttonAgreeContinue.setOnClickListener {
            if (binding.checkboxPrivacy.isChecked) {
                // Request Notification and Phone permissions first (these can be requested before default handler)
                requestNonSmsPermissions()
            }
        }
        
        // Set underline for privacy policy text
        binding.textPrivacyPolicy.paintFlags = binding.textPrivacyPolicy.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        
        // Set privacy policy link click listener
        binding.textPrivacyPolicy.setOnClickListener {
            // Open privacy policy (you may need to replace with actual URL)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/quizangomedia.com/quizango-media-private-limited/messages-sms-texting-app"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
    
    private fun getRequiredNonSmsPermissions(): List<String> {
        val requiredPermissions = mutableListOf<String>()
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Phone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        
        return requiredPermissions
    }
    
    private fun requestNonSmsPermissions() {
        val missingPermissions = getRequiredNonSmsPermissions()
        
        if (missingPermissions.isEmpty()) {
            // All permissions already granted, proceed
            onPermissionsGranted()
            return
        }
        
        if (!permissionsRequested) {
            permissionsRequested = true
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Notification and Phone permissions are required for the app to function properly. Please enable them in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // User cancelled, check again (might have granted manually)
                permissionsRequested = false
            }
            .setCancelable(false)
            .show()
    }
    
    private fun onPermissionsGranted() {
        // Mark welcome screen as shown
        sharedPreferences.edit().putBoolean("HAS_SEEN_WELCOME", true).apply()
        
        // Navigate to default SMS setup screen (per Google Play policy)
        // Default handler prompt comes after non-SMS permissions, but before SMS permissions
        startActivity(Intent(this, DefaultSmsActivity::class.java))
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        // Check permissions again when returning from settings
        if (permissionsRequested) {
            val missingPermissions = getRequiredNonSmsPermissions()
            if (missingPermissions.isEmpty()) {
                onPermissionsGranted()
            }
        }
    }
}

