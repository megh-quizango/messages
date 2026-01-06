package com.quizangomedia.messages.ui.splash

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.widget.ProgressBar
import android.animation.ObjectAnimator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.MobileAds
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityLandingBinding
import com.quizangomedia.messages.ui.language.LanguageActivity
import com.quizangomedia.messages.ui.main.MainActivity
import com.quizangomedia.messages.util.PermissionManager

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val splashDuration = 3000L // 3 seconds
    
    private var permissionsRequested = false
    private var overlayPermissionRequested = false

    // Activity result launcher for overlay permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check overlay permission after returning from settings
        handler.postDelayed({
            checkAndRequestPermissions()
        }, 500)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableEdgeToEdge()
        }
        
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Don't apply theme to splash screen - keep the gradient background
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        // Initialize AdMob SDK
        initializeMobileAdsSdk()
        
        // Animate progress bar
        animateProgressBar()
        
        // Check and request permissions first
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        // Check if all permissions are granted
        if (PermissionManager.areAllPermissionsGranted(this)) {
            // All permissions granted, proceed after delay
            permissionsRequested = false
            overlayPermissionRequested = false
            handler.postDelayed({
                redirectToActivity()
            }, splashDuration)
            return
        }
        
        // Check overlay permission first (special handling)
        if (!PermissionManager.hasOverlayPermission(this)) {
            if (!overlayPermissionRequested) {
                overlayPermissionRequested = true
                showOverlayPermissionDialog()
            }
            return
        }
        
        // Overlay permission granted, check runtime permissions
        val missingPermissions = PermissionManager.getMissingPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            if (!permissionsRequested) {
                permissionsRequested = true
                showPermissionDialog(missingPermissions)
            }
        } else {
            // All runtime permissions granted and overlay is granted
            permissionsRequested = false
            overlayPermissionRequested = false
            handler.postDelayed({
                redirectToActivity()
            }, splashDuration)
        }
    }
    
    private fun showPermissionDialog(missingPermissions: List<String>) {
        val permissionNames = missingPermissions.map { permission ->
            when (permission) {
                android.Manifest.permission.READ_SMS -> "Read SMS"
                android.Manifest.permission.SEND_SMS -> "Send SMS"
                android.Manifest.permission.RECEIVE_SMS -> "Receive SMS"
                android.Manifest.permission.READ_PHONE_STATE -> "Read Phone State"
                android.Manifest.permission.READ_CONTACTS -> "Read Contacts"
                android.Manifest.permission.CALL_PHONE -> "Make Phone Calls"
                android.Manifest.permission.READ_EXTERNAL_STORAGE -> "Read Storage"
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Write Storage"
                android.Manifest.permission.CAMERA -> "Camera"
                android.Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                else -> permission
            }
        }.joinToString("\n• ")
        
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs the following permissions to function properly:\n\n• $permissionNames\n\nPlease grant these permissions to continue.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                PermissionManager.requestAllPermissions(
                    this,
                    PermissionManager.RequestCodes.ALL_PERMISSIONS
                )
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Display Over Other Apps")
            .setMessage("This app needs permission to display over other apps for the call overlay feature. Please enable this permission in settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                overlayPermissionLauncher.launch(PermissionManager.getOverlayPermissionIntent(this))
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PermissionManager.RequestCodes.ALL_PERMISSIONS) {
            val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                // All runtime permissions granted, check overlay permission
                checkAndRequestPermissions()
            } else {
                // Some permissions denied, show dialog again
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                
                val firstDeniedPermission = deniedPermissions.firstOrNull() ?: ""
                if (firstDeniedPermission.isNotEmpty() && 
                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, firstDeniedPermission)) {
                    // User denied but can still grant, show dialog again
                    permissionsRequested = false
                    checkAndRequestPermissions()
                } else {
                    // User permanently denied, show settings dialog
                    showPermissionSettingsDialog()
                }
            }
        }
    }
    
    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Some permissions were denied. Please enable them in app settings to continue.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // User cancelled, check again (might have granted manually)
                handler.postDelayed({
                    checkAndRequestPermissions()
                }, 1000)
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        // Check permissions again when returning from settings
        if (permissionsRequested || overlayPermissionRequested) {
            handler.postDelayed({
                checkAndRequestPermissions()
            }, 500)
        }
    }
    
    private fun animateProgressBar() {
        val progressBar = binding.progressBar
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100)
        animator.duration = splashDuration
        animator.start()
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

