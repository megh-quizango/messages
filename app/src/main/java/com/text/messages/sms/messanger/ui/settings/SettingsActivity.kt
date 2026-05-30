package com.text.messages.sms.messanger.ui.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.databinding.ActivitySettingsBinding
import com.text.messages.sms.messanger.ui.contacts.ContactsActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.ui.personalize.PersonalizeActivity
import com.text.messages.sms.messanger.ui.spam.SpamBlockActivity
import com.text.messages.sms.messanger.ui.private.PrivateConversationsActivity
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.ui.manageapps.ManageAppsActivity
import com.text.messages.sms.messanger.ui.archive.ArchiveActivity
import com.text.messages.sms.messanger.util.MessagesExportImport
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ImExTransitionAdManager
import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var adapter: SettingsAdapter
    private var isSettingSelectedItem = false
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var pendingImExAction: PendingImExAction? = null
    
    // Activity result launchers for file picker
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            handleExportResult(it)
        }
    }
    
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            handleImportResult(it)
        }
    }

    private val imExAdLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = pendingImExAction
        pendingImExAction = null
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        when (action) {
            PendingImExAction.EXPORT -> exportMessages()
            PendingImExAction.IMPORT -> importMessages()
            null -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        android.util.Log.d("SettingsActivity", "=== SettingsActivity.onCreate() ===")
        AnalyticsHelper.logScreenView("SettingsActivity", "SettingsActivity")
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        android.util.Log.d("SettingsActivity", "SettingsActivity.onCreate(): Binding initialized")
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Handle window insets - same as MainActivity
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to root so ad view stays above system navigation
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Bottom navigation should not have extra padding from window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { _, insets ->
            // Don't add padding - we want it to be exactly the size of its content
            insets
        }
        
        // Fix bottom navigation padding
        binding.bottomNavigationView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.bottomNavigationView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                val topPadding = binding.bottomNavigationView.paddingTop
                val bottomPadding = binding.bottomNavigationView.paddingBottom
                binding.bottomNavigationView.setPadding(0, topPadding, 0, bottomPadding)
                binding.bottomNavigationView.minimumHeight = 0
                
                val menuView = binding.bottomNavigationView.getChildAt(0) as? ViewGroup
                menuView?.let {
                    it.setPadding(0, 0, 0, 0)
                    it.minimumHeight = 0
                    
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        child?.let { item ->
                            if (item is ViewGroup) {
                                item.setPadding(item.paddingLeft, 0, item.paddingRight, 0)
                                item.minimumHeight = 0
                            }
                        }
                    }
                }
            }
        })
        
        setupBackButton()
        setupRecyclerView()
        setupBottomNavigation()
        setupBannerAd()
        ImExTransitionAdManager.preload(applicationContext)
        
        // Set Settings as selected initially and apply theme
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_settings)
            // Apply theme after bottom nav is fully laid out
            ThemeManager.applyTheme(this, binding.root)
        }
        
        // Also apply theme immediately
        ThemeManager.applyTheme(this, binding.root)
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        // Helper function to get icon from drawable/settings folder
        // Android flattens drawable resources, so files in drawable/settings/ should be accessible by filename
        fun getIcon(name: String): Int? {
            android.util.Log.d("SettingsActivity", "=== getIcon($name) ===")
            
            return try {
                // Try multiple approaches to find the resource
                var resourceId: Int

                // First try: direct name (for files in main drawable folder or flattened from subfolders)
                resourceId = resources.getIdentifier(name, "drawable", packageName)
                android.util.Log.d("SettingsActivity", "getIcon($name): Direct lookup = $resourceId")
                
                if (resourceId != 0) {
                    // Verify the resource actually exists and is valid
                    try {
                        val drawable = resources.getDrawable(resourceId, theme)
                        android.util.Log.d("SettingsActivity", "getIcon($name): SUCCESS - Resource ID = $resourceId, drawable = ${drawable != null}")
                        return resourceId
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsActivity", "getIcon($name): Resource ID found but invalid: ${e.message}")
                        resourceId = 0
                    }
                }
                
                // Second try: with settings_ prefix (in case they're named with prefix)
                if (resourceId == 0) {
                    resourceId = resources.getIdentifier("settings_$name", "drawable", packageName)
                    android.util.Log.d("SettingsActivity", "getIcon($name): With 'settings_' prefix = $resourceId")
                    if (resourceId != 0) {
                        try {
                            resources.getDrawable(resourceId, theme)
                            android.util.Log.d("SettingsActivity", "getIcon($name): SUCCESS with prefix - Resource ID = $resourceId")
                            return resourceId
                        } catch (e: Exception) {
                            android.util.Log.w("SettingsActivity", "getIcon($name): Prefix resource ID invalid: ${e.message}")
                            resourceId = 0
                        }
                    }
                }
                
                // Third try: List all drawable resources to see what's available
                if (resourceId == 0) {
                    android.util.Log.w("SettingsActivity", "getIcon($name): Resource not found! Listing available drawables...")
                    @Suppress("UNUSED_VARIABLE")
                    val packageName = packageName
                    val drawableFields = R.drawable::class.java.fields
                    val matchingDrawables = drawableFields.filter { 
                        it.name.contains(name, ignoreCase = true) || 
                        it.name.contains("settings", ignoreCase = true)
                    }.take(10)
                    android.util.Log.d("SettingsActivity", "getIcon($name): Found ${matchingDrawables.size} potentially matching drawables:")
                    matchingDrawables.forEach { field ->
                        try {
                            val id = field.getInt(null)
                            android.util.Log.d("SettingsActivity", "  - ${field.name} = $id")
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
                
                if (resourceId == 0) {
                    android.util.Log.e("SettingsActivity", "getIcon($name): FAILED - Resource not found in any location!")
                }
                
                if (resourceId != 0) resourceId else null
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "getIcon($name): Exception = ${e.message}", e)
                null
            }
        }
        
        val settingsItems = listOf(
            SettingsItem(getString(R.string.settings_section_general), listOf(
                SettingsOption(SettingsOptionId.DEFAULT_SMS_APP, getString(R.string.set_default_sms), getIcon("default_sms"), null, true) {
                    // Check if app is already default SMS app
                    val isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ - Use RoleManager
                        val roleManager = getSystemService(RoleManager::class.java)
                        roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                    } else {
                        // Android 9 and below - Use Telephony
                        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
                        defaultSmsPackage != null && packageName == defaultSmsPackage
                    }
                    
                    if (isDefaultSmsApp) {
                        // App is already default - show toast
                        Toast.makeText(this, getString(R.string.settings_default_sms_already_set), Toast.LENGTH_SHORT).show()
                    } else {
                        // App is not default - open DefaultSmsActivity
                        startActivity(Intent(this, com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity::class.java).apply {
                            putExtra("from_settings", true)
                        })
                    }
                },
                SettingsOption(SettingsOptionId.CONTACTS_COLORED_ICONS, getString(R.string.settings_contacts_colored_icons), getIcon("contacts"), true, false),
                SettingsOption(SettingsOptionId.COLOR_SIM_CARD_ICONS, getString(R.string.settings_color_sim_card_icons), getIcon("sim"),
                    com.text.messages.sms.messanger.util.AppPreferences.getColorSimCardIcons(this), false),
                SettingsOption(SettingsOptionId.QUICK_ACCESS_TO_OTP, getString(R.string.settings_quick_access_to_otp), getIcon("otp"), true, false)
            )),
            SettingsItem(getString(R.string.settings_section_go_to), listOf(
                SettingsOption(SettingsOptionId.MANAGE_APPS, getString(R.string.manage_apps), getIcon("manage"), null, false) { startActivity(Intent(this, ManageAppsActivity::class.java)) },
                SettingsOption(SettingsOptionId.PRIVATE_CONVERSATIONS, getString(R.string.private_conversations), getIcon("private_convo"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.pin.PinActivity::class.java)) },
                SettingsOption(SettingsOptionId.SPAM_BLOCK, getString(R.string.spam_block), getIcon("spam"), null, false) { startActivity(Intent(this, SpamBlockActivity::class.java)) },
                SettingsOption(SettingsOptionId.ARCHIVE, getString(R.string.settings_archive), getIcon("archive"), null, false) { startActivity(Intent(this, ArchiveActivity::class.java)) },
                SettingsOption(SettingsOptionId.RECYCLE_BIN, getString(R.string.settings_recycle_bin), getIcon("recycle"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.recyclebin.RecycleBinActivity::class.java)) },
                SettingsOption(SettingsOptionId.SCHEDULE_MESSAGES, getString(R.string.settings_schedule_messages), getIcon("schedule"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.scheduled.ScheduledMessagesActivity::class.java)) },
                SettingsOption(SettingsOptionId.CALLER_SETTINGS, getString(R.string.settings_caller_settings), getIcon("caller"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.caller.CallerSettingsActivity::class.java)) },
                SettingsOption(SettingsOptionId.STARRED, getString(R.string.settings_starred), getIcon("starred"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.starred.StarredActivity::class.java)) },
                SettingsOption(SettingsOptionId.SWIPE_GESTURES, getString(R.string.settings_swipe_gestures), getIcon("swipe"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.swipe.SwipeGesturesActivity::class.java)) },
                SettingsOption(SettingsOptionId.ADD_SIGNATURE, getString(R.string.settings_add_signature), getIcon("signature"), null, false) { showSignatureDialog() },
                SettingsOption(SettingsOptionId.NOTIFICATIONS, getString(R.string.welcome_notifications_title), getIcon("notifications"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.notifications.NotificationsActivity::class.java)) },
                SettingsOption(SettingsOptionId.LANGUAGE, getString(R.string.settings_language), getIcon("language"), null, false) {
                    startActivity(Intent(this, LanguageActivity::class.java).apply {
                        putExtra("from_settings", true)
                    })
                },
                SettingsOption(SettingsOptionId.ADVANCE, getString(R.string.settings_advance), getIcon("advance"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.advance.AdvanceActivity::class.java)) },
                SettingsOption(SettingsOptionId.FEEDBACK, getString(R.string.settings_feedback), getIcon("feedback"), null, false) { startActivity(Intent(this, com.text.messages.sms.messanger.ui.feedback.FeedbackActivity::class.java)) },
                SettingsOption(SettingsOptionId.SHARE_APP, getString(R.string.settings_share_app), getIcon("share"), null, false) { openPlayStoreForSharing() },
                SettingsOption(SettingsOptionId.RATE_US, getString(R.string.settings_rate_us), getIcon("rate_us"), null, false) { showRateUsBottomSheet() }
            )),
            SettingsItem(getString(R.string.settings_section_backups), listOf(
                SettingsOption(SettingsOptionId.EXPORT_MESSAGES, getString(R.string.settings_export_messages), getIcon("export"), null, false) {
                    launchImExAdThen(PendingImExAction.EXPORT)
                },
                SettingsOption(SettingsOptionId.IMPORT_MESSAGES, getString(R.string.settings_import_messages), getIcon("import_message"), null, false) {
                    launchImExAdThen(PendingImExAction.IMPORT)
                }
            ))
        )
        
        adapter = SettingsAdapter(settingsItems) { option ->
            option.onClick?.invoke()
        }
        
        binding.recyclerViewSettings.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSettings.adapter = adapter
    }

    private fun launchImExAdThen(action: PendingImExAction) {
        pendingImExAction = action
        ImExTransitionAdManager.preload(applicationContext)
        imExAdLauncher.launch(
            Intent(this, ImExTransitionAdActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            // Ignore if we're programmatically setting the selected item
            if (isSettingSelectedItem) {
                return@setOnItemSelectedListener true
            }
            
            when (item.itemId) {
                R.id.nav_messages -> {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_contacts -> {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                    startActivity(Intent(this, ContactsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_personalize -> {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                    startActivity(Intent(this, PersonalizeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    // Already on Settings screen
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure Settings tab is selected when activity is visible
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_settings)
        }
    }
    
    private fun setSelectedNavigationItem(itemId: Int) {
        isSettingSelectedItem = true
        
        // First, uncheck all menu items
        for (i in 0 until binding.bottomNavigationView.menu.size()) {
            binding.bottomNavigationView.menu.getItem(i).isChecked = false
        }
        
        // Then check the selected item
        binding.bottomNavigationView.menu.findItem(itemId)?.isChecked = true
        binding.bottomNavigationView.selectedItemId = itemId
        
        // Force refresh
        binding.bottomNavigationView.invalidate()
        binding.bottomNavigationView.post {
            // Force refresh after layout
            binding.bottomNavigationView.invalidate()
            binding.bottomNavigationView.postDelayed({
                isSettingSelectedItem = false
                // One more refresh to ensure tint is applied
                binding.bottomNavigationView.invalidate()
            }, 50)
        }
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
    
    private fun showSignatureDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_signature, null)
        val editTextSignature = dialogView.findViewById<EditText>(R.id.editTextSignature)
        val buttonDelete = dialogView.findViewById<TextView>(R.id.buttonDelete)
        val buttonCancel = dialogView.findViewById<TextView>(R.id.buttonCancel)
        val buttonSave = dialogView.findViewById<TextView>(R.id.buttonSave)
        
        // Load saved signature
        val prefs = getSharedPreferences("signature", MODE_PRIVATE)
        val savedSignature = prefs.getString("signature_text", "")
        editTextSignature.setText(savedSignature)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Apply theme to dialog
        ThemeManager.applyTheme(this, dialogView)
        
        buttonSave.setOnClickListener {
            val signature = editTextSignature.text.toString().trim()
            prefs.edit().putString("signature_text", signature).apply()
            dialog.dismiss()
        }
        
        buttonDelete.setOnClickListener {
            prefs.edit().remove("signature_text").apply()
            editTextSignature.setText("")
        }
        
        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun openPlayStoreForRating() {
        try {
            val appPackageName = packageName
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName"))
            try {
                startActivity(marketIntent)
            } catch (e: android.content.ActivityNotFoundException) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.settings_unable_open_play_store), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlayStoreForSharing() {
        try {
            val appPackageName = packageName
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    getString(R.string.settings_share_app_message, getString(R.string.settings_app_name), appPackageName)
                )
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share_app_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.settings_unable_share_play_store_link), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showRateUsBottomSheet() {
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_rate_us, null)
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(bottomSheetView)
        
        // Apply theme to bottom sheet
        ThemeManager.applyTheme(this, bottomSheetView)
        
        val star1 = bottomSheetView.findViewById<ImageView>(R.id.star1)
        val star2 = bottomSheetView.findViewById<ImageView>(R.id.star2)
        val star3 = bottomSheetView.findViewById<ImageView>(R.id.star3)
        val star4 = bottomSheetView.findViewById<ImageView>(R.id.star4)
        val star5 = bottomSheetView.findViewById<ImageView>(R.id.star5)
        val buttonRateUs = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonRateUs)
        
        val stars = listOf(star1, star2, star3, star4, star5)
        var selectedRating = 0
        
        fun updateStars(rating: Int) {
            stars.forEachIndexed { index, star ->
                if (index < rating) {
                    star.setImageResource(R.drawable.ic_star_filled)
                } else {
                    star.setImageResource(R.drawable.ic_star_outline)
                }
            }
            selectedRating = rating
            buttonRateUs.isEnabled = rating > 0
            buttonRateUs.alpha = if (rating > 0) 1f else 0.5f
        }
        
        stars.forEachIndexed { index, star ->
            star?.setOnClickListener {
                updateStars(index + 1)
            }
        }
        
        buttonRateUs?.setOnClickListener {
            if (selectedRating > 0) {
                bottomSheet.dismiss()
                openPlayStoreForRating()
            }
        }
        
        bottomSheet.show()
    }
    
    private fun exportMessages() {
        try {
            // Generate default filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val defaultFileName = "messages_backup_$timestamp.zip"
            
            // Create intent to open file picker with Downloads folder suggestion
            @Suppress("UNUSED_VARIABLE")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, defaultFileName)
                
                // Try to set initial URI to Downloads folder (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        @Suppress("UNUSED_VARIABLE")
                        val downloadsUri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val treeUri = DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:${Environment.DIRECTORY_DOWNLOADS}"
                        )
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                    } catch (e: Exception) {
                        Log.w("SettingsActivity", "Could not set initial URI to Downloads", e)
                    }
                }
            }
            
            exportFileLauncher.launch(defaultFileName)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error launching export file picker", e)
            Toast.makeText(this, getString(R.string.settings_error_opening_file_picker), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun importMessages() {
        try {
            // Create intent to open file picker with Downloads folder suggestion
            @Suppress("UNUSED_VARIABLE")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                
                // Try to set initial URI to Downloads folder (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val treeUri = DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:${Environment.DIRECTORY_DOWNLOADS}"
                        )
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                    } catch (e: Exception) {
                        Log.w("SettingsActivity", "Could not set initial URI to Downloads", e)
                    }
                }
            }
            
            importFileLauncher.launch(arrayOf("application/zip"))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error launching import file picker", e)
            Toast.makeText(this, getString(R.string.settings_error_opening_file_picker), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleExportResult(uri: Uri) {
        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_exporting_messages))
            .setMessage(getString(R.string.settings_please_wait))
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = MessagesExportImport.exportMessages(this@SettingsActivity, uri)
                progressDialog.dismiss()
                
                if (success) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.settings_messages_exported_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, getString(R.string.settings_failed_export_messages), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("SettingsActivity", "Error exporting messages", e)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_error_with_reason, e.message ?: getString(R.string.settings_unknown_error)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun handleImportResult(uri: Uri) {
        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_importing_messages))
            .setMessage(getString(R.string.settings_please_wait))
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val importedCount = MessagesExportImport.importMessages(this@SettingsActivity, uri)
                progressDialog.dismiss()
                
                if (importedCount > 0) {
                    Toast.makeText(
                        this@SettingsActivity,
                        resources.getQuantityString(R.plurals.settings_imported_messages_success, importedCount, importedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                    // Refresh the main activity if it's in the back stack
                    // The messages will be visible when user navigates back
                } else if (importedCount == 0) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.settings_no_new_messages_to_import), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, getString(R.string.settings_failed_import_messages), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("SettingsActivity", "Error importing messages", e)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_error_with_reason, e.message ?: getString(R.string.settings_unknown_error)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        themeChangeReceiver?.let {
            unregisterReceiver(it)
        }
    }
}

data class SettingsItem(
    val title: String,
    val options: List<SettingsOption>
)

private enum class PendingImExAction {
    EXPORT,
    IMPORT
}

enum class SettingsOptionId {
    DEFAULT_SMS_APP,
    CONTACTS_COLORED_ICONS,
    COLOR_SIM_CARD_ICONS,
    QUICK_ACCESS_TO_OTP,
    MANAGE_APPS,
    PRIVATE_CONVERSATIONS,
    SPAM_BLOCK,
    ARCHIVE,
    RECYCLE_BIN,
    SCHEDULE_MESSAGES,
    CALLER_SETTINGS,
    STARRED,
    SWIPE_GESTURES,
    ADD_SIGNATURE,
    NOTIFICATIONS,
    LANGUAGE,
    ADVANCE,
    FEEDBACK,
    SHARE_APP,
    RATE_US,
    EXPORT_MESSAGES,
    IMPORT_MESSAGES
}

data class SettingsOption(
    val id: SettingsOptionId,
    val title: String,
    val iconRes: Int?,
    val switchState: Boolean?,
    val isDefaultSms: Boolean,
    val onClick: (() -> Unit)? = null
)
