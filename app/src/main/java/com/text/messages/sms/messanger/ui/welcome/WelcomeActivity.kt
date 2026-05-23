package com.text.messages.sms.messanger.ui.welcome

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.animation.ObjectAnimator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityWelcomeBinding
import com.text.messages.sms.messanger.ui.overlaypermission.OverlayPermissionActivity
import com.text.messages.sms.messanger.util.ButtonShimmerAnimator

class WelcomeActivity : BaseActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var buttonShimmerAnimator: ObjectAnimator? = null
    private var permissionsRequested = false
    private var hasLaunchedDefaultSmsIntent = false

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        hasLaunchedDefaultSmsIntent = false
        if (isDefaultSmsApp()) {
            sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
            requestRemainingPermissions()
        }
    }
    
    // Permission launcher for post-default runtime permissions.
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            val deniedPermissions = permissions.filter { !it.value }
            val firstDenied = deniedPermissions.keys.firstOrNull() ?: ""
            
            if (firstDenied.isNotEmpty() && 
                ActivityCompat.shouldShowRequestPermissionRationale(this, firstDenied)) {
                permissionsRequested = false
                requestRemainingPermissions()
            } else {
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
            startDefaultSmsThenPermissionsFlow()
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

    private fun startDefaultSmsThenPermissionsFlow() {
        if (isDefaultSmsApp()) {
            sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
            requestRemainingPermissions()
            return
        }

        requestDefaultSms()
    }

    private fun requestDefaultSms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {
                hasLaunchedDefaultSmsIntent = true
                roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            } else if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
                requestRemainingPermissions()
            }
        } else {
            hasLaunchedDefaultSmsIntent = true
            startActivity(
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
            )
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

    private fun getRequiredRemainingPermissions(): List<String> {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val postDefaultPermissions = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        postDefaultPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(permission)
            }
        }

        return requiredPermissions
    }
    private fun requestRemainingPermissions() {
        val missingPermissions = getRequiredRemainingPermissions()

        if (missingPermissions.isEmpty()) {
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
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.welcome_combined_permissions_required_message)
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
        sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
        sharedPreferences.edit().putBoolean("HAS_SEEN_WELCOME", true).apply()

        startActivity(Intent(this, OverlayPermissionActivity::class.java))
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

        if (permissionsRequested) {
            if (getRequiredRemainingPermissions().isEmpty()) {
                onPermissionsGranted()
            }
            return
        }

        if (hasLaunchedDefaultSmsIntent) {
            hasLaunchedDefaultSmsIntent = false
            if (isDefaultSmsApp()) {
                sharedPreferences.edit().putBoolean("IS_DEFAULT_SMS_SET", true).apply()
                if (getRequiredRemainingPermissions().isEmpty()) {
                    onPermissionsGranted()
                } else {
                    requestRemainingPermissions()
                }
            }
        }
    }

    override fun onPause() {
        ButtonShimmerAnimator.stop(binding.viewAgreeContinueShimmer, buttonShimmerAnimator)
        buttonShimmerAnimator = null
        super.onPause()
    }
}
