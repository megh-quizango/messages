package com.text.messages.sms.messanger.util

import android.app.Activity
import android.content.Intent
import com.text.messages.sms.messanger.ui.language.LanguageNativeFullscreenAdActivity

object LanguageTransitionIntentHelper {

    private const val EXTRA_SHOW_LANGUAGE_TRANSITION_AD = "show_language_transition_ad"

    fun markForTransitionAd(intent: Intent): Intent {
        intent.putExtra(EXTRA_SHOW_LANGUAGE_TRANSITION_AD, true)
        return intent
    }

    fun maybeShowPendingTransitionAd(activity: Activity) {
        if (!consumeShouldShowTransitionAd(activity)) {
            return
        }

        AppOpenManager.suppressAppOpenFor(8000L)

        if (LanguageTransitionAdManager.shouldUseNativeFullscreenOnly()) {
            launchNativeFullscreenFallback(activity)
            return
        }

        val handled = LanguageTransitionAdManager.showInterstitialIfAvailable(
            activity = activity,
            onDismiss = {},
            onFallbackToNative = { launchNativeFullscreenFallback(activity) }
        )

        if (!handled && LanguageTransitionAdManager.shouldUseNativeFullscreenOnly()) {
            launchNativeFullscreenFallback(activity)
        }
    }

    private fun consumeShouldShowTransitionAd(activity: Activity): Boolean {
        val intent = activity.intent ?: return false
        val shouldShow = intent.getBooleanExtra(EXTRA_SHOW_LANGUAGE_TRANSITION_AD, false)
        if (shouldShow) {
            intent.removeExtra(EXTRA_SHOW_LANGUAGE_TRANSITION_AD)
        }
        return shouldShow
    }

    private fun launchNativeFullscreenFallback(activity: Activity) {
        if (!LanguageTransitionAdManager.hasNativeFullscreenAd()) {
            return
        }

        activity.startActivity(
            Intent(activity, LanguageNativeFullscreenAdActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}
