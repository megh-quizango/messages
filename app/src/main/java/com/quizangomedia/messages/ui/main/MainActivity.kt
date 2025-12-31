package com.quizangomedia.messages.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityMainBinding
import com.quizangomedia.messages.ui.compose.ComposeActivity
import com.quizangomedia.messages.ui.contacts.ContactsActivity
import com.quizangomedia.messages.ui.personalize.PersonalizeActivity
import com.quizangomedia.messages.ui.settings.SettingsActivity
import com.quizangomedia.messages.ui.conversation.ConversationDetailActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ConversationAdapter
    private var selectedTab: TextView? = null
    private var isSettingSelectedItem = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, load conversations
            viewModel.loadConversations("All")
        } else {
            // Permission denied
            Toast.makeText(
                this,
                "SMS permission is required to display messages",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Remove extra padding from internal menu container while preserving outer padding
        binding.bottomNavigationView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.bottomNavigationView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // Preserve the padding we set in XML (8dp top and bottom) for breathing room
                val topPadding = binding.bottomNavigationView.paddingTop
                val bottomPadding = binding.bottomNavigationView.paddingBottom
                binding.bottomNavigationView.setPadding(0, topPadding, 0, bottomPadding)
                binding.bottomNavigationView.minimumHeight = 0
                
                // Remove padding from the internal menu container (usually a LinearLayout)
                val menuView = binding.bottomNavigationView.getChildAt(0) as? ViewGroup
                menuView?.let {
                    it.setPadding(0, 0, 0, 0)
                    it.minimumHeight = 0
                    
                    // Remove padding from individual menu item views
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        child?.let { item ->
                            if (item is ViewGroup) {
                                // Keep horizontal padding but remove vertical
                                item.setPadding(item.paddingLeft, 0, item.paddingRight, 0)
                                item.minimumHeight = 0
                            }
                        }
                    }
                }
            }
        })




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



        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupTabs()
        setupRecyclerView()
        setupFab()
        setupBottomNavigation()
        setupBannerAd()
        observeConversations()
        
        // Request SMS permission and load conversations
        checkSmsPermissionAndLoad()
        
        // Set Messages as selected initially
        binding.bottomNavigationView.post {
            setSelectedNavigationItem(R.id.nav_messages)
        }
    }
    
    private fun checkSmsPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, load conversations
                viewModel.loadConversations("All")
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }
    
    private fun setupTabs() {
        // Set "All" as initially selected
        selectedTab = binding.tabAll
        binding.tabAll.setOnClickListener { selectTab(it as TextView) }
        binding.tabPersonal.setOnClickListener { selectTab(it as TextView) }
        binding.tabOTPs.setOnClickListener { selectTab(it as TextView) }
        binding.tabOffers.setOnClickListener { selectTab(it as TextView) }
        binding.tabTransactions.setOnClickListener { selectTab(it as TextView) }
    }
    
    private fun selectTab(tab: TextView) {
        // Deselect previous tab
        selectedTab?.let {
            it.setBackgroundResource(R.drawable.bg_tab_unselected)
            it.setTextColor(getColor(R.color.black))
        }
        
        // Select new tab
        tab.setBackgroundResource(R.drawable.bg_tab_selected)
        tab.setTextColor(getColor(R.color.white))
        selectedTab = tab
        
        // Filter conversations by category
        val category = when (tab.id) {
            R.id.tabAll -> "All"
            R.id.tabPersonal -> "Personal"
            R.id.tabOTPs -> "OTPs"
            R.id.tabOffers -> "Offers"
            R.id.tabTransactions -> "Transactions"
            else -> "All"
        }
        viewModel.loadConversations(category)
    }
    
    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
            val intent = Intent(this, ConversationDetailActivity::class.java)
            intent.putExtra("thread_id", conversation.threadId)
            intent.putExtra("address", conversation.address)
            startActivity(intent)
        }
        
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConversations.adapter = adapter
    }
    
    private fun setupFab() {
        binding.fabStartChat.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            // Ignore if we're programmatically setting the selected item
            if (isSettingSelectedItem) {
                return@setOnItemSelectedListener true
            }
            
            when (item.itemId) {
                R.id.nav_messages -> {
                    // Already on Messages screen
                    true
                }
                R.id.nav_contacts -> {
                    startActivity(Intent(this, ContactsActivity::class.java))
                    true
                }
                R.id.nav_personalize -> {
                    startActivity(Intent(this, PersonalizeActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure Messages tab is selected when activity is visible
        setSelectedNavigationItem(R.id.nav_messages)
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
    
    private fun observeConversations() {
        viewModel.conversations.observe(this) { conversations ->
            adapter.submitList(conversations)
        }
    }
}
