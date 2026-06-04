package com.text.messages.sms.messanger.ui.base

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.text.messages.sms.messanger.util.MainBackPressInterstitialAdManager
import com.text.messages.sms.messanger.util.LocaleHelper

/**
 * Base activity that ensures locale is applied and handles orientation
 * for different screen sizes (portrait on phones, rotation on tablets/foldables).
 */
open class BaseActivity : AppCompatActivity() {

    private var isHandlingBackPressInterstitial = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyOrientationForScreenSize()
        super.onCreate(savedInstanceState)
        setupAppWideBackPressInterstitial()
        preloadBackPressInterstitial()
    }

    override fun onResume() {
        super.onResume()
        preloadBackPressInterstitial()
    }

    private fun applyOrientationForScreenSize() {
        val screenWidthDp = resources.configuration.smallestScreenWidthDp
        if (screenWidthDp < 600) {
            // Phone: lock to portrait
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Tablet (>=600dp) and foldable: allow system default rotation
    }

    private fun setupAppWideBackPressInterstitial() {
        if (!shouldShowBackPressInterstitial()) return

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showBackPressInterstitialOrRun {
                    onBackPressedAfterInterstitial()
                }
            }
        })
    }

    protected fun showBackPressInterstitialOrRun(onFinish: () -> Unit) {
        if (isHandlingBackPressInterstitial) return

        isHandlingBackPressInterstitial = true
        val handled = MainBackPressInterstitialAdManager.showIfAvailable(this) {
            isHandlingBackPressInterstitial = false
            onFinish()
        }

        if (!handled) {
            isHandlingBackPressInterstitial = false
            preloadBackPressInterstitial()
            onFinish()
        }
    }

    protected open fun shouldShowBackPressInterstitial(): Boolean {
        return javaClass.simpleName !in backPressInterstitialExcludedScreens
    }

    protected open fun onBackPressedAfterInterstitial() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
    }

    private fun preloadBackPressInterstitial() {
        if (shouldShowBackPressInterstitial()) {
            MainBackPressInterstitialAdManager.preload(this)
        }
    }

    companion object {
        private val backPressInterstitialExcludedScreens = setOf(
            "DefaultSmsActivity",
            "LandingActivity",
            "ResumeActivity",
            "WelcomeActivity",
            "OverlayPermissionActivity",
            "OverlayPermissionGuideActivity",
            "LanguageTransitionAdActivity",
            "LanguageNativeFullscreenAdActivity",
            "ThemeTransitionAdActivity",
            "ThemeNativeFullscreenAdActivity",
            "ImExTransitionAdActivity",
            "ImExNativeFullscreenAdActivity"
        )
    }
}
