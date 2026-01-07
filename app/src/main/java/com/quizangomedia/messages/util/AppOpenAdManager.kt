package com.quizangomedia.messages.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

class AppOpenAdManager(private val application: Application) {

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var adUnitId: String? = null

    companion object {
        private const val TAG = "AppOpenAdManager"
    }

    /**
     * Loads app open ad - simplified like working example
     */
    fun loadAppOpenAd(context: Context, callback: (AppOpenAd?) -> Unit) {
        val adUnitIdToUse = adUnitId ?: return callback(null)
        
        Log.d(TAG, "=== loadAppOpenAd called ===")
        Log.d(TAG, "Ad unit ID: $adUnitIdToUse")
        
        AppOpenAd.load(
            context,
            adUnitIdToUse,
            AdRequest.Builder().build(),
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "=== onAdLoaded ===")
                    Log.d(TAG, "Ad successfully loaded")
                    callback(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "=== onAdFailedToLoad ===")
                    Log.e(TAG, "Error: ${error.message}, Code: ${error.code}")
                    callback(null)
                }
            }
        )
    }

    /**
     * Sets the ad unit ID
     */
    fun setAdUnitId(adUnitId: String) {
        this.adUnitId = adUnitId
    }

    /**
     * Loads and shows app open ad - simplified like working example
     */
    fun loadAndShowAppOpenAd(activity: Activity) {
        Log.d(TAG, "=== loadAndShowAppOpenAd called ===")
        Log.d(TAG, "Activity: ${activity.javaClass.simpleName}")
        Log.d(TAG, "isShowingAd: $isShowingAd")
        
        if (isShowingAd) {
            Log.d(TAG, "Ad already showing, skipping")
            return
        }

        // Check if activity is AdActivity (prevent recursion)
        if (activity.javaClass.name.contains("AdActivity")) {
            Log.w(TAG, "AdActivity detected, skipping to prevent recursion")
            return
        }

        // Check if activity is valid
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed, skipping")
            Log.w(TAG, "isFinishing: ${activity.isFinishing}, isDestroyed: ${activity.isDestroyed}")
            return
        }

        loadAppOpenAd(activity) { ad ->
            appOpenAd = ad
            ad?.let {
                Log.d(TAG, "Ad loaded successfully, showing ad")
                showAppOpenAd(it, activity)
            } ?: run {
                Log.w(TAG, "Ad failed to load or is null")
            }
        }
    }

    /**
     * Shows the app open ad - simplified like working example
     */
    private fun showAppOpenAd(ad: AppOpenAd, activity: Activity) {
        Log.d(TAG, "=== showAppOpenAd called ===")
        Log.d(TAG, "Activity: ${activity.javaClass.simpleName}")
        
        if (isShowingAd) {
            Log.d(TAG, "Ad already showing, skipping")
            return
        }

        isShowingAd = true
        Log.d(TAG, "isShowingAd set to true")

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "=== onAdDismissedFullScreenContent ===")
                Log.d(TAG, "Ad dismissed - dismiss button was clicked")
                appOpenAd = null
                isShowingAd = false
                // Load next ad for future use
                adUnitId?.let { unitId ->
                    loadAppOpenAd(activity) { nextAd ->
                        appOpenAd = nextAd
                        Log.d(TAG, "Next ad loaded: ${nextAd != null}")
                    }
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "=== onAdFailedToShowFullScreenContent ===")
                Log.e(TAG, "Error code: ${error.code}, message: ${error.message}")
                appOpenAd = null
                isShowingAd = false
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "=== onAdShowedFullScreenContent ===")
                Log.d(TAG, "Ad is now displayed on screen")
                Log.d(TAG, "Dismiss button should appear within 3-6 seconds")
            }

            override fun onAdImpression() {
                Log.d(TAG, "=== onAdImpression ===")
            }

            override fun onAdClicked() {
                Log.d(TAG, "=== onAdClicked ===")
            }
        }

        try {
            Log.d(TAG, "Calling ad.show(activity)")
            ad.show(activity)
            Log.d(TAG, "ad.show() called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "=== Exception showing ad ===")
            Log.e(TAG, "Error: ${e.message}", e)
            appOpenAd = null
            isShowingAd = false
        }
    }

    /**
     * Shows ad if available - for backward compatibility
     * Now delegates to loadAndShowAppOpenAd
     */
    fun showAdIfAvailable(activity: Activity, onAdDismissed: (() -> Unit)? = null) {
        Log.d(TAG, "=== showAdIfAvailable called (legacy method) ===")
        
        // If we have a preloaded ad, show it immediately
        if (appOpenAd != null && !isShowingAd) {
            Log.d(TAG, "Preloaded ad available, showing immediately")
            showAppOpenAd(appOpenAd!!, activity)
            onAdDismissed?.invoke()
        } else {
            // Otherwise load and show
            Log.d(TAG, "No preloaded ad, loading and showing")
            loadAndShowAppOpenAd(activity)
            onAdDismissed?.invoke()
        }
    }

    
    fun isAdShowing(): Boolean {
        return isShowingAd
    }
    
    fun isAdLoading(): Boolean {
        // Check if we're currently loading (no ad available and not showing)
        return appOpenAd == null && !isShowingAd
    }
}

