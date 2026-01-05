package com.quizangomedia.messages.ui.defaultsms

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quizangomedia.messages.databinding.ActivityDefaultSmsBinding
import com.quizangomedia.messages.ui.main.MainActivity
import com.quizangomedia.messages.util.ThemeManager

class DefaultSmsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDefaultSmsBinding
    private var isFromSettings = false
    private var hasLaunchedIntent = false
    
    // ActivityResultLauncher for RoleManager (Android 10+)
    private val roleRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        hasLaunchedIntent = false
        
        // Check if app is now default SMS
        val isDefault = packageName == Telephony.Sms.getDefaultSmsPackage(this)
        
        if (isDefault) {
            getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DEFAULT_SMS_SET", true)
                .apply()
        }
        
        if (isFromSettings) {
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
            // App is already default - update SharedPreferences and navigate away immediately
            Log.d("DefaultSmsActivity", "App is already default - navigating away")
            getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DEFAULT_SMS_SET", true)
                .apply()
            
            // Navigate away immediately without showing the activity
            if (isFromSettings) {
                finish() // Return to SettingsActivity
            } else {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            return // Exit early - don't show the activity
        }
        
        Log.d("DefaultSmsActivity", "App is NOT default - showing activity")
        
        // Only proceed with activity setup if app is NOT default
        enableEdgeToEdge()
        binding = ActivityDefaultSmsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupButton()
    }
    
    override fun onResume() {
        super.onResume()
        
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
                getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("IS_DEFAULT_SMS_SET", true)
                    .apply()
            }
            
            // Navigate based on where we came from
            if (isFromSettings) {
                finish() // Return to SettingsActivity
            } else {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
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
            // App became default - navigate away immediately
            getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DEFAULT_SMS_SET", true)
                .apply()
            
            if (isFromSettings) {
                finish()
            } else {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            return
        }
        
        binding.buttonSetDefault.setOnClickListener {
            requestDefaultSms()
        }
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
            // App is already default - navigate away
            getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DEFAULT_SMS_SET", true)
                .apply()
            
            if (isFromSettings) {
                finish()
            } else {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
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
                // Already the default SMS app - navigate away
                getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("IS_DEFAULT_SMS_SET", true)
                    .apply()
                
                if (isFromSettings) {
                    finish()
                } else {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        } else {
            // Android 9 and below - Use ACTION_CHANGE_DEFAULT
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
            hasLaunchedIntent = true
        }
    }
}
