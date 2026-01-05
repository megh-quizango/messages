package com.quizangomedia.messages.ui.blocking

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityCustomBlockingBinding
import com.quizangomedia.messages.databinding.DialogAddBlacklistContactsBinding
import com.quizangomedia.messages.databinding.DialogAddKeywordBinding
import com.quizangomedia.messages.databinding.DialogAddPhoneNumberBinding
import com.quizangomedia.messages.databinding.DialogUnblockConfirmationBinding
import com.quizangomedia.messages.ui.blocking.overlay.ConversationSelectionActivity
import com.quizangomedia.messages.ui.blocking.overlay.ContactSelectionActivity
import com.quizangomedia.messages.util.ThemeManager

class CustomBlockingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomBlockingBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val keywordsList = mutableListOf<String>()
    private val contactsList = mutableListOf<BlockedContact>()
    private var isKeywordsTabSelected = true
    private lateinit var keywordsAdapter: KeywordsAdapter
    private lateinit var contactsAdapter: BlockedContactsAdapter
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "MessagesPrefs"
        private const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
        private const val KEY_BLOCKED_CONTACTS = "blocked_contacts"
        private const val REQUEST_CODE_SELECT_CONVERSATIONS = 1001
        private const val REQUEST_CODE_SELECT_CONTACTS = 1002
    }

    data class BlockedContact(
        val name: String,
        val phoneNumber: String,
        val photoUri: String? = null
    ) : android.os.Parcelable {
        constructor(parcel: android.os.Parcel) : this(
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString()
        )

        override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
            parcel.writeString(name)
            parcel.writeString(phoneNumber)
            parcel.writeString(photoUri)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : android.os.Parcelable.Creator<BlockedContact> {
            override fun createFromParcel(parcel: android.os.Parcel): BlockedContact {
                return BlockedContact(parcel)
            }

            override fun newArray(size: Int): Array<BlockedContact?> {
                return arrayOfNulls(size)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityCustomBlockingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Load blocked keywords and contacts from SharedPreferences
        loadBlockedKeywords()
        loadBlockedContacts()
        
        // Check if opened from Numbers option (Contacts tab should be selected)
        val fromNumbers = intent.getBooleanExtra("from_numbers", false)
        if (fromNumbers) {
            isKeywordsTabSelected = false
        }

        setupBackButton()
        setupTabs()
        setupKeywordsList()
        setupContactsList()
        setupBannerAd()
        updateUI()
    }

    private fun loadBlockedKeywords() {
        val keywordsJson = sharedPreferences.getString(KEY_BLOCKED_KEYWORDS, null)
        if (keywordsJson != null) {
            val type = object : TypeToken<List<String>>() {}.type
            val loaded = gson.fromJson<List<String>>(keywordsJson, type)
            keywordsList.clear()
            keywordsList.addAll(loaded)
        }
    }

    private fun saveBlockedKeywords() {
        val keywordsJson = gson.toJson(keywordsList)
        sharedPreferences.edit().putString(KEY_BLOCKED_KEYWORDS, keywordsJson).apply()
    }

    private fun loadBlockedContacts() {
        val contactsJson = sharedPreferences.getString(KEY_BLOCKED_CONTACTS, null)
        if (contactsJson != null) {
            val type = object : TypeToken<List<BlockedContact>>() {}.type
            val loaded = gson.fromJson<List<BlockedContact>>(contactsJson, type)
            contactsList.clear()
            contactsList.addAll(loaded)
        }
    }

    private fun saveBlockedContacts() {
        val contactsJson = gson.toJson(contactsList)
        sharedPreferences.edit().putString(KEY_BLOCKED_CONTACTS, contactsJson).apply()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupTabs() {
        binding.tabKeywords.setOnClickListener {
            if (!isKeywordsTabSelected) {
                isKeywordsTabSelected = true
                updateUI()
            }
        }

        binding.tabContacts.setOnClickListener {
            if (isKeywordsTabSelected) {
                isKeywordsTabSelected = false
                updateUI()
            }
        }
    }

    private fun updateUI() {
        val themeColor = ThemeManager.getThemeColor(this)
        val themeColorLight = ThemeManager.getThemeColorLight(this)
        
        if (isKeywordsTabSelected) {
            // Keywords tab selected
            binding.tabKeywords.setBackgroundResource(R.drawable.bg_tab_connected_selected_left)
            binding.tabKeywords.setTextColor(getColor(R.color.white))
            // Apply theme color to selected tab background dynamically
            val selectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(12f, 12f, 0f, 0f, 12f, 12f, 0f, 0f)
                setColor(themeColor)
            }
            binding.tabKeywords.background = selectedDrawable
            
            binding.tabContacts.setBackgroundResource(R.drawable.bg_tab_connected_unselected_right)
            binding.tabContacts.setTextColor(getColor(R.color.black))
            // Apply theme light color to unselected tab
            val unselectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(0f, 0f, 12f, 12f, 0f, 0f, 12f, 12f)
                setColor(themeColorLight)
            }
            binding.tabContacts.background = unselectedDrawable

            binding.layoutKeywordsContent.visibility = View.VISIBLE
            binding.layoutContactsContent.visibility = View.GONE

            updateKeywordsUI()
        } else {
            // Contacts tab selected
            binding.tabContacts.setBackgroundResource(R.drawable.bg_tab_connected_selected_right)
            binding.tabContacts.setTextColor(getColor(R.color.white))
            // Apply theme color to selected tab background dynamically
            val selectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(0f, 0f, 12f, 12f, 0f, 0f, 12f, 12f)
                setColor(themeColor)
            }
            binding.tabContacts.background = selectedDrawable
            
            binding.tabKeywords.setBackgroundResource(R.drawable.bg_tab_connected_unselected_left)
            binding.tabKeywords.setTextColor(getColor(R.color.black))
            // Apply theme light color to unselected tab
            val unselectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(12f, 12f, 0f, 0f, 12f, 12f, 0f, 0f)
                setColor(themeColorLight)
            }
            binding.tabKeywords.background = unselectedDrawable

            binding.layoutKeywordsContent.visibility = View.GONE
            binding.layoutContactsContent.visibility = View.VISIBLE

            updateContactsUI()
        }
    }

    private fun updateKeywordsUI() {
        if (keywordsList.isEmpty()) {
            binding.layoutKeywordsEmpty.visibility = View.VISIBLE
            binding.recyclerViewKeywords.visibility = View.GONE
            binding.fabAddKeyword.visibility = View.GONE
        } else {
            binding.layoutKeywordsEmpty.visibility = View.GONE
            binding.recyclerViewKeywords.visibility = View.VISIBLE
            binding.fabAddKeyword.visibility = View.VISIBLE
            keywordsAdapter.submitList(keywordsList.toList())
        }
    }

    private fun updateContactsUI() {
        if (contactsList.isEmpty()) {
            binding.layoutContactsEmpty.visibility = View.VISIBLE
            binding.recyclerViewContacts.visibility = View.GONE
            binding.fabAddContact.visibility = View.GONE
        } else {
            binding.layoutContactsEmpty.visibility = View.GONE
            binding.recyclerViewContacts.visibility = View.VISIBLE
            binding.fabAddContact.visibility = View.VISIBLE
            contactsAdapter.submitList(contactsList.toList())
        }
    }

    private fun setupKeywordsList() {
        keywordsAdapter = KeywordsAdapter(
            onUnblockClick = { keyword ->
                showUnblockKeywordDialog(keyword)
            }
        )
        binding.recyclerViewKeywords.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewKeywords.adapter = keywordsAdapter

        // Apply theme to add keyword buttons
        val themeColor = ThemeManager.getThemeColor(this)
        binding.buttonAddKeywords.backgroundTintList = null
        binding.buttonAddKeywords.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.fabAddKeyword.backgroundTintList = null
        binding.fabAddKeyword.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)

        binding.buttonAddKeywords.setOnClickListener {
            showAddKeywordDialog()
        }

        binding.fabAddKeyword.setOnClickListener {
            showAddKeywordDialog()
        }
    }

    private fun setupContactsList() {
        contactsAdapter = BlockedContactsAdapter(
            onUnblockClick = { contact ->
                showUnblockContactDialog(contact)
            }
        )
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = contactsAdapter

        // Apply theme to add contact buttons
        val themeColor = ThemeManager.getThemeColor(this)
        binding.buttonAddContacts.backgroundTintList = null
        binding.buttonAddContacts.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.fabAddContact.backgroundTintList = null
        binding.fabAddContact.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)

        binding.buttonAddContacts.setOnClickListener {
            showAddBlacklistContactsDialog()
        }

        binding.fabAddContact.setOnClickListener {
            showAddBlacklistContactsDialog()
        }
    }

    private fun showAddKeywordDialog() {
        val dialogBinding = DialogAddKeywordBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Apply theme to dialog
        ThemeManager.applyThemeToDialog(this, dialogBinding.root)

        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonAdd.setOnClickListener {
            val keyword = dialogBinding.editTextKeyword.text.toString().trim()
            if (keyword.isNotEmpty()) {
                keywordsList.add(keyword)
                saveBlockedKeywords()
                updateKeywordsUI()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a keyword", Toast.LENGTH_SHORT).show()
            }
        }

        // Apply theme after dialog is shown
        dialog.setOnShowListener {
            ThemeManager.applyTheme(this, dialogBinding.root)
        }
        dialog.show()
    }

    private fun showAddPhoneNumberDialog() {
        val dialogBinding = DialogAddPhoneNumberBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Apply theme to dialog
        ThemeManager.applyThemeToDialog(this, dialogBinding.root)

        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonAdd.setOnClickListener {
            val phoneNumber = dialogBinding.editTextPhoneNumber.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                contactsList.add(BlockedContact(name = phoneNumber, phoneNumber = phoneNumber))
                saveBlockedContacts()
                updateContactsUI()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // Apply theme after dialog is shown
        dialog.setOnShowListener {
            ThemeManager.applyTheme(this, dialogBinding.root)
        }
        dialog.show()
    }

    private fun showAddBlacklistContactsDialog() {
        val dialogBinding = DialogAddBlacklistContactsBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Apply theme to dialog
        ThemeManager.applyThemeToDialog(this, dialogBinding.root)

        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.optionConversationList.setOnClickListener {
            dialog.dismiss()
            startActivityForResult(
                Intent(this, ConversationSelectionActivity::class.java),
                REQUEST_CODE_SELECT_CONVERSATIONS
            )
        }

        dialogBinding.optionContactsList.setOnClickListener {
            dialog.dismiss()
            startActivityForResult(
                Intent(this, ContactSelectionActivity::class.java),
                REQUEST_CODE_SELECT_CONTACTS
            )
        }

        dialogBinding.optionAddPhoneNumber.setOnClickListener {
            dialog.dismiss()
            showAddPhoneNumberDialog()
        }

        // Apply theme after dialog is shown
        dialog.setOnShowListener {
            ThemeManager.applyTheme(this, dialogBinding.root)
        }
        dialog.show()
    }

    private fun showUnblockKeywordDialog(keyword: String) {
        val dialogBinding = DialogUnblockConfirmationBinding.inflate(LayoutInflater.from(this))
        dialogBinding.textTitle.text = keyword
        dialogBinding.textMessage.text = "Do you want to unblock this keyword from blacklist?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Apply theme to dialog
        ThemeManager.applyTheme(this, dialogBinding.root)

        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonUnblock.setOnClickListener {
            keywordsList.remove(keyword)
            saveBlockedKeywords()
            updateKeywordsUI()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showUnblockContactDialog(contact: BlockedContact) {
        val dialogBinding = DialogUnblockConfirmationBinding.inflate(LayoutInflater.from(this))
        dialogBinding.textTitle.text = contact.name
        dialogBinding.textMessage.text = "Do you want to unblock this contact from blacklist?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Apply theme to dialog
        ThemeManager.applyThemeToDialog(this, dialogBinding.root)

        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonUnblock.setOnClickListener {
            contactsList.remove(contact)
            saveBlockedContacts()
            updateContactsUI()
            dialog.dismiss()
        }

        // Apply theme after dialog is shown
        dialog.setOnShowListener {
            ThemeManager.applyTheme(this, dialogBinding.root)
        }
        dialog.show()
    }

    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_SELECT_CONVERSATIONS -> {
                    val selectedConversations = data?.getParcelableArrayListExtra<BlockedContact>("selected_contacts")
                    selectedConversations?.let {
                        // Filter out duplicates
                        val existingPhoneNumbers = contactsList.map { it.phoneNumber }.toSet()
                        val newContacts = it.filter { !existingPhoneNumbers.contains(it.phoneNumber) }
                        contactsList.addAll(newContacts)
                        saveBlockedContacts()
                        updateContactsUI()
                    }
                }
                REQUEST_CODE_SELECT_CONTACTS -> {
                    val selectedContacts = data?.getParcelableArrayListExtra<BlockedContact>("selected_contacts")
                    selectedContacts?.let {
                        // Filter out duplicates
                        val existingPhoneNumbers = contactsList.map { it.phoneNumber }.toSet()
                        val newContacts = it.filter { !existingPhoneNumbers.contains(it.phoneNumber) }
                        contactsList.addAll(newContacts)
                        saveBlockedContacts()
                        updateContactsUI()
                    }
                }
            }
        }
    }
}
