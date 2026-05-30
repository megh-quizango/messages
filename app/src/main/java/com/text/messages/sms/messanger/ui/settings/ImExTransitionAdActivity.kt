package com.text.messages.sms.messanger.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.AppOpenManager
import com.text.messages.sms.messanger.util.ImExTransitionAdManager
import com.text.messages.sms.messanger.util.ThemeManager

class ImExTransitionAdActivity : BaseActivity() {

    private var hasStartedTransitionFlow = false
    private var hasFinishedTransition = false

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

        if (ImExTransitionAdManager.shouldUseNativeFullscreenOnly()) {
            launchNativeFullscreenFallbackOrContinue()
            return
        }

        val handled = ImExTransitionAdManager.showInterstitialIfAvailable(
            activity = this,
            onDismiss = { finishTransition() },
            onFallbackToNative = { launchNativeFullscreenFallbackOrContinue() }
        )

        if (!handled) {
            finishTransition()
        }
    }

    private fun launchNativeFullscreenFallbackOrContinue() {
        if (!ImExTransitionAdManager.hasNativeFullscreenAd()) {
            finishTransition()
            return
        }

        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, ImExNativeFullscreenAdActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            },
            REQUEST_NATIVE_FULLSCREEN
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_NATIVE_FULLSCREEN) {
            finishTransition()
        }
    }

    private fun finishTransition() {
        if (hasFinishedTransition) {
            return
        }
        hasFinishedTransition = true
        setResult(Activity.RESULT_OK)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQUEST_NATIVE_FULLSCREEN = 7001
    }
}
