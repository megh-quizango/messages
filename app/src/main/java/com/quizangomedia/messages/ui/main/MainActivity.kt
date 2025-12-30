package com.quizangomedia.messages.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.dp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
//        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNavigationView.post {
            val menuView = binding.bottomNavigationView.getChildAt(0)
            menuView.setPadding(0, 0, 0, 0)
            menuView.minimumHeight = 0
        }



//        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomContainer) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(0, 0, 0, bottomInset)
            insets
        }



        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupTabs()
        setupRecyclerView()
        setupFab()
        setupBottomNavigation()
        setupBannerAd()
        observeConversations()
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
        
        // Set Messages as selected
        binding.bottomNavigationView.selectedItemId = R.id.nav_messages
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
    
    private fun observeConversations() {
        viewModel.conversations.observe(this) { conversations ->
            adapter.submitList(conversations)
        }
        
        viewModel.loadConversations("All")
    }
}
