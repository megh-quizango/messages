package com.quizangomedia.messages.ui.personalize

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityPersonalizeBinding
import com.quizangomedia.messages.ui.contacts.ContactsActivity
import com.quizangomedia.messages.ui.main.MainActivity
import com.quizangomedia.messages.ui.settings.SettingsActivity

class PersonalizeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalizeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityPersonalizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupToolbar()
        setupBottomNavigation()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = "Personalize"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.nav_personalize
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
                R.id.nav_personalize -> true
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}

