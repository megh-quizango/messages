package com.quizangomedia.messages.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
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
                SettingsOption("Recycle Bin", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.recyclebin.RecycleBinActivity::class.java)) },
                SettingsOption("Schedule Messages", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.scheduled.ScheduledMessagesActivity::class.java)) },
                SettingsOption("Caller Settings", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.caller.CallerSettingsActivity::class.java)) },
                SettingsOption("Starred", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.starred.StarredActivity::class.java)) },
                SettingsOption("Swipe Gestures", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.swipe.SwipeGesturesActivity::class.java)) },
                SettingsOption("Add Signature", null, null, false) { showSignatureDialog() },
                SettingsOption("Notifications", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.notifications.NotificationsActivity::class.java)) },
                SettingsOption("Language", null, null, false) { 
                    startActivity(Intent(this, LanguageActivity::class.java).apply {
                        putExtra("from_settings", true)
                    })
                },
                SettingsOption("Advance", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.advance.AdvanceActivity::class.java)) },
                SettingsOption("Feedback", null, null, false) { startActivity(Intent(this, com.quizangomedia.messages.ui.feedback.FeedbackActivity::class.java)) },
                SettingsOption("Share App!", null, null, false),
                SettingsOption("Rate Us", null, null, false) { showRateUsBottomSheet() }
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
