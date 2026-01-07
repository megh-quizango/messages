package com.text.messages.sms.messanger.ui.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.BroadcastReceiver
import com.text.messages.sms.messanger.databinding.FragmentContactsBinding
import com.text.messages.sms.messanger.ui.conversation.ConversationDetailActivity
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ContactAdapter
    private var allContacts: List<ContactListItem> = emptyList()
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadContacts()
        } else {
            Toast.makeText(
                requireContext(),
                "Contacts permission is required to display contacts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Apply theme
        ThemeManager.applyTheme(requireContext(), binding.root)
        
        setupBackButton()
        setupSearchBar()
        setupRecyclerView()
        
        // Request permission and load contacts
        checkContactsPermissionAndLoad()
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        themeChangeReceiver?.let {
            requireContext().unregisterReceiver(it)
        }
        _binding = null
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            // Navigate back to MainActivity with Messages tab selected
            val mainActivity = activity as? com.text.messages.sms.messanger.ui.main.MainActivity
            mainActivity?.navigateToMessages()
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
            val intent = Intent(requireContext(), ConversationDetailActivity::class.java)
            intent.putExtra("address", contact.phoneNumber)
            intent.putExtra("thread_id", 0L)
            startActivity(intent)
        }
        
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewContacts.adapter = adapter
    }
    
    private fun checkContactsPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
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
        val contentResolver: ContentResolver = requireContext().contentResolver
        
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
}

