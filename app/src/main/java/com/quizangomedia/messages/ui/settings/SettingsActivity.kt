package com.quizangomedia.messages.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivitySettingsBinding
import com.quizangomedia.messages.ui.contacts.ContactsActivity
import com.quizangomedia.messages.ui.main.MainActivity
import com.quizangomedia.messages.ui.personalize.PersonalizeActivity
import com.quizangomedia.messages.ui.spam.SpamBlockActivity
import com.quizangomedia.messages.ui.private.PrivateConversationsActivity
import com.quizangomedia.messages.ui.language.LanguageActivity
import com.quizangomedia.messages.ui.manageapps.ManageAppsActivity
import com.quizangomedia.messages.util.ThemeManager
import com.quizangomedia.messages.util.ThemeChangeHelper
import android.content.BroadcastReceiver

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var adapter: SettingsAdapter
    private var isSettingSelectedItem = false
    private var themeChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle window insets - same as MainActivity
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to root so ad view stays above system navigation
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Bottom navigation should not have extra padding from window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { view, insets ->
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
                var resourceId = 0
                
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
            SettingsItem("General", listOf(
                SettingsOption("Default SMS apps Messages", getIcon("default_sms"), null, true) { 
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
                        Toast.makeText(this, "App is already set as default", Toast.LENGTH_SHORT).show()
                    } else {
                        // App is not default - open DefaultSmsActivity
                        startActivity(Intent(this, com.quizangomedia.messages.ui.defaultsms.DefaultSmsActivity::class.java).apply {
                            putExtra("from_settings", true)
                        })
                    }
                },
                SettingsOption("Contacts colored icons", getIcon("contacts"), true, false),
                SettingsOption("Color SIM card icons", getIcon("sim"), false, false),
                SettingsOption("Quick access to OTP", getIcon("otp"), true, false)
            )),
            SettingsItem("Go To", listOf(
                SettingsOption("Manage Apps", getIcon("manage"), null, false) { startActivity(Intent(this, ManageAppsActivity::class.java)) },
                SettingsOption("Private Conversations", getIcon("private_convo"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.pin.PinActivity::class.java)) },
                SettingsOption("Spam & Block", getIcon("spam"), null, false) { startActivity(Intent(this, SpamBlockActivity::class.java)) },
                SettingsOption("Archive", getIcon("archive"), null, false),
                SettingsOption("Recycle Bin", getIcon("recycle"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.recyclebin.RecycleBinActivity::class.java)) },
                SettingsOption("Schedule Messages", getIcon("schedule"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.scheduled.ScheduledMessagesActivity::class.java)) },
                SettingsOption("Caller Settings", getIcon("caller"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.caller.CallerSettingsActivity::class.java)) },
                SettingsOption("Starred", getIcon("starred"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.starred.StarredActivity::class.java)) },
                SettingsOption("Swipe Gestures", getIcon("swipe"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.swipe.SwipeGesturesActivity::class.java)) },
                SettingsOption("Add Signature", getIcon("signature"), null, false) { showSignatureDialog() },
                SettingsOption("Notifications", getIcon("notifications"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.notifications.NotificationsActivity::class.java)) },
                SettingsOption("Language", getIcon("language"), null, false) { 
                    startActivity(Intent(this, LanguageActivity::class.java).apply {
                        putExtra("from_settings", true)
                    })
                },
                SettingsOption("Advance", getIcon("advance"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.advance.AdvanceActivity::class.java)) },
                SettingsOption("Feedback", getIcon("feedback"), null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.feedback.FeedbackActivity::class.java)) },
                SettingsOption("Share App!", getIcon("share"), null, false),
                SettingsOption("Rate Us", getIcon("rate_us"), null, false) { showRateUsBottomSheet() }
            )),
            SettingsItem("Backups", listOf(
                SettingsOption("Export Messages", getIcon("export"), null, false),
                SettingsOption("Import Messages", getIcon("import_message"), null, false)
            ))
        )
        
        adapter = SettingsAdapter(settingsItems) { option ->
            option.onClick?.invoke()
        }
        
        binding.recyclerViewSettings.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSettings.adapter = adapter
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            // Ignore if we're programmatically setting the selected item
            if (isSettingSelectedItem) {
                return@setOnItemSelectedListener true
            }
            
            when (item.itemId) {
                R.id.nav_messages -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_contacts -> {
                    startActivity(Intent(this, ContactsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_personalize -> {
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
        setSelectedNavigationItem(R.id.nav_settings)
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
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
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
                // TODO: Open Play Store rating or handle rating submission
                bottomSheet.dismiss()
            }
        }
        
        bottomSheet.show()
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

data class SettingsOption(
    val title: String,
    val iconRes: Int?,
    val switchState: Boolean?,
    val isDefaultSms: Boolean,
    val onClick: (() -> Unit)? = null
)
