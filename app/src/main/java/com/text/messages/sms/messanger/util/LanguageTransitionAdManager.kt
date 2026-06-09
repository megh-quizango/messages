package com.text.messages.sms.messanger.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

object LanguageTransitionAdManager {

    private const val TAG = "LanguageTransitionAds"

    private var interstitialAd: InterstitialAd? = null
    private var nativeFullscreenAd: NativeAd? = null
    private var isLoadingInterstitial = false
    private var isLoadingNativeFullscreen = false
    private var didInterstitialLoadFail = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun preload(context: Context) {
        val appContext = context.applicationContext
        if (RemoteConfigHelper.shouldUseLanguageNativeFullscreenOnly()) {
            loadNativeFullscreenAd(appContext)
            return
        }

        loadInterstitialAd(appContext)
        loadNativeFullscreenAd(appContext)
    }

    fun shouldUseNativeFullscreenOnly(): Boolean {
        return RemoteConfigHelper.shouldUseLanguageNativeFullscreenOnly()
    }

    fun showInterstitialIfAvailable(
        activity: Activity,
        onDismiss: () -> Unit,
        onFallbackToNative: () -> Unit
    ): Boolean {
        val ad = interstitialAd
        if (ad == null) {
            if (didInterstitialLoadFail && hasNativeFullscreenAd()) {
                onFallbackToNative()
                return true
            }
            return false
        }
        interstitialAd = null
        val adUnitId = RemoteConfigHelper.getLanguageInterstitialAdUnitId()

        NextGenAdHelper.showInterstitial(
            activity = activity,
            ad = ad,
            onShowed = {
                AppOpenManager.suppressAppOpenFor(4_000L)
                AnalyticsHelper.logAdImpression("interstitial", adUnitId)
            },
            onClicked = {
                AnalyticsHelper.logAdClick("interstitial", adUnitId)
            },
            onDismissed = {
                completeAfterAd(activity, onDismiss)
            },
            onFailedToShow = { adError ->
                AnalyticsHelper.logAdError("interstitial", adUnitId, adError.code.toString())
                completeAfterAd(activity, onDismiss)
            }
        )
        return true
    }

    private fun completeAfterAd(activity: Activity, onDismiss: () -> Unit) {
        val appContext = activity.applicationContext
        AppOpenManager.suppressAppOpenFor(4_000L)
        mainHandler.post {
            if (!activity.isDestroyed) {
                onDismiss()
            }
        }
        mainHandler.postDelayed({
            preload(appContext)
        }, 250L)
    }

    fun hasNativeFullscreenAd(): Boolean {
        return nativeFullscreenAd != null
    }

    fun consumeNativeFullscreenAd(): NativeAd? {
        val ad = nativeFullscreenAd
        nativeFullscreenAd = null
        return ad
    }

    fun reloadAfterNativeDismiss(context: Context) {
        preload(context.applicationContext)
    }

    private fun loadInterstitialAd(context: Context) {
        if (isLoadingInterstitial || interstitialAd != null) {
            return
        }

        val adUnitId = RemoteConfigHelper.getLanguageInterstitialAdUnitId()
        if (adUnitId.isBlank()) {
            return
        }

        isLoadingInterstitial = true
        didInterstitialLoadFail = false
        NextGenAdHelper.startInterstitialPreload(adUnitId)
        NextGenAdHelper.pollInterstitial(adUnitId)?.let { ad ->
            interstitialAd = ad
            isLoadingInterstitial = false
            didInterstitialLoadFail = false
            AnalyticsHelper.logAdLoad("interstitial", adUnitId, true)
            return
        }
        NextGenAdHelper.loadInterstitial(
            adUnitId,
            onLoaded = { ad ->
                interstitialAd = ad
                isLoadingInterstitial = false
                didInterstitialLoadFail = false
                AnalyticsHelper.logAdLoad("interstitial", adUnitId, true)
            },
            onFailed = { loadAdError ->
                interstitialAd = null
                isLoadingInterstitial = false
                didInterstitialLoadFail = true
                AnalyticsHelper.logAdLoad("interstitial", adUnitId, false)
                AnalyticsHelper.logAdError("interstitial", adUnitId, loadAdError.code.toString())
                Log.w(TAG, "Language interstitial failed to load: ${loadAdError.message}")
            }
        )
    }

    private fun loadNativeFullscreenAd(context: Context) {
        if (isLoadingNativeFullscreen || nativeFullscreenAd != null) {
            return
        }

        val adUnitId = RemoteConfigHelper.getLanguageNativeFullscreenAdUnitId()
        if (adUnitId.isBlank()) {
            return
        }

        isLoadingNativeFullscreen = true
        NextGenAdHelper.loadNative(
            adUnitId = adUnitId,
            preferLandscape = false,
            onLoaded = { ad ->
                nativeFullscreenAd?.destroy()
                nativeFullscreenAd = ad
                isLoadingNativeFullscreen = false
                AnalyticsHelper.logAdLoad("native_fullscreen", adUnitId, true)
            },
            onFailed = { loadAdError ->
                isLoadingNativeFullscreen = false
                AnalyticsHelper.logAdLoad("native_fullscreen", adUnitId, false)
                AnalyticsHelper.logAdError("native_fullscreen", adUnitId, loadAdError.code.toString())
                Log.w(TAG, "Language native fullscreen failed to load: ${loadAdError.message}")
            }
        )
    }
}
