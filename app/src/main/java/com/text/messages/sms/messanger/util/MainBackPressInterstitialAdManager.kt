package com.text.messages.sms.messanger.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd

object MainBackPressInterstitialAdManager {

    private const val TAG = "MainBackPressInterstitial"
    private const val AD_TYPE = "main_back_interstitial"
    private const val FALLBACK_AD_TYPE = "main_back_fallback_interstitial"

    private var primaryInterstitialAd: InterstitialAd? = null
    private var fallbackInterstitialAd: InterstitialAd? = null
    private var isLoadingPrimary = false
    private var isLoadingFallback = false
    private var currentPrimaryAdUnitId: String? = null
    private var currentFallbackAdUnitId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun preload(context: Context) {
        val appContext = context.applicationContext
        val primaryAdUnitId = AdConfig.resolveMainBackInterstitialAdUnitId(appContext).trim()
        val fallbackAdUnitId = AdConfig.resolveMainBackFallbackInterstitialAdUnitId(appContext).trim()
        if (primaryAdUnitId.isBlank() && fallbackAdUnitId.isBlank()) {
            destroy()
            return
        }

        val didPrimaryChange = currentPrimaryAdUnitId != null && currentPrimaryAdUnitId != primaryAdUnitId
        val didFallbackChange = currentFallbackAdUnitId != null && currentFallbackAdUnitId != fallbackAdUnitId
        if (didPrimaryChange || didFallbackChange) {
            destroy()
        }

        currentPrimaryAdUnitId = primaryAdUnitId
        currentFallbackAdUnitId = fallbackAdUnitId

        if (primaryAdUnitId.isNotBlank()) {
            NextGenAdHelper.startInterstitialPreload(primaryAdUnitId)
            loadPrimary(appContext, primaryAdUnitId, fallbackAdUnitId)
        } else if (fallbackAdUnitId.isNotBlank()) {
            NextGenAdHelper.startInterstitialPreload(fallbackAdUnitId)
            loadFallback(appContext, fallbackAdUnitId)
        }
    }

    private fun loadPrimary(context: Context, adUnitId: String, fallbackAdUnitId: String) {
        if (isLoadingPrimary || primaryInterstitialAd != null) return

        NextGenAdHelper.pollInterstitial(adUnitId)?.let { preloadedAd ->
            primaryInterstitialAd = preloadedAd
            AnalyticsHelper.logAdLoad(AD_TYPE, adUnitId, true)
            return
        }

        isLoadingPrimary = true
        NextGenAdHelper.loadInterstitial(
            adUnitId,
            onLoaded = { ad ->
                primaryInterstitialAd = ad
                isLoadingPrimary = false
                AnalyticsHelper.logAdLoad(AD_TYPE, adUnitId, true)
            },
            onFailed = { loadAdError ->
                primaryInterstitialAd = null
                isLoadingPrimary = false
                AnalyticsHelper.logAdLoad(AD_TYPE, adUnitId, false)
                AnalyticsHelper.logAdError(AD_TYPE, adUnitId, loadAdError.code.toString())
                Log.w(TAG, "Main back interstitial failed to load: ${loadAdError.message}")
                if (fallbackAdUnitId.isNotBlank()) {
                    NextGenAdHelper.startInterstitialPreload(fallbackAdUnitId)
                    loadFallback(context, fallbackAdUnitId)
                }
            }
        )
    }

    private fun loadFallback(context: Context, adUnitId: String) {
        if (isLoadingFallback || fallbackInterstitialAd != null) return

        NextGenAdHelper.pollInterstitial(adUnitId)?.let { preloadedAd ->
            fallbackInterstitialAd = preloadedAd
            AnalyticsHelper.logAdLoad(FALLBACK_AD_TYPE, adUnitId, true)
            return
        }

        isLoadingFallback = true
        NextGenAdHelper.loadInterstitial(
            adUnitId,
            onLoaded = { ad ->
                fallbackInterstitialAd = ad
                isLoadingFallback = false
                AnalyticsHelper.logAdLoad(FALLBACK_AD_TYPE, adUnitId, true)
            },
            onFailed = { loadAdError ->
                fallbackInterstitialAd = null
                isLoadingFallback = false
                AnalyticsHelper.logAdLoad(FALLBACK_AD_TYPE, adUnitId, false)
                AnalyticsHelper.logAdError(FALLBACK_AD_TYPE, adUnitId, loadAdError.code.toString())
                Log.w(TAG, "Main back fallback interstitial failed to load: ${loadAdError.message}")
            }
        )
    }

    fun showIfAvailable(activity: Activity, onFinish: () -> Unit): Boolean {
        val ad: InterstitialAd
        val adUnitId: String
        val adType: String
        if (primaryInterstitialAd != null) {
            ad = primaryInterstitialAd ?: return false
            adUnitId = currentPrimaryAdUnitId ?: AdConfig.resolveMainBackInterstitialAdUnitId(activity)
            adType = AD_TYPE
            primaryInterstitialAd = null
        } else if (fallbackInterstitialAd != null) {
            ad = fallbackInterstitialAd ?: return false
            adUnitId = currentFallbackAdUnitId ?: AdConfig.resolveMainBackFallbackInterstitialAdUnitId(activity)
            adType = FALLBACK_AD_TYPE
            fallbackInterstitialAd = null
        } else {
            return false
        }

        NextGenAdHelper.showInterstitial(
            activity = activity,
            ad = ad,
            onShowed = {
                AppOpenManager.suppressAppOpenFor(4_000L)
                AnalyticsHelper.logAdImpression(adType, adUnitId)
            },
            onClicked = {
                AnalyticsHelper.logAdClick(adType, adUnitId)
            },
            onDismissed = {
                completeAfterAd(activity, onFinish)
            },
            onFailedToShow = { adError ->
                AnalyticsHelper.logAdError(adType, adUnitId, adError.code.toString())
                completeAfterAd(activity, onFinish)
            }
        )
        return true
    }

    fun destroy() {
        primaryInterstitialAd = null
        fallbackInterstitialAd = null
        isLoadingPrimary = false
        isLoadingFallback = false
        currentPrimaryAdUnitId = null
        currentFallbackAdUnitId = null
    }

    private fun completeAfterAd(activity: Activity, onFinish: () -> Unit) {
        val appContext = activity.applicationContext
        AppOpenManager.suppressAppOpenFor(4_000L)
        mainHandler.postDelayed({
            if (!activity.isDestroyed) {
                onFinish()
            }
            preload(appContext)
        }, 80L)
    }
}
