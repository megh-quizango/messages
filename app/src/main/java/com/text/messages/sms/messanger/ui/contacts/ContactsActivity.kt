package com.text.messages.sms.messanger.ui.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityContactsBinding
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.ui.personalize.PersonalizeActivity
import com.text.messages.sms.messanger.ui.settings.SettingsActivity
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : BaseActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var adapter: ContactAdapter
    private var allContacts: List<ContactListItem> = emptyList()
    private var isSettingSelectedItem = false
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadContacts()
        } else {
            Toast.makeText(
                this,
                "Contacts permission is required to display contacts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        android.util.Log.d("ContactsActivity", "=== ContactsActivity.onCreate() ===")
        AnalyticsHelper.logScreenView("ContactsActivity", "ContactsActivity")
        
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        android.util.Log.d("ContactsActivity", "ContactsActivity.onCreate(): Binding initialized")
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        // Check if opened from FAB
        val fromFab = intent.getBooleanExtra("from_fab", false)
        
        // Hide bottom navigation if opened from FAB
        if (fromFab) {
            binding.bottomNavigationView.visibility = View.GONE
            binding.adViewBanner.visibility = View.GONE
            
            // Update RecyclerView constraint to fill the space using ConstraintSet
            val constraintSet = ConstraintSet()
            constraintSet.clone(binding.root)
            constraintSet.clear(binding.recyclerViewContacts.id, ConstraintSet.BOTTOM)
            constraintSet.connect(binding.recyclerViewContacts.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            constraintSet.applyTo(binding.root)
        }
        
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
        
        // Fix bottom navigation padding (only if visible)
        if (!fromFab) {
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
        }
        
        setupBackButton()
        setupSearchBar()
        setupRecyclerView()
        if (!fromFab) {
            setupBottomNavigation()
            // Set Contacts as selected initially
            binding.bottomNavigationView.post {
                setSelectedNavigationItem(R.id.nav_contacts)
            }
        }
        setupBannerAd()
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
        
        // Request permission and load contacts
        checkContactsPermissionAndLoad()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupSearchBar() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterContacts(s?.toString() ?: "")
            }
        })
    }
    
    private fun setupRecyclerView() {
        adapter = ContactAdapter { contact ->
            // Open conversation with contact
            val intent = Intent(this, com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity::class.java)
            intent.putExtra("address", contact.phoneNumber)
            intent.putExtra("thread_id", 0L)
            startActivity(intent)
        }
        
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = adapter
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
                    // Already on Contacts screen
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
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure Contacts tab is selected when activity is visible
        if (!intent.getBooleanExtra("from_fab", false)) {
            binding.bottomNavigationView.post {
                setSelectedNavigationItem(R.id.nav_contacts)
            }
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
    
    private fun checkContactsPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadContacts()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
    
    private fun loadContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            val contacts = fetchContactsFromDevice()
            withContext(Dispatchers.Main) {
                allContacts = contacts
                adapter.submitList(contacts)
            }
        }
    }
    
    private fun filterContacts(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(allContacts)
            return
        }
        
        val filtered = mutableListOf<ContactListItem>()
        var currentSection: String? = null
        
        allContacts.forEach { item ->
            when (item) {
                is ContactListItem.Contact -> {
                    val contact = item.contact
                    val matches = contact.name.contains(query, ignoreCase = true) ||
                            contact.phoneNumber.contains(query, ignoreCase = true)
                    
                    if (matches) {
                        val firstLetter = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                        if (currentSection != firstLetter) {
                            filtered.add(ContactListItem.SectionHeader(firstLetter))
                            currentSection = firstLetter
                        }
                        filtered.add(item)
                    }
                }
                is ContactListItem.SectionHeader -> {
                    // Skip section headers when filtering
                }
            }
        }
        
        adapter.submitList(filtered)
    }
    
    private fun fetchContactsFromDevice(): List<ContactListItem> {
        val contactsList = mutableListOf<ContactItem>()
        val contentResolver: ContentResolver = contentResolver
        
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            
            val seenContacts = mutableSetOf<String>()
            
            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: ""
                val number = it.getString(numberIndex) ?: ""
                val photoUri = it.getString(photoIndex)
                
                // Skip if we've already seen this contact (handle multiple phone numbers)
                val contactKey = "$name|$number"
                if (seenContacts.contains(contactKey)) continue
                seenContacts.add(contactKey)
                
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    contactsList.add(ContactItem(name, number, photoUri))
                }
            }
        }
        
        // Sort contacts alphabetically
        val sortedContacts = contactsList.sortedBy { it.name.uppercase() }
        
        // Group by first letter and create section headers
        val result = mutableListOf<ContactListItem>()
        var currentLetter: String? = null
        
        sortedContacts.forEach { contact ->
            val firstLetter = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (currentLetter != firstLetter) {
                result.add(ContactListItem.SectionHeader(firstLetter))
                currentLetter = firstLetter
            }
            result.add(ContactListItem.Contact(contact))
        }
        
        return result
    }
    
    override fun onDestroy() {
        super.onDestroy()
        themeChangeReceiver?.let {
            unregisterReceiver(it)
        }
    }
}

data class ContactItem(
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)
