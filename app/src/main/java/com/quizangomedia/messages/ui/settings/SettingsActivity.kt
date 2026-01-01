package com.quizangomedia.messages.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
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

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var adapter: SettingsAdapter
    private var isSettingSelectedItem = false

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
        
        // Set Settings as selected initially
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_settings)
        }
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        val settingsItems = listOf(
            SettingsItem("General", listOf(
                SettingsOption("Default SMS apps Messages", null, null, true),
                SettingsOption("Contacts colored icons", null, true, false),
                SettingsOption("Color SIM card icons", R.drawable.sim, false, false),
                SettingsOption("Quick access to OTP", R.drawable.otp, true, false)
            )),
            SettingsItem("Go To", listOf(
                SettingsOption("Manage Apps", R.drawable.manage, null, false) { startActivity(Intent(this, ManageAppsActivity::class.java)) },
                SettingsOption("Private Conversations", R.drawable.lock, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.pin.PinActivity::class.java)) },
                SettingsOption("Spam & Block", R.drawable.spam, null, false) { startActivity(Intent(this, SpamBlockActivity::class.java)) },
                SettingsOption("Archive", R.drawable.archive, null, false),
                SettingsOption("Recycle Bin", null, null, false),
                SettingsOption("Schedule Messages", null, null, false),
                SettingsOption("Caller Settings", null, null, false),
                SettingsOption("Starred", null, null, false),
                SettingsOption("Swipe Gestures", null, null, false),
                SettingsOption("Add Signature", null, null, false),
                SettingsOption("Notifications", null, null, false),
                SettingsOption("Language", null, null, false) { startActivity(Intent(this, LanguageActivity::class.java)) },
                SettingsOption("Advance", null, null, false),
                SettingsOption("Feedback", null, null, false),
                SettingsOption("Share App!", null, null, false),
                SettingsOption("Rate Us", null, null, false)
            )),
            SettingsItem("Backups", listOf(
                SettingsOption("Export Messages", null, null, false),
                SettingsOption("Import Messages", null, null, false)
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
        if (binding.bottomNavigationView.selectedItemId != itemId) {
            isSettingSelectedItem = true
            binding.bottomNavigationView.selectedItemId = itemId
            binding.bottomNavigationView.post {
                isSettingSelectedItem = false
            }
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
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
