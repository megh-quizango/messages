package com.text.messages.sms.messanger.util

import android.app.Activity
import android.app.Application
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

class AppOpenAdManager(private val application: Application) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var adUnitId: String? = null
    private var onAdDismissedListener: (() -> Unit)? = null

    companion object {
        private const val TAG = "AppOpenAdManager"
    }

    fun loadAd(adUnitId: String) {
        this.adUnitId = adUnitId
        
        // Don't load if already loading, showing, or ad is available
        if (isLoadingAd || isShowingAd || isAdAvailable()) {
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        
        AppOpenAd.load(
            application,
            adUnitId,
            request,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App open ad loaded")
                    appOpenAd = ad
                    isLoadingAd = false
                    AnalyticsHelper.logAdLoad("app_open", adUnitId, true)
                    ad.setOnPaidEventListener { adValue ->
                        Log.d(TAG, "App open ad paid event: ${adValue.valueMicros}")
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "App open ad failed to load: ${loadAdError.message}")
                    isLoadingAd = false
                    appOpenAd = null
                    AnalyticsHelper.logAdLoad("app_open", adUnitId, false)
                    AnalyticsHelper.logAdError("app_open", adUnitId, loadAdError.code.toString())
                }
            }
        )
    }

    fun showAdIfAvailable(activity: Activity, onAdDismissed: (() -> Unit)? = null) {
        onAdDismissedListener = onAdDismissed
        
        if (isShowingAd) {
            Log.d(TAG, "Ad is already showing")
            onAdDismissed?.invoke()
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "Ad not available")
            onAdDismissed?.invoke()
            loadAd(adUnitId ?: return)
            return
        }

        val adToShow = appOpenAd
        if (adToShow == null) {
            Log.e(TAG, "Ad is null when trying to show")
            onAdDismissed?.invoke()
            loadAd(adUnitId ?: return)
            return
        }

        adToShow.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App open ad dismissed")
                appOpenAd = null
                isShowingAd = false
                onAdDismissedListener?.invoke()
                onAdDismissedListener = null
                loadAd(adUnitId ?: return)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "App open ad failed to show: ${adError.message}")
                adUnitId?.let { AnalyticsHelper.logAdError("app_open", it, adError.code.toString()) }
                appOpenAd = null
                isShowingAd = false
                onAdDismissedListener?.invoke()
                onAdDismissedListener = null
                loadAd(adUnitId ?: return)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App open ad showed")
                isShowingAd = true
                adUnitId?.let { AnalyticsHelper.logAdImpression("app_open", it) }
            }

            override fun onAdClicked() {
                Log.d(TAG, "App open ad clicked")
                adUnitId?.let { AnalyticsHelper.logAdClick("app_open", it) }
            }

            override fun onAdImpression() {
                Log.d(TAG, "App open ad impression")
            }
        }

        try {
            adToShow.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Exception showing ad: ${e.message}", e)
            appOpenAd = null
            isShowingAd = false
            onAdDismissedListener?.invoke()
            onAdDismissedListener = null
            loadAd(adUnitId ?: return)
        }
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo()
    }

    private fun wasLoadTimeLessThanNHoursAgo(): Boolean {
        // For simplicity, we'll consider the ad valid if it exists
        // In production, you might want to track load time
        return true
    }
}

