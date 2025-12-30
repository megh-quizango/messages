package com.quizangomedia.messages.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupToolbar()
        setupRecyclerView()
        setupBottomNavigation()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = "Settings"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        val settingsItems = listOf(
            SettingsItem("General", listOf(
                SettingsOption("Default SMS apps Messages", R.drawable.messages, null, true),
                SettingsOption("Contacts colored icons", R.drawable.contacts, true, false),
                SettingsOption("Color SIM card icons", R.drawable.sim, false, false),
                SettingsOption("Quick access to OTP", R.drawable.otp, true, false)
            )),
            SettingsItem("Go To", listOf(
                SettingsOption("Manage Apps", R.drawable.manage, null, false) { startActivity(Intent(this, ManageAppsActivity::class.java)) },
                SettingsOption("Private Conversations", R.drawable.lock, null, false) { startActivity(Intent(this, PrivateConversationsActivity::class.java)) },
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
        binding.bottomNavigationView.selectedItemId = R.id.nav_settings
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
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
                R.id.nav_settings -> true
                else -> false
            }
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

