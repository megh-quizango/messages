package com.text.messages.sms.messanger.ui.defaultsms

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityDefaultSmsBinding
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.ui.overlaypermission.OverlayPermissionActivity
import com.text.messages.sms.messanger.util.ButtonShimmerAnimator
import com.text.messages.sms.messanger.util.ThemeManager

class DefaultSmsActivity : BaseActivity() {

    private lateinit var binding: ActivityDefaultSmsBinding
    private var isFromSettings = false
    private var hasLaunchedIntent = false
    private lateinit var sharedPreferences: SharedPreferences
    private var buttonShimmerAnimator: ObjectAnimator? = null
    private var permissionsRequested = false
    
    // ActivityResultLauncher for RoleManager (Android 10+)
    private val roleRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        hasLaunchedIntent = false
        
        // Check if app is now default SMS using proper method
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
        
        if (isDefault) {
            getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DEFAULT_SMS_SET", true)
                .apply()
            
            // Now request runtime permissions (SMS/Phone) after default handler is set
            // This complies with Google Play policy: default handler prompt must come before runtime permissions
            requestRuntimePermissions()
        }
        // If not default, activity remains open - user must set it as default
    }
    
    // Permission launcher for SMS and Phone permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("DefaultSmsActivity", "Permission request result: $permissions")
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // All runtime permissions granted, proceed to next screen
            Log.d("DefaultSmsActivity", "All permissions granted")
            onPermissionsGranted()
        } else {
            // Some permissions denied
            val deniedPermissions = permissions.filter { !it.value }
            val firstDenied = deniedPermissions.keys.firstOrNull() ?: ""
            Log.d("DefaultSmsActivity", "Some permissions denied: $deniedPermissions")
            
            if (firstDenied.isNotEmpty() && 
                ActivityCompat.shouldShowRequestPermissionRationale(this, firstDenied)) {
                // User denied but can still grant, show dialog again
                permissionsRequested = false
                requestRuntimePermissions()
            } else {
                // User permanently denied, show settings dialog
                showPermissionSettingsDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        // Check if opened from Settings
        isFromSettings = intent.getBooleanExtra("from_settings", false)
        
        // IMPORTANT: Check if app is already default SMS BEFORE showing the screen
        // This must be the FIRST check to prevent the activity from appearing
        // Use RoleManager for Android 10+ (more reliable), fallback to Telephony for older versions
        val isAlreadyDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Use RoleManager
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            // Android 9 and below - Use Telephony
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
        
        Log.d("DefaultSmsActivity", "onCreate - isAlreadyDefault: $isAlreadyDefault")
        Log.d("DefaultSmsActivity", "onCreate - isFromSettings: $isFromSettings")
        
        if (isAlreadyDefault) {
            // App is already default - update SharedPreferences
            Log.d("DefaultSmsActivity", "App is already default - checking permissions")
            sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
            
            // Check if we need to request permissions
            if (!hasAllRequiredPermissions()) {
                // Need to request permissions, show activity and request them
                Log.d("DefaultSmsActivity", "App is default but missing permissions - requesting")
                // Continue to show activity and request permissions
            } else {
                // Already have permissions, navigate away immediately
                Log.d("DefaultSmsActivity", "App is default and has permissions - navigating away")
                onPermissionsGranted()
                return // Exit early - don't show the activity
            }
        }
        
        Log.d("DefaultSmsActivity", "App is NOT default - showing activity")
        
        // Only proceed with activity setup if app is NOT default
        enableEdgeToEdge()
        binding = ActivityDefaultSmsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupButton()
        setupHelp()
        
        // If app is already default and we need to request permissions, do it after UI is set up
        if (::binding.isInitialized) {
            val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            } else {
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
                defaultSmsPackage != null && packageName == defaultSmsPackage
            }
            
            if (isDefault && !hasAllRequiredPermissions()) {
                // Small delay to ensure UI is ready, then request permissions
                binding.root.post {
                    requestRuntimePermissions()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.viewSetDefaultShimmer.post {
            buttonShimmerAnimator = ButtonShimmerAnimator.start(
                binding.viewSetDefaultShimmer,
                buttonShimmerAnimator
            )
        }
        
        // Check if we're returning from the system dialog
        if (hasLaunchedIntent) {
            hasLaunchedIntent = false // Reset flag
            
            // Check if app is now default SMS using RoleManager for Android 10+
            val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            } else {
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
                defaultSmsPackage != null && packageName == defaultSmsPackage
            }
            
            if (isDefault) {
                // Mark default SMS as set
                sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
                
                // Request runtime permissions after default handler is set
                requestRuntimePermissions()
            }
            // If not default, activity remains open - user must set it as default
            return
        }
        
        // Check permissions again when returning from settings
        if (permissionsRequested) {
            if (hasAllRequiredPermissions()) {
                onPermissionsGranted()
            }
            return
        }
        
        // Always check in onResume if app became default (e.g., from system settings)
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
        
        if (isDefault) {
            sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
            
            // Check if we need to request permissions
            if (!hasAllRequiredPermissions()) {
                requestRuntimePermissions()
            } else {
                // Already have permissions, navigate to next screen
                onPermissionsGranted()
            }
        }
    }

    override fun onPause() {
        ButtonShimmerAnimator.stop(binding.viewSetDefaultShimmer, buttonShimmerAnimator)
        buttonShimmerAnimator = null
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Check if app is default SMS before allowing back press
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
        
        // Only allow back press if app is set as default SMS
        if (isDefault) {
            super.onBackPressed()
        }
        // Otherwise, do nothing - prevent dismissing the activity
    }
    
    private fun setupButton() {
        // Double-check if already default SMS app (in case state changed)
        val isAlreadyDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
        
        if (isAlreadyDefault) {
            // App became default - check permissions
            sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
            
            if (!hasAllRequiredPermissions()) {
                requestRuntimePermissions()
            } else {
                onPermissionsGranted()
            }
            return
        }
        
        binding.buttonSetDefault.setOnClickListener {
            requestDefaultSms()
        }
    }

    private fun setupHelp() {
        binding.textHelpLink.setOnClickListener {
            showDefaultSmsHelpDialog()
        }
    }

    private fun showDefaultSmsHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.default_sms_help_title)
            .setMessage(R.string.default_sms_help_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }
    
    private fun requestDefaultSms() {
        // Check again before launching intent using RoleManager for Android 10+
        val isAlreadyDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            defaultSmsPackage != null && packageName == defaultSmsPackage
        }
        
        if (isAlreadyDefault) {
            // App is already default - check permissions
            sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
            
            if (!hasAllRequiredPermissions()) {
                requestRuntimePermissions()
            } else {
                onPermissionsGranted()
            }
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+) - Use RoleManager
            val roleManager = getSystemService(RoleManager::class.java)
            
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) 
                && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                roleRequestLauncher.launch(intent)
                hasLaunchedIntent = true
            } else if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                // Already the default SMS app - request permissions
                sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
                requestRuntimePermissions()
            }
        } else {
            // Android 9 and below - Use ACTION_CHANGE_DEFAULT
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
            hasLaunchedIntent = true
        }
    }
    
    private fun hasAllRequiredPermissions(): Boolean {
        return getRequiredRuntimePermissions().isEmpty()
    }
    
    private fun getAllRequiredPermissions(): List<String> {
        // Only SMS permissions - these must be requested AFTER default handler is set
        // POST_NOTIFICATIONS and READ_PHONE_STATE are requested in WelcomeActivity
        val requiredPermissions = mutableListOf<String>()
        
        // SMS permissions - request these after app becomes default SMS handler
        requiredPermissions.add(Manifest.permission.READ_SMS)
        requiredPermissions.add(Manifest.permission.SEND_SMS)
        requiredPermissions.add(Manifest.permission.RECEIVE_SMS)
        requiredPermissions.add(Manifest.permission.READ_PHONE_STATE)
        
        return requiredPermissions
    }
    
    private fun getRequiredRuntimePermissions(): List<String> {
        // Return only permissions that are NOT granted
        val allPermissions = getAllRequiredPermissions()
        return allPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestRuntimePermissions() {
        val allPermissions = getAllRequiredPermissions()
        val missingPermissions = getRequiredRuntimePermissions()
        
        Log.d("DefaultSmsActivity", "Requesting permissions - All: $allPermissions")
        Log.d("DefaultSmsActivity", "Missing permissions: $missingPermissions")
        
        // Check current permission status for debugging
        allPermissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            Log.d("DefaultSmsActivity", "Permission $permission: ${if (isGranted) "GRANTED" else "NOT GRANTED"}")
        }
        
        // IMPORTANT: Always request permissions explicitly, even if already granted
        // This ensures Google Play compliance - we must go through the permission request flow
        // Android will handle already-granted permissions gracefully:
        // - If permissions are auto-granted (when app becomes default SMS handler), Android will
        //   immediately return granted status in the callback without showing a dialog
        // - If permissions are not granted, Android will show the permission dialog
        // This demonstrates proper permission request flow to Google Play reviewers
        if (!permissionsRequested) {
            permissionsRequested = true
            
            if (missingPermissions.isEmpty()) {
                // All permissions already granted (likely auto-granted when set as default SMS handler)
                // Still request them explicitly to go through the proper flow
                Log.d("DefaultSmsActivity", "All permissions already granted (auto-granted), but requesting explicitly for compliance")
                // Request all permissions - Android will immediately return granted in callback
                requestPermissionsLauncher.launch(allPermissions.toTypedArray())
            } else {
                // Some permissions missing - request them (Android will show dialog)
                Log.d("DefaultSmsActivity", "Launching permission request dialog for: $missingPermissions")
                requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }
    
    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.default_sms_permissions_required_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                // User cancelled, check again (might have granted manually)
                permissionsRequested = false
            }
            .setCancelable(false)
            .show()
    }
    
    private fun onPermissionsGranted() {
        // All permissions granted, navigate to next screen
        if (isFromSettings) {
            // If opened from settings, just finish (user can continue from MainActivity)
            finish()
        } else {
            // Navigate to overlay permission screen
            // OverlayPermissionActivity will handle language check and navigation
            startActivity(Intent(this, OverlayPermissionActivity::class.java))
            finish()
        }
    }
}
