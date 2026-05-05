package com.text.messages.sms.messanger.ui.overlaypermission

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityOverlayPermissionBinding
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.util.PermissionManager
import android.app.role.RoleManager
import android.provider.Telephony
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.os.Handler
import android.os.Looper
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.WindowManager
import android.view.Gravity
import android.graphics.PixelFormat
import android.provider.Settings

class OverlayPermissionActivity : BaseActivity() {

    private lateinit var binding: ActivityOverlayPermissionBinding
    private lateinit var sharedPreferences: SharedPreferences
    
    // Activity result launcher for overlay permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Check overlay permission after returning from settings
        checkOverlayPermission()
    }
    
    override fun onPause() {
        super.onPause()
        // Keep bottom sheet visible when activity goes to background
    }
    
    private val imageTexts = listOf(
        "See Essential Messages Instantly On The Screen Without Opening The App, Which Will Help You To Get Information Quickly.\n\nOnly some important messages will be shown on the screen. Ex: OTP messages.",
        "See caller details instantly after every call, helping you stay organized and efficient."
    )
    
    private val stepTexts = listOf(
        "Step 01: Tap \"Enable\" Below",
        "Step 02: Find #Messages In The List",
        "Step 03: Toggle The Switch To Activate It"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableEdgeToEdge()
        }
        
        binding = ActivityOverlayPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        setupUI()
    }
    
    private fun setupUI() {
        // Setup ViewPager for images
        val images = listOf(R.drawable.daw1, R.drawable.daw2)
        val adapter = ImagePagerAdapter(images)
        binding.viewPagerImages.adapter = adapter
        
        // Setup scroll dot indicator
        setupScrollIndicator()
        
        // Update text based on current page
        binding.viewPagerImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateScrollDots(position)
                binding.textDescription.text = imageTexts[position]
            }
        })
        
        // Format step texts with bold parts
        formatStepTexts()
        
        // Set initial text
        binding.textDescription.text = imageTexts[0]
        
        // Setup allow permission button
        binding.buttonAllowPermission.setOnClickListener {
            if (PermissionManager.hasOverlayPermission(this)) {
                // Already granted, proceed
                onOverlayPermissionGranted()
            } else {
                // Show bottom sheet guide first
                showOverlayGuideBottomSheet()
            }
        }
    }
    
    private fun setupScrollIndicator() {
        // Create dots
        @Suppress("UNUSED_VARIABLE")
        val dots = arrayOf(binding.dot1, binding.dot2)
        
        // Set initial state
        updateScrollDots(0)
    }
    
    private fun formatStepTexts() {
        // Format Step 1: Bold "Enable"
        val step1Text = SpannableString(stepTexts[0])
        val enableStart = step1Text.indexOf("\"Enable\"")
        val enableEnd = enableStart + 8
        if (enableStart >= 0) {
            step1Text.setSpan(StyleSpan(android.graphics.Typeface.BOLD), enableStart, enableEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.textStep1.text = step1Text
        
        // Format Step 2: Bold "#Messages"
        val step2Text = SpannableString(stepTexts[1])
        val messagesStart = step2Text.indexOf("#Messages")
        val messagesEnd = messagesStart + 9
        if (messagesStart >= 0) {
            step2Text.setSpan(StyleSpan(android.graphics.Typeface.BOLD), messagesStart, messagesEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.textStep2.text = step2Text
        
        // Step 3 doesn't need formatting
        binding.textStep3.text = stepTexts[2]
    }
    
    private fun updateScrollDots(position: Int) {
        val activeWidth = (24 * resources.displayMetrics.density).toInt()
        val activeHeight = (8 * resources.displayMetrics.density).toInt()
        val inactiveSize = (8 * resources.displayMetrics.density).toInt()
        
        if (position == 0) {
            // First dot is active (pill-shaped)
            binding.dot1.layoutParams.width = activeWidth
            binding.dot1.layoutParams.height = activeHeight
            binding.dot1.setBackgroundResource(R.drawable.dot_indicator_active)
            
            // Second dot is inactive (circular)
            binding.dot2.layoutParams.width = inactiveSize
            binding.dot2.layoutParams.height = inactiveSize
            binding.dot2.setBackgroundResource(R.drawable.dot_indicator_inactive)
        } else {
            // First dot is inactive (circular)
            binding.dot1.layoutParams.width = inactiveSize
            binding.dot1.layoutParams.height = inactiveSize
            binding.dot1.setBackgroundResource(R.drawable.dot_indicator_inactive)
            
            // Second dot is active (pill-shaped)
            binding.dot2.layoutParams.width = activeWidth
            binding.dot2.layoutParams.height = activeHeight
            binding.dot2.setBackgroundResource(R.drawable.dot_indicator_active)
        }
        binding.dot1.requestLayout()
        binding.dot2.requestLayout()
    }
    
    private var overlayGuideBottomSheet: BottomSheetDialog? = null
    private var overlayWindowView: View? = null
    private var windowManager: WindowManager? = null
    
    private fun showOverlayGuideBottomSheet() {
        // Show overlay window first
//        showOverlayWindow()
        
        // Launch settings after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            overlayPermissionLauncher.launch(PermissionManager.getOverlayPermissionIntent(this))
        }, 300) // Small delay to show overlay first
        
        // Auto-close overlay after 5 seconds
//        Handler(Looper.getMainLooper()).postDelayed({
//            dismissOverlayWindow()
//        }, 5000) // Close after 5 seconds
    }
    
    private fun showOverlayWindow() {
        if (overlayWindowView != null) {
            return // Already showing
        }
        
        try {
            windowManager = getSystemService(WindowManager::class.java)
            
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_overlay_guide, null)
            
            // Load GIF image
            val toggleGifView = bottomSheetView.findViewById<android.widget.ImageView>(R.id.imageToggleGif)
            try {
                val gifDrawable = resources.getDrawable(R.drawable.toggle, theme)
                toggleGifView?.setImageDrawable(gifDrawable)
                
                // If it's an AnimatedImageDrawable, start animation
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && 
                    gifDrawable is android.graphics.drawable.AnimatedImageDrawable) {
                    gifDrawable.start()
                }
            } catch (e: Exception) {
                toggleGifView?.setImageResource(R.drawable.toggle)
            }

            // Create layout parameters for overlay window
            // Note: This requires SYSTEM_ALERT_WINDOW permission, but we can try to show it anyway
            // If permission is not granted, it will fail gracefully
            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
                )
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
                )
            }
            
            params.gravity = Gravity.BOTTOM
            params.x = 0
            params.y = 0
            
            overlayWindowView = bottomSheetView
            windowManager?.addView(bottomSheetView, params)
            
            // Add transparent overlay behind to catch outside touches for dismissal
            val overlayContainer = android.widget.FrameLayout(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                setOnClickListener {
                    dismissOverlayWindow()
                }
                alpha = 0.01f // Almost invisible but clickable
            }
            
            val rootParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )
            }
            rootParams.gravity = Gravity.BOTTOM
            rootParams.y = bottomSheetView.height
            
            try {
                windowManager?.addView(overlayContainer, rootParams)
            } catch (e: Exception) {
                // Ignore if we can't add the overlay container
            }
            
        } catch (e: SecurityException) {
            // Overlay permission not granted, fallback to regular bottom sheet
            e.printStackTrace()
            showBottomSheetDialog()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to regular bottom sheet if overlay fails
            showBottomSheetDialog()
        }
    }
    
    private fun dismissOverlayWindow() {
        overlayWindowView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayWindowView = null
    }
    
    private fun showBottomSheetDialog() {
        // Don't show if already showing or permission already granted
        if (overlayGuideBottomSheet?.isShowing == true || PermissionManager.hasOverlayPermission(this)) {
            return
        }
        
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_overlay_guide, null)
        val bottomSheet = BottomSheetDialog(this)
        overlayGuideBottomSheet = bottomSheet
        bottomSheet.setContentView(bottomSheetView)
        
        // Load GIF image
        val toggleGifView = bottomSheetView.findViewById<android.widget.ImageView>(R.id.imageToggleGif)
        try {
            val gifDrawable = resources.getDrawable(R.drawable.toggle, theme)
            toggleGifView?.setImageDrawable(gifDrawable)
            
            // If it's an AnimatedImageDrawable, start animation
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && 
                gifDrawable is android.graphics.drawable.AnimatedImageDrawable) {
                gifDrawable.start()
            }
        } catch (e: Exception) {
            // Fallback to static drawable if GIF loading fails
            toggleGifView?.setImageResource(R.drawable.toggle)
        }
        
        // Set behavior - allow dismiss by clicking outside
        bottomSheet.behavior.isDraggable = false
        bottomSheet.behavior.skipCollapsed = true
        bottomSheet.setCancelable(true) // Allow dismiss on back button
        bottomSheet.setCanceledOnTouchOutside(true) // Allow dismiss on outside touch
        bottomSheet.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Show the bottom sheet
        if (!isFinishing && !isDestroyed) {
            bottomSheet.show()
        }
    }
    
    private fun dismissBottomSheetIfShown() {
        overlayGuideBottomSheet?.dismiss()
        overlayGuideBottomSheet = null
        dismissOverlayWindow()
    }
    
    private fun checkOverlayPermission() {
        if (PermissionManager.hasOverlayPermission(this)) {
            // Dismiss bottom sheet if permission is granted
            dismissBottomSheetIfShown()
            onOverlayPermissionGranted()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        dismissBottomSheetIfShown()
    }
    
    private fun onOverlayPermissionGranted() {
        // Navigate to next activity (Language selection or continue flow)
        val isLanguageSet = sharedPreferences.getBoolean("IS_LANGUAGE_SET", false)
        
        if (!isLanguageSet) {
            // First time - show language selection
            startActivity(Intent(this, LanguageActivity::class.java))
        } else {
            // Language already set, check default SMS app status
            val isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use RoleManager
                val roleManager = getSystemService(RoleManager::class.java)
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            } else {
                // Android 9 and below - Use Telephony
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
                defaultSmsPackage != null && packageName == defaultSmsPackage
            }
            
            if (!isDefaultSmsApp) {
                // App is not set as default SMS - show default SMS setup
                startActivity(Intent(this, DefaultSmsActivity::class.java))
            } else {
                // App is default SMS app - go to main
                startActivity(Intent(this, MainActivity::class.java))
            }
        }
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        // Check overlay permission when returning from settings
        if (PermissionManager.hasOverlayPermission(this)) {
            // Permission granted, dismiss bottom sheet and overlay
            dismissBottomSheetIfShown()
            checkOverlayPermission()
        }
        // Note: If overlay window is showing, it will remain visible even after returning
        // Only dismiss it if permission is granted
    }
}

