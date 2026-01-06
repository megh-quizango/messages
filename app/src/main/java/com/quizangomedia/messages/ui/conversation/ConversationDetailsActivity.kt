package com.quizangomedia.messages.ui.conversation

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quizangomedia.messages.MessagesApp
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityConversationDetailsBinding
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.util.AvatarHelper
import com.quizangomedia.messages.util.ThemeManager
import io.realm.kotlin.query.RealmQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ConversationDetails"
    }

    private lateinit var binding: ActivityConversationDetailsBinding
    private var threadId: Long = -1
    private var address: String = ""
    private var contactName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        threadId = intent.getLongExtra("thread_id", -1)
        address = intent.getStringExtra("address") ?: ""
        contactName = intent.getStringExtra("contact_name") ?: ""

        // If contact name is not provided, look it up
        if (contactName.isEmpty() && address.isNotEmpty()) {
            contactName = lookupContactName(address) ?: address
        }

        binding = ActivityConversationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup navigation bar
        ThemeManager.setupNavigationBar(this)

        // Apply theme
        ThemeManager.applyTheme(this, binding.root)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBackButton()
        setupContactInfo()
        setupOptions()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupContactInfo() {
        // Set contact name
        binding.textContactName.text = contactName

        // Set phone number
        binding.textContactNumber.text = address

        // Load avatar
        val photoUri = getContactPhotoUri()
        AvatarHelper.loadAvatar(
            imageView = binding.imageContact,
            textView = binding.textAvatarLetter,
            photoUri = photoUri,
            contactName = contactName,
            address = address,
            context = this
        )
    }

    private fun setupOptions() {
        // Archive option
        binding.optionArchive.setOnClickListener {
            archiveConversation()
        }

        // Block option
        binding.optionBlock.setOnClickListener {
            blockConversation()
        }

        // Delete conversation option
        binding.optionDelete.setOnClickListener {
            deleteConversation()
        }
    }

    private fun archiveConversation() {
        if (threadId <= 0) {
            Toast.makeText(this, "Unable to archive conversation", Toast.LENGTH_SHORT).show()
            return
        }

        val currentContactName = contactName
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val realm = MessagesApp.realm
                realm.writeBlocking {
                    val conversation = query(Conversation::class, "threadId == $threadId").first().find()
                    if (conversation != null) {
                        findLatest(conversation)?.archived = true
                        Log.d(TAG, "Conversation archived - threadId: $threadId")
                    } else {
                        // Create conversation if it doesn't exist
                        copyToRealm(Conversation().apply {
                            this.threadId = threadId
                            this.address = address
                            this.contactName = if (currentContactName.isNotEmpty()) currentContactName else null
                            this.archived = true
                            this.date = System.currentTimeMillis()
                        })
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConversationDetailsActivity, "Conversation archived", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error archiving conversation", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConversationDetailsActivity, "Failed to archive conversation", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun blockConversation() {
        if (threadId <= 0) {
            Toast.makeText(this, "Unable to block conversation", Toast.LENGTH_SHORT).show()
            return
        }

        val currentContactName = contactName
        AlertDialog.Builder(this)
            .setTitle("Block Conversation")
            .setMessage("Are you sure you want to block this conversation? You will not receive messages from this contact.")
            .setPositiveButton("Block") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val realm = MessagesApp.realm
                        realm.writeBlocking {
                            val conversation = query(Conversation::class, "threadId == $threadId").first().find()
                            if (conversation != null) {
                                findLatest(conversation)?.blocked = true
                                Log.d(TAG, "Conversation blocked - threadId: $threadId")
                            } else {
                                // Create conversation if it doesn't exist
                                copyToRealm(Conversation().apply {
                                    this.threadId = threadId
                                    this.address = address
                                    this.contactName = if (currentContactName.isNotEmpty()) currentContactName else null
                                    this.blocked = true
                                    this.date = System.currentTimeMillis()
                                })
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Conversation blocked", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error blocking conversation", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Failed to block conversation", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteConversation() {
        if (threadId <= 0) {
            Toast.makeText(this, "Unable to delete conversation", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Delete from Realm
                        val realm = MessagesApp.realm
                        realm.writeBlocking {
                            // Delete conversation
                            val conversation = query(Conversation::class, "threadId == $threadId").first().find()
                            conversation?.let {
                                findLatest(it)?.let { conv ->
                                    delete(conv)
                                }
                            }

                            // Delete all messages in this thread
                            val messages = query(com.quizangomedia.messages.data.model.Message::class, "threadId == $threadId").find()
                            messages.forEach { message ->
                                findLatest(message)?.let {
                                    delete(it)
                                }
                            }
                        }

                        // Delete from SMS database
                        try {
                            val uri = Telephony.Sms.CONTENT_URI
                            val selection = "${Telephony.Sms.THREAD_ID} = ?"
                            val selectionArgs = arrayOf(threadId.toString())
                            contentResolver.delete(uri, selection, selectionArgs)
                            Log.d(TAG, "Deleted messages from SMS database - threadId: $threadId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting from SMS database", e)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Conversation deleted", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting conversation", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ConversationDetailsActivity, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun lookupContactName(phoneNumber: String): String? {
        fun normalizePhoneNumber(phone: String): String {
            var normalized = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
            if (normalized.startsWith("+")) {
                normalized = normalized.substring(1)
            }
            if (normalized.length > 10) {
                if (normalized.startsWith("91") && normalized.length == 12) {
                    normalized = normalized.substring(2)
                } else if (normalized.startsWith("0") && normalized.length == 11) {
                    normalized = normalized.substring(1)
                }
            }
            if (normalized.length > 10) {
                normalized = normalized.takeLast(10)
            }
            return normalized
        }

        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val variations = listOf(
            normalizedNumber,
            phoneNumber,
            if (normalizedNumber.length > 10) normalizedNumber.takeLast(10) else null
        ).filterNotNull().distinct()

        for (number in variations) {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )

            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) {
                            return name
                        }
                    }
                }
            }
        }

        return null
    }

    private fun getContactPhotoUri(): String? {
        if (address.isEmpty()) return null

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )

            val projection = arrayOf(ContactsContract.PhoneLookup._ID)

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                    val photoUri = ContactsContract.Contacts.getLookupUri(contactId, null)
                    return photoUri?.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact photo URI", e)
        }

        return null
    }
}

