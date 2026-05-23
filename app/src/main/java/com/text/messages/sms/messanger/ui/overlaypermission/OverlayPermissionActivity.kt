package com.text.messages.sms.messanger.ui.overlaypermission

import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.animation.ObjectAnimator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityOverlayPermissionBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.util.ButtonShimmerAnimator
import com.text.messages.sms.messanger.util.PermissionManager

class OverlayPermissionActivity : BaseActivity() {

    companion object {
        private const val OVERLAY_GUIDE_LAUNCH_DELAY_MS = 1200L
    }

    private lateinit var binding: ActivityOverlayPermissionBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var pagerAdapter: ImagePagerAdapter
    private var buttonShimmerAnimator: ObjectAnimator? = null
    private val guideLaunchHandler = Handler(Looper.getMainLooper())
    private var shouldShowOverlayGuide = false
    private var isWaitingForOverlaySettings = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkOverlayPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableEdgeToEdge()
        }

        binding = ActivityOverlayPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureStatusBar()

        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingRight = binding.root.paddingRight
        val initialPaddingBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )
            insets
        }

        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        setupUi()
    }

    private fun configureStatusBar() {
        window.statusBarColor = getColor(android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun setupUi() {
        val slides = listOf(
            OverlayPermissionSlide(
                stepBadge = getString(R.string.overlay_step_badge_one),
                title = getString(R.string.overlay_slide_one_title),
                subtitle = null,
                imageRes = R.drawable.daw1,
                showPermissionRow = false
            ),
            OverlayPermissionSlide(
                stepBadge = getString(R.string.overlay_step_badge_two),
                title = getString(R.string.overlay_slide_two_title),
                subtitle = getString(R.string.overlay_slide_two_subtitle),
                imageRes = R.drawable.daw2,
                showPermissionRow = true
            )
        )

        pagerAdapter = ImagePagerAdapter(slides)
        binding.viewPagerImages.adapter = pagerAdapter
        pagerAdapter.setSelectedPosition(0)
        updateScrollDots(0)

        binding.viewPagerImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateScrollDots(position)
                pagerAdapter.setSelectedPosition(position)
            }
        })

        binding.buttonAllowPermission.setOnClickListener {
            if (PermissionManager.hasOverlayPermission(this)) {
                onOverlayPermissionGranted()
            } else {
                shouldShowOverlayGuide = true
                isWaitingForOverlaySettings = true
                cancelOverlayGuideLaunch()
                overlayPermissionLauncher.launch(PermissionManager.getOverlayPermissionIntent(this))
            }
        }
    }

    private fun updateScrollDots(position: Int) {
        binding.dot1.setBackgroundResource(
            if (position == 0) R.drawable.overlay_dot_active else R.drawable.overlay_dot_inactive
        )
        binding.dot2.setBackgroundResource(
            if (position == 1) R.drawable.overlay_dot_active else R.drawable.overlay_dot_inactive
        )
    }

    private fun checkOverlayPermission() {
        shouldShowOverlayGuide = false
        isWaitingForOverlaySettings = false
        cancelOverlayGuideLaunch()
        if (PermissionManager.hasOverlayPermission(this)) {
            onOverlayPermissionGranted()
        }
    }

    private fun scheduleOverlayGuideLaunch() {
        cancelOverlayGuideLaunch()
        guideLaunchHandler.postDelayed({
            if (
                shouldShowOverlayGuide &&
                isWaitingForOverlaySettings &&
                !PermissionManager.hasOverlayPermission(this) &&
                !isFinishing &&
                !isDestroyed
            ) {
                startActivity(Intent(this, OverlayPermissionGuideActivity::class.java))
                overridePendingTransition(0, 0)
            }
        }, OVERLAY_GUIDE_LAUNCH_DELAY_MS)
    }

    private fun cancelOverlayGuideLaunch() {
        guideLaunchHandler.removeCallbacksAndMessages(null)
    }

    private fun onOverlayPermissionGranted() {
        val isLanguageSet = sharedPreferences.getBoolean("IS_LANGUAGE_SET", false)

        if (!isLanguageSet) {
            startActivity(Intent(this, LanguageActivity::class.java))
        } else {
            val isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            } else {
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
                defaultSmsPackage != null && packageName == defaultSmsPackage
            }

            if (!isDefaultSmsApp) {
                startActivity(Intent(this, DefaultSmsActivity::class.java))
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
        }

        finish()
    }

    override fun onResume() {
        super.onResume()
        cancelOverlayGuideLaunch()
        binding.viewAllowPermissionShimmer.post {
            buttonShimmerAnimator = ButtonShimmerAnimator.start(
                binding.viewAllowPermissionShimmer,
                buttonShimmerAnimator
            )
        }
        if (PermissionManager.hasOverlayPermission(this)) {
            checkOverlayPermission()
        }
    }

    override fun onPause() {
        ButtonShimmerAnimator.stop(binding.viewAllowPermissionShimmer, buttonShimmerAnimator)
        buttonShimmerAnimator = null
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (
            shouldShowOverlayGuide &&
            isWaitingForOverlaySettings &&
            !PermissionManager.hasOverlayPermission(this)
        ) {
            scheduleOverlayGuideLaunch()
        } else {
            cancelOverlayGuideLaunch()
        }
    }

    override fun onDestroy() {
        cancelOverlayGuideLaunch()
        super.onDestroy()
    }
}
