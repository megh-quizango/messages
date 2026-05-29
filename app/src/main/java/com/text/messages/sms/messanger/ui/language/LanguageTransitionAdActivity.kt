package com.text.messages.sms.messanger.ui.language

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.AppOpenManager
import com.text.messages.sms.messanger.util.LanguageTransitionAdManager
import com.text.messages.sms.messanger.util.ThemeManager

class LanguageTransitionAdActivity : BaseActivity() {

    companion object {
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }

    private var hasStartedTransitionFlow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val root = FrameLayout(this)
        setContentView(root)

        ThemeManager.setupNavigationBar(this)
        ThemeManager.applyTheme(this, root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (hasStartedTransitionFlow || isFinishing || isDestroyed) {
            return
        }
        hasStartedTransitionFlow = true

        AppOpenManager.suppressAppOpenFor(8000L)

        if (LanguageTransitionAdManager.shouldUseNativeFullscreenOnly()) {
            launchNativeFullscreenFallbackOrContinue()
            return
        }

        val handled = LanguageTransitionAdManager.showInterstitialIfAvailable(
            activity = this,
            onDismiss = { navigateToNextScreen() },
            onFallbackToNative = { launchNativeFullscreenFallbackOrContinue() }
        )

        if (!handled) {
            navigateToNextScreen()
        }
    }

    private fun launchNativeFullscreenFallbackOrContinue() {
        if (!LanguageTransitionAdManager.hasNativeFullscreenAd()) {
            navigateToNextScreen()
            return
        }

        startActivity(
            Intent(this, LanguageNativeFullscreenAdActivity::class.java).apply {
                putExtra(
                    LanguageNativeFullscreenAdActivity.EXTRA_FROM_SETTINGS,
                    intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
                )
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }

    private fun navigateToNextScreen() {
        val fromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
        val nextIntent = if (fromSettings) {
            Intent(this, com.text.messages.sms.messanger.ui.main.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
        } else {
            Intent(this, com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }

        startActivity(nextIntent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }
}
