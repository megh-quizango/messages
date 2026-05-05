package com.text.messages.sms.messanger.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Telephony
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.text.messages.sms.messanger.databinding.FragmentSettingsBinding
import com.text.messages.sms.messanger.ui.advance.AdvanceActivity
import com.text.messages.sms.messanger.ui.feedback.FeedbackActivity
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.ui.manageapps.ManageAppsActivity
import com.text.messages.sms.messanger.ui.notifications.NotificationsActivity
import com.text.messages.sms.messanger.ui.pin.PinActivity
import com.text.messages.sms.messanger.ui.recyclebin.RecycleBinActivity
import com.text.messages.sms.messanger.ui.scheduled.ScheduledMessagesActivity
import com.text.messages.sms.messanger.ui.spam.SpamBlockActivity
import com.text.messages.sms.messanger.ui.starred.StarredActivity
import com.text.messages.sms.messanger.ui.swipe.SwipeGesturesActivity
import com.text.messages.sms.messanger.ui.caller.CallerSettingsActivity
import android.content.BroadcastReceiver
import com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity
import com.text.messages.sms.messanger.util.MessagesExportImport
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.text.messages.sms.messanger.ui.archive.ArchiveActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: SettingsAdapter
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    // Activity result launchers for file picker
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            handleExportResult(it)
        }
    }
    
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            handleImportResult(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupBackButton()
        setupRecyclerView()
        
        // Apply theme after views are created
        binding.root.post {
            ThemeManager.applyTheme(requireContext(), binding.root)
        }
        // Also apply immediately
        ThemeManager.applyTheme(requireContext(), binding.root)
        
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
    
    private fun setupRecyclerView() {
        val settingsItems = listOf(
            SettingsItem("General", listOf(
                SettingsOption("Default SMS apps Messages", com.text.messages.sms.messanger.R.drawable.default_sms, null, true) {
                    // Check if app is already default SMS app
                    val isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = requireContext().getSystemService(RoleManager::class.java)
                        roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                    } else {
                        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(requireContext())
                        defaultSmsPackage != null && requireContext().packageName == defaultSmsPackage
                    }
                    
                    if (isDefaultSmsApp) {
                        Toast.makeText(requireContext(), "App is already set as default", Toast.LENGTH_SHORT).show()
                    } else {
                        startActivity(Intent(requireContext(), DefaultSmsActivity::class.java).apply {
                            putExtra("from_settings", true)
                        })
                    }
                },
                SettingsOption("Contacts colored icons", com.text.messages.sms.messanger.R.drawable.contacts, true, false),
                SettingsOption("Color SIM card icons", com.text.messages.sms.messanger.R.drawable.sim, false, false),
                SettingsOption("Quick access to OTP", com.text.messages.sms.messanger.R.drawable.otp, true, false)
            )),
            SettingsItem("Go To", listOf(
                SettingsOption("Manage Apps", com.text.messages.sms.messanger.R.drawable.manage, null, false) { 
                    startActivity(Intent(requireContext(), ManageAppsActivity::class.java)) 
                },
                SettingsOption("Private Conversations", com.text.messages.sms.messanger.R.drawable.private_convo, null, false) {
                    startActivity(Intent(requireContext(), PinActivity::class.java)) 
                },
                SettingsOption("Spam & Block", com.text.messages.sms.messanger.R.drawable.spam, null, false) { 
                    startActivity(Intent(requireContext(), SpamBlockActivity::class.java)) 
                },
                SettingsOption("Archive", com.text.messages.sms.messanger.R.drawable.archive, null, false){
                    startActivity(Intent(requireContext(), ArchiveActivity::class.java))
                },
                SettingsOption("Recycle Bin", com.text.messages.sms.messanger.R.drawable.recycle, null, false) {
                    startActivity(Intent(requireContext(), RecycleBinActivity::class.java)) 
                },
                SettingsOption("Schedule Messages", com.text.messages.sms.messanger.R.drawable.schedule, null, false) {
                    startActivity(Intent(requireContext(), ScheduledMessagesActivity::class.java)) 
                },
                SettingsOption("Caller Settings", com.text.messages.sms.messanger.R.drawable.caller, null, false) {
                    startActivity(Intent(requireContext(), CallerSettingsActivity::class.java)) 
                },
                SettingsOption("Starred", com.text.messages.sms.messanger.R.drawable.starred, null, false) {
                    startActivity(Intent(requireContext(), StarredActivity::class.java)) 
                },
                SettingsOption("Swipe Gestures", com.text.messages.sms.messanger.R.drawable.swipe, null, false) {
                    startActivity(Intent(requireContext(), SwipeGesturesActivity::class.java)) 
                },
                SettingsOption("Add Signature", com.text.messages.sms.messanger.R.drawable.signature, null, false) {
                    showSignatureDialog()
                },
                SettingsOption("Notifications", com.text.messages.sms.messanger.R.drawable.notifications, null, false) {
                    startActivity(Intent(requireContext(), NotificationsActivity::class.java)) 
                },
                SettingsOption("Language", com.text.messages.sms.messanger.R.drawable.language, null, false) {
                    startActivity(Intent(requireContext(), LanguageActivity::class.java).apply {
                        putExtra("from_settings", true)
                    })
                },
                SettingsOption("Advance", com.text.messages.sms.messanger.R.drawable.advance, null, false) {
                    startActivity(Intent(requireContext(), AdvanceActivity::class.java)) 
                },
                SettingsOption("Feedback", com.text.messages.sms.messanger.R.drawable.feedback, null, false) {
                    startActivity(Intent(requireContext(), FeedbackActivity::class.java)) 
                },
                SettingsOption("Share App!", com.text.messages.sms.messanger.R.drawable.share, null, false) {
                    openPlayStoreForSharing()
                },
                SettingsOption("Rate Us", com.text.messages.sms.messanger.R.drawable.rate_us, null, false) {
                    showRateUsBottomSheet()
                }
            )),
            SettingsItem("Backups", listOf(
                SettingsOption("Export Messages", com.text.messages.sms.messanger.R.drawable.export, null, false) {
                    exportMessages()
                },
                SettingsOption("Import Messages", com.text.messages.sms.messanger.R.drawable.import_message, null, false) {
                    importMessages()
                }
            ))
        )
        
        adapter = SettingsAdapter(settingsItems) { option ->
            option.onClick?.invoke()
        }
        
        binding.recyclerViewSettings.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSettings.adapter = adapter
    }
    
    private fun showSignatureDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(com.text.messages.sms.messanger.R.layout.dialog_add_signature, null)
        val editTextSignature = dialogView.findViewById<EditText>(com.text.messages.sms.messanger.R.id.editTextSignature)
        val buttonDelete = dialogView.findViewById<TextView>(com.text.messages.sms.messanger.R.id.buttonDelete)
        val buttonCancel = dialogView.findViewById<TextView>(com.text.messages.sms.messanger.R.id.buttonCancel)
        val buttonSave = dialogView.findViewById<TextView>(com.text.messages.sms.messanger.R.id.buttonSave)
        
        val prefs = requireContext().getSharedPreferences("signature", android.content.Context.MODE_PRIVATE)
        val savedSignature = prefs.getString("signature_text", "")
        editTextSignature?.setText(savedSignature)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        ThemeManager.applyTheme(requireContext(), dialogView)
        
        buttonSave?.setOnClickListener {
            val signature = editTextSignature?.text?.toString()?.trim() ?: ""
            prefs.edit().putString("signature_text", signature).apply()
            dialog.dismiss()
        }
        
        buttonDelete?.setOnClickListener {
            prefs.edit().remove("signature_text").apply()
            editTextSignature?.setText("")
        }
        
        buttonCancel?.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun openPlayStoreForRating() {
        try {
            val packageName = requireContext().packageName
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            try {
                startActivity(marketIntent)
            } catch (e: android.content.ActivityNotFoundException) {
                // If Play Store app is not available, open in browser
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open Play Store", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openPlayStoreForSharing() {
        try {
            val packageName = requireContext().packageName
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Check out Messages App on Play Store: https://play.google.com/store/apps/details?id=$packageName"
                )
            }
            context?.startActivity(Intent.createChooser(shareIntent, "Share App"))
//            val packageName = requireContext().packageName
//            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
//            try {
//                startActivity(marketIntent)
//            } catch (e: android.content.ActivityNotFoundException) {
//                // If Play Store app is not available, open in browser
//                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
//                startActivity(webIntent)
//            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to share Play Store link", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showRateUsBottomSheet() {
        val bottomSheetView = LayoutInflater.from(requireContext()).inflate(com.text.messages.sms.messanger.R.layout.bottom_sheet_rate_us, null)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(bottomSheetView)
        
        ThemeManager.applyThemeToBottomSheet(requireContext(), bottomSheetView)
        
        // Apply theme after bottom sheet is shown
        bottomSheet.setOnShowListener {
            ThemeManager.applyTheme(requireContext(), bottomSheetView)
        }
        
        val star1 = bottomSheetView.findViewById<ImageView>(com.text.messages.sms.messanger.R.id.star1)
        val star2 = bottomSheetView.findViewById<ImageView>(com.text.messages.sms.messanger.R.id.star2)
        val star3 = bottomSheetView.findViewById<ImageView>(com.text.messages.sms.messanger.R.id.star3)
        val star4 = bottomSheetView.findViewById<ImageView>(com.text.messages.sms.messanger.R.id.star4)
        val star5 = bottomSheetView.findViewById<ImageView>(com.text.messages.sms.messanger.R.id.star5)
        val buttonRateUs = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(com.text.messages.sms.messanger.R.id.buttonRateUs)
        
        val stars = listOf(star1, star2, star3, star4, star5)
        var selectedRating = 0
        
        fun updateStars(rating: Int) {
            stars.forEachIndexed { index, star ->
                star?.setImageResource(
                    if (index < rating) com.text.messages.sms.messanger.R.drawable.ic_star_filled
                    else com.text.messages.sms.messanger.R.drawable.ic_star_outline
                )
            }
            selectedRating = rating
            buttonRateUs?.isEnabled = rating > 0
            buttonRateUs?.alpha = if (rating > 0) 1f else 0.5f
        }
        
        stars.forEachIndexed { index, star ->
            star?.setOnClickListener {
                updateStars(index + 1)
            }
        }
        
        // Set backgroundTint to null for rate us button and apply theme color directly
        buttonRateUs?.backgroundTintList = null
        val themeColor = com.text.messages.sms.messanger.util.ThemeManager.getThemeColor(requireContext())
        buttonRateUs?.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        buttonRateUs?.setOnClickListener {
            if (selectedRating > 0) {
                bottomSheet.dismiss()
                openPlayStoreForRating()
            }
        }
        
        bottomSheet.show()
    }
    
    private fun exportMessages() {
        try {
            // Generate default filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val defaultFileName = "messages_backup_$timestamp.zip"
            
            // Create intent to open file picker with Downloads folder suggestion
            @Suppress("UNUSED_VARIABLE")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, defaultFileName)

                // Try to set initial URI to Downloads folder (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val treeUri = DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:${Environment.DIRECTORY_DOWNLOADS}"
                        )
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                    } catch (e: Exception) {
                        Log.w("SettingsFragment", "Could not set initial URI to Downloads", e)
                    }
                }
            }

            exportFileLauncher.launch(defaultFileName)
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Error launching export file picker", e)
            Toast.makeText(requireContext(), "Error opening file picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun importMessages() {
        try {
            // Create intent to open file picker with Downloads folder suggestion
            @Suppress("UNUSED_VARIABLE")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                
                // Try to set initial URI to Downloads folder (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val treeUri = DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:${Environment.DIRECTORY_DOWNLOADS}"
                        )
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                    } catch (e: Exception) {
                        Log.w("SettingsFragment", "Could not set initial URI to Downloads", e)
                    }
                }
            }
            
            importFileLauncher.launch(arrayOf("application/zip"))
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Error launching import file picker", e)
            Toast.makeText(requireContext(), "Error opening file picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleExportResult(uri: Uri) {
        // Show progress dialog
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Exporting Messages")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = MessagesExportImport.exportMessages(requireContext(), uri)
                progressDialog.dismiss()
                
                if (success) {
                    Toast.makeText(requireContext(), "Messages exported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to export messages", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("SettingsFragment", "Error exporting messages", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleImportResult(uri: Uri) {
        // Show progress dialog
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Importing Messages")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val importedCount = MessagesExportImport.importMessages(requireContext(), uri)
                progressDialog.dismiss()
                
                if (importedCount > 0) {
                    Toast.makeText(requireContext(), "Imported $importedCount messages successfully", Toast.LENGTH_SHORT).show()
                    // Refresh the main activity if it's in the back stack
                    // The messages will be visible when user navigates back
                } else if (importedCount == 0) {
                    Toast.makeText(requireContext(), "No new messages to import (all messages already exist)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to import messages", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("SettingsFragment", "Error importing messages", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

