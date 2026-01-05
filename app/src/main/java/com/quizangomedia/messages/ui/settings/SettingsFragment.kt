package com.quizangomedia.messages.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.quizangomedia.messages.databinding.FragmentSettingsBinding
import com.quizangomedia.messages.ui.advance.AdvanceActivity
import com.quizangomedia.messages.ui.feedback.FeedbackActivity
import com.quizangomedia.messages.ui.language.LanguageActivity
import com.quizangomedia.messages.ui.manageapps.ManageAppsActivity
import com.quizangomedia.messages.ui.notifications.NotificationsActivity
import com.quizangomedia.messages.ui.pin.PinActivity
import com.quizangomedia.messages.ui.recyclebin.RecycleBinActivity
import com.quizangomedia.messages.ui.scheduled.ScheduledMessagesActivity
import com.quizangomedia.messages.ui.spam.SpamBlockActivity
import com.quizangomedia.messages.ui.starred.StarredActivity
import com.quizangomedia.messages.ui.swipe.SwipeGesturesActivity
import com.quizangomedia.messages.ui.caller.CallerSettingsActivity
import android.content.BroadcastReceiver
import com.quizangomedia.messages.ui.defaultsms.DefaultSmsActivity
import com.quizangomedia.messages.util.ThemeChangeHelper
import com.quizangomedia.messages.util.ThemeManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: SettingsAdapter
    private var themeChangeReceiver: BroadcastReceiver? = null

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
            val mainActivity = activity as? com.quizangomedia.messages.ui.main.MainActivity
            mainActivity?.navigateToMessages()
        }
    }
    
    private fun setupRecyclerView() {
        val settingsItems = listOf(
            SettingsItem("General", listOf(
                SettingsOption("Default SMS apps Messages", null, null, true) { 
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
                SettingsOption("Contacts colored icons", null, true, false),
                SettingsOption("Color SIM card icons", com.quizangomedia.messages.R.drawable.sim, false, false),
                SettingsOption("Quick access to OTP", com.quizangomedia.messages.R.drawable.otp, true, false)
            )),
            SettingsItem("Go To", listOf(
                SettingsOption("Manage Apps", com.quizangomedia.messages.R.drawable.manage, null, false) { 
                    startActivity(Intent(requireContext(), ManageAppsActivity::class.java)) 
                },
                SettingsOption("Private Conversations", com.quizangomedia.messages.R.drawable.lock, null, false) { 
                    startActivity(Intent(requireContext(), PinActivity::class.java)) 
                },
                SettingsOption("Spam & Block", com.quizangomedia.messages.R.drawable.spam, null, false) { 
                    startActivity(Intent(requireContext(), SpamBlockActivity::class.java)) 
                },
                SettingsOption("Archive", com.quizangomedia.messages.R.drawable.archive, null, false),
                SettingsOption("Recycle Bin", null, null, false) { 
                    startActivity(Intent(requireContext(), RecycleBinActivity::class.java)) 
                },
                SettingsOption("Schedule Messages", null, null, false) { 
                    startActivity(Intent(requireContext(), ScheduledMessagesActivity::class.java)) 
                },
                SettingsOption("Caller Settings", null, null, false) { 
                    startActivity(Intent(requireContext(), CallerSettingsActivity::class.java)) 
                },
                SettingsOption("Starred", null, null, false) { 
                    startActivity(Intent(requireContext(), StarredActivity::class.java)) 
                },
                SettingsOption("Swipe Gestures", null, null, false) { 
                    startActivity(Intent(requireContext(), SwipeGesturesActivity::class.java)) 
                },
                SettingsOption("Add Signature", null, null, false) { 
                    showSignatureDialog()
                },
                SettingsOption("Notifications", null, null, false) { 
                    startActivity(Intent(requireContext(), NotificationsActivity::class.java)) 
                },
                SettingsOption("Language", null, null, false) { 
                    startActivity(Intent(requireContext(), LanguageActivity::class.java).apply {
                        putExtra("from_settings", true)
                    })
                },
                SettingsOption("Advance", null, null, false) { 
                    startActivity(Intent(requireContext(), AdvanceActivity::class.java)) 
                },
                SettingsOption("Feedback", null, null, false) { 
                    startActivity(Intent(requireContext(), FeedbackActivity::class.java)) 
                },
                SettingsOption("Share App!", null, null, false),
                SettingsOption("Rate Us", null, null, false) { 
                    showRateUsBottomSheet()
                }
            )),
            SettingsItem("Backups", listOf(
                SettingsOption("Export Messages", null, null, false),
                SettingsOption("Import Messages", null, null, false)
            ))
        )
        
        adapter = SettingsAdapter(settingsItems) { option ->
            option.onClick?.invoke()
        }
        
        binding.recyclerViewSettings.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSettings.adapter = adapter
    }
    
    private fun showSignatureDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(com.quizangomedia.messages.R.layout.dialog_add_signature, null)
        val editTextSignature = dialogView.findViewById<EditText>(com.quizangomedia.messages.R.id.editTextSignature)
        val buttonDelete = dialogView.findViewById<TextView>(com.quizangomedia.messages.R.id.buttonDelete)
        val buttonCancel = dialogView.findViewById<TextView>(com.quizangomedia.messages.R.id.buttonCancel)
        val buttonSave = dialogView.findViewById<TextView>(com.quizangomedia.messages.R.id.buttonSave)
        
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
    
    private fun showRateUsBottomSheet() {
        val bottomSheetView = LayoutInflater.from(requireContext()).inflate(com.quizangomedia.messages.R.layout.bottom_sheet_rate_us, null)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(bottomSheetView)
        
        ThemeManager.applyThemeToBottomSheet(requireContext(), bottomSheetView)
        
        // Apply theme after bottom sheet is shown
        bottomSheet.setOnShowListener {
            ThemeManager.applyTheme(requireContext(), bottomSheetView)
        }
        
        val star1 = bottomSheetView.findViewById<ImageView>(com.quizangomedia.messages.R.id.star1)
        val star2 = bottomSheetView.findViewById<ImageView>(com.quizangomedia.messages.R.id.star2)
        val star3 = bottomSheetView.findViewById<ImageView>(com.quizangomedia.messages.R.id.star3)
        val star4 = bottomSheetView.findViewById<ImageView>(com.quizangomedia.messages.R.id.star4)
        val star5 = bottomSheetView.findViewById<ImageView>(com.quizangomedia.messages.R.id.star5)
        val buttonRateUs = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(com.quizangomedia.messages.R.id.buttonRateUs)
        
        val stars = listOf(star1, star2, star3, star4, star5)
        var selectedRating = 0
        
        fun updateStars(rating: Int) {
            stars.forEachIndexed { index, star ->
                star?.setImageResource(
                    if (index < rating) com.quizangomedia.messages.R.drawable.ic_star_filled
                    else com.quizangomedia.messages.R.drawable.ic_star_outline
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
        val themeColor = com.quizangomedia.messages.util.ThemeManager.getThemeColor(requireContext())
        buttonRateUs?.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        buttonRateUs?.setOnClickListener {
            if (selectedRating > 0) {
                bottomSheet.dismiss()
            }
        }
        
        bottomSheet.show()
    }
}

