package com.text.messages.sms.messanger.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

object AppOpenAdManager {

    private const val TAG = "AppOpenAdManager"
    private const val MIN_AD_DELAY_MS = 2500L

    private val handler = Handler(Looper.getMainLooper())
    private var isLoadingAd = false
    private var isShowingAd = false
    private var currentAd: AppOpenAd? = null
    private var loadTimeMs: Long = 0L

    fun showColdStartAppOpenAd(activity: Activity, onFinish: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onFinish()
            return
        }

        val coldStart = com.text.messages.sms.messanger.ui.splash.LandingActivity.coldStartTimestampMs
        if (coldStart > 0L) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - coldStart
            val remainingDelay = (MIN_AD_DELAY_MS - elapsed).coerceAtLeast(0L)
            if (remainingDelay > 0L) {
                handler.postDelayed({
                    showColdStartAppOpenAd(activity, onFinish)
                }, remainingDelay)
                return
            }
        }

        if (isShowingAd) {
            onFinish()
            return
        }

        currentAd?.takeIf { System.currentTimeMillis() - loadTimeMs < 4 * 60 * 60 * 1000L }?.let { loadedAd ->
            showLoadedAd(activity, loadedAd, onFinish)
            return
        }

        val adUnitId = RemoteConfigHelper.getAppOpenAdUnitId().trim()
        if (adUnitId.isBlank()) {
            Log.w(TAG, "App open ad unit id is blank, skipping cold-start ad")
            onFinish()
            return
        }

        if (isLoadingAd) {
            onFinish()
            return
        }

        isLoadingAd = true

        AppOpenAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAd = false
                    currentAd = ad
                    loadTimeMs = System.currentTimeMillis()
                    showLoadedAd(activity, ad, onFinish)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    currentAd = null
                    Log.w(TAG, "Cold-start app open ad failed to load: ${loadAdError.message}")
                    onFinish()
                }
            }
        )
    }

    private fun showLoadedAd(activity: Activity, ad: AppOpenAd, onFinish: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            currentAd = null
            onFinish()
            return
        }

        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }

            override fun onAdDismissedFullScreenContent() {
                cleanup()
                onFinish()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.w(TAG, "Cold-start app open ad failed to show: ${fullScreenContentError.message}")
                cleanup()
                onFinish()
            }
        }

        ad.show(activity)
    }

    private fun cleanup() {
        currentAd = null
        isLoadingAd = false
        isShowingAd = false
    }
}
