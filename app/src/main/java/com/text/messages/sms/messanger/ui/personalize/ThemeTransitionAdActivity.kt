package com.text.messages.sms.messanger.ui.personalize

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.AppOpenManager
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeTransitionAdManager

class ThemeTransitionAdActivity : BaseActivity() {

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

        if (ThemeTransitionAdManager.shouldUseNativeFullscreenOnly()) {
            launchNativeFullscreenFallbackOrContinue()
            return
        }

        val handled = ThemeTransitionAdManager.showInterstitialIfAvailable(
            activity = this,
            onDismiss = { finishTransition() },
            onFallbackToNative = { launchNativeFullscreenFallbackOrContinue() }
        )

        if (!handled) {
            finishTransition()
        }
    }

    private fun launchNativeFullscreenFallbackOrContinue() {
        if (!ThemeTransitionAdManager.hasNativeFullscreenAd()) {
            finishTransition()
            return
        }

        startActivity(
            Intent(this, ThemeNativeFullscreenAdActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }

    private fun finishTransition() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }
}
