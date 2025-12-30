package com.quizangomedia.messages.ui.contacts

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityContactsBinding
import com.quizangomedia.messages.ui.main.MainActivity
import com.quizangomedia.messages.ui.personalize.PersonalizeActivity
import com.quizangomedia.messages.ui.settings.SettingsActivity

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityContactsBinding.inflate(layoutInflater)
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
        binding.toolbar.title = "Contacts"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ContactAdapter { contact ->
            // Open conversation with contact
            val intent = Intent(this, com.quizangomedia.messages.ui.conversation.ConversationDetailActivity::class.java)
            intent.putExtra("address", contact.phoneNumber)
            startActivity(intent)
        }
        
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = adapter
        
        // TODO: Load contacts from device
        adapter.submitList(emptyList())
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.nav_contacts
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_messages -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_contacts -> true
                R.id.nav_personalize -> {
                    startActivity(Intent(this, PersonalizeActivity::class.java))
                    finish()
                    true
                }
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

data class ContactItem(
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)

