package com.text.messages.sms.messanger.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.text.messages.sms.messanger.ui.caller.CallAfterActivity
import com.text.messages.sms.messanger.ui.language.LanguageActivity
import com.text.messages.sms.messanger.ui.language.LanguageNativeFullscreenAdActivity
import com.text.messages.sms.messanger.ui.language.LanguageTransitionAdActivity
import com.text.messages.sms.messanger.ui.overlaypermission.OverlayPermissionActivity
import com.text.messages.sms.messanger.ui.overlaypermission.OverlayPermissionGuideActivity
import com.text.messages.sms.messanger.ui.personalize.ThemeNativeFullscreenAdActivity
import com.text.messages.sms.messanger.ui.personalize.ThemeTransitionAdActivity
import com.text.messages.sms.messanger.ui.settings.ImExNativeFullscreenAdActivity
import com.text.messages.sms.messanger.ui.settings.ImExTransitionAdActivity
import com.text.messages.sms.messanger.ui.splash.LandingActivity

class AppOpenManager(
    private val application: Application
) : Application.ActivityLifecycleCallbacks {

    companion object {
        @Volatile
        private var suppressAppOpenUntilElapsedMs: Long = 0L

        fun suppressAppOpenFor(durationMs: Long) {
            val until = android.os.SystemClock.elapsedRealtime() + durationMs
            if (until > suppressAppOpenUntilElapsedMs) {
                suppressAppOpenUntilElapsedMs = until
            }
        }

        private fun isAppOpenSuppressed(): Boolean {
            return android.os.SystemClock.elapsedRealtime() < suppressAppOpenUntilElapsedMs
        }
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var currentActivity: Activity? = null

    /** Number of activities currently in "started" state. When this goes 1 -> 0, app went to background. */
    private var startedActivityCount = 0
    /** True after app has gone to background (count went to 0). Used to show ad only on resume from background. */
    private var wasInBackground = false
    /** When true, show ad as soon as it loads (user resumed from background but ad wasn't ready). */
    private var pendingShowOnLoad = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun loadAd() {
        if (isLoadingAd || isAdAvailable()) return

        val adUnitId = AdConfig.resolveAppOpenResumeAdUnitId(application)
        if (adUnitId.isBlank()) {
            return
        }

        isLoadingAd = true

        val request = AdRequest.Builder().build()

        @Suppress("DEPRECATION")
        AppOpenAd.load(
            application,
            adUnitId,
            request,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    // If user resumed from background while ad was loading, show it now
                    if (pendingShowOnLoad && currentActivity != null && !isShowingAd) {
                        pendingShowOnLoad = false
                        mainHandler.post { showAdIfAvailable() }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    pendingShowOnLoad = false
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null
    }

    /**
     * @param showWhenLoaded If true and ad isn't ready yet, we'll show it when the load completes (used on resume from background).
     */
    private fun showAdIfAvailable(showWhenLoaded: Boolean = false) {
        if (isShowingAd) return
        val activity = currentActivity
        if (activity == null || !shouldShowAppOpenOn(activity)) {
            pendingShowOnLoad = false
            loadAd()
            return
        }
        if (!isAdAvailable()) {
            if (showWhenLoaded) pendingShowOnLoad = true
            loadAd()
            return
        }

        appOpenAd?.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdShowedFullScreenContent() {
                    isShowingAd = true
                }

                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAd = false
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    isShowingAd = false
                    loadAd()
                }
            }

        appOpenAd?.show(activity)
    }

    // 🔹 Lifecycle callbacks
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        startedActivityCount++
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        if (isAppOpenSuppressed()) {
            pendingShowOnLoad = false
            wasInBackground = false
            loadAd()
            return
        }
        if (!shouldShowAppOpenOn(activity)) {
            pendingShowOnLoad = false
            loadAd()
            return
        }
        // Show app open ad only when resuming from background, not on initial app launch
        if (wasInBackground) {
            wasInBackground = false
            // Only show ads on resume if minimum cold-start delay has passed
            val coldStart = com.text.messages.sms.messanger.ui.splash.LandingActivity.coldStartTimestampMs
            val elapsed = if (coldStart > 0L) android.os.SystemClock.elapsedRealtime() - coldStart else Long.MAX_VALUE
            if (elapsed > 2500L) {
                showAdIfAvailable(showWhenLoaded = true)
            } else {
                loadAd() // Too early for ads - just preload for later
            }
        } else {
            // First launch: delay preload to avoid contention during startup
            mainHandler.postDelayed({ loadAd() }, 3000L)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount <= 0) {
            startedActivityCount = 0
            wasInBackground = true
            pendingShowOnLoad = false
            loadAd() // Preload for next resume
        }
        if (activity == currentActivity) {
            currentActivity = null
        }
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun shouldShowAppOpenOn(activity: Activity): Boolean {
        return activity !is CallAfterActivity &&
            activity !is LandingActivity &&
            activity !is OverlayPermissionActivity &&
            activity !is OverlayPermissionGuideActivity &&
            activity !is LanguageActivity &&
            activity !is LanguageTransitionAdActivity &&
            activity !is LanguageNativeFullscreenAdActivity &&
            activity !is ThemeTransitionAdActivity &&
            activity !is ThemeNativeFullscreenAdActivity &&
            activity !is ImExTransitionAdActivity &&
            activity !is ImExNativeFullscreenAdActivity
    }
}
