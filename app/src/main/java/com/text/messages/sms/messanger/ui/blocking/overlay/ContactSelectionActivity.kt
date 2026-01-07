package com.text.messages.sms.messanger.ui.blocking.overlay

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.text.messages.sms.messanger.databinding.ActivityContactSelectionBinding
import com.text.messages.sms.messanger.ui.blocking.CustomBlockingActivity
import com.text.messages.sms.messanger.util.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactSelectionBinding
    private lateinit var adapter: ContactSelectionAdapter
    private val selectedContacts = mutableSetOf<String>()
    private var allContacts: List<ContactItem> = emptyList()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadContacts()
        } else {
            Toast.makeText(
                this,
                "Contacts permission is required",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    data class ContactItem(
        val name: String,
        val phoneNumber: String,
        val photoUri: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityContactSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply theme
        ThemeManager.applyTheme(this, binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        setupDoneButton()
        
        // Set backgroundTint to null for done button and apply theme color directly
        binding.buttonDone.backgroundTintList = null
        val themeColor = ThemeManager.getThemeColor(this)
        binding.buttonDone.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        checkContactsPermissionAndLoad()
        
        // Apply theme after views are laid out
        binding.root.post {
            ThemeManager.applyTheme(this, binding.root)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactSelectionAdapter(
            onItemClick = { phoneNumber ->
                if (selectedContacts.contains(phoneNumber)) {
                    selectedContacts.remove(phoneNumber)
                } else {
                    selectedContacts.add(phoneNumber)
                }
                adapter.notifyDataSetChanged()
            },
            isSelected = { phoneNumber ->
                selectedContacts.contains(phoneNumber)
            }
        )
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = adapter
    }

    private fun setupDoneButton() {
        binding.buttonDone.setOnClickListener {
            val blockedContacts = allContacts
                .filter { selectedContacts.contains(it.phoneNumber) }
                .map { contact ->
                    CustomBlockingActivity.BlockedContact(
                        name = contact.name,
                        phoneNumber = contact.phoneNumber,
                        photoUri = contact.photoUri
                    )
                }

            val resultIntent = Intent().apply {
                putParcelableArrayListExtra("selected_contacts", ArrayList(blockedContacts))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
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

    private fun fetchContactsFromDevice(): List<ContactItem> {
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

                val contactKey = "$name|$number"
                if (seenContacts.contains(contactKey)) continue
                seenContacts.add(contactKey)

                if (name.isNotEmpty() && number.isNotEmpty()) {
                    contactsList.add(ContactItem(name, number, photoUri))
                }
            }
        }

        return contactsList.sortedBy { it.name.uppercase() }
    }
}

