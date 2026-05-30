package com.text.messages.sms.messanger.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

object ImExTransitionAdManager {

    private const val TAG = "ImExTransitionAds"

    private var interstitialAd: InterstitialAd? = null
    private var nativeFullscreenAd: NativeAd? = null
    private var isLoadingInterstitial = false
    private var isLoadingNativeFullscreen = false
    private var didInterstitialLoadFail = false

    fun preload(context: Context) {
        val appContext = context.applicationContext
        if (RemoteConfigHelper.shouldUseImExNativeFullscreenOnly()) {
            loadNativeFullscreenAd(appContext)
            return
        }

        loadInterstitialAd(appContext)
        loadNativeFullscreenAd(appContext)
    }

    fun shouldUseNativeFullscreenOnly(): Boolean {
        return RemoteConfigHelper.shouldUseImExNativeFullscreenOnly()
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
        val adUnitId = AdConfig.resolveImExInterstitialAdUnitId(activity)

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AnalyticsHelper.logAdImpression("interstitial", adUnitId)
            }

            override fun onAdClicked() {
                AnalyticsHelper.logAdClick("interstitial", adUnitId)
            }

            override fun onAdDismissedFullScreenContent() {
                onDismiss()
                preload(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                AnalyticsHelper.logAdError("interstitial", adUnitId, adError.code.toString())
                onDismiss()
                preload(activity.applicationContext)
            }
        }

        ad.show(activity)
        return true
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

        val adUnitId = AdConfig.resolveImExInterstitialAdUnitId(context)
        if (adUnitId.isBlank()) {
            return
        }

        isLoadingInterstitial = true
        didInterstitialLoadFail = false
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                    didInterstitialLoadFail = false
                    AnalyticsHelper.logAdLoad("interstitial", adUnitId, true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
                    didInterstitialLoadFail = true
                    AnalyticsHelper.logAdLoad("interstitial", adUnitId, false)
                    AnalyticsHelper.logAdError("interstitial", adUnitId, loadAdError.code.toString())
                    Log.w(TAG, "ImEx interstitial failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    private fun loadNativeFullscreenAd(context: Context) {
        if (isLoadingNativeFullscreen || nativeFullscreenAd != null) {
            return
        }

        val adUnitId = AdConfig.resolveImExNativeFullscreenAdUnitId(context)
        if (adUnitId.isBlank()) {
            return
        }

        isLoadingNativeFullscreen = true
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeFullscreenAd?.destroy()
                nativeFullscreenAd = ad
                isLoadingNativeFullscreen = false
                AnalyticsHelper.logAdLoad("native_fullscreen", adUnitId, true)
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingNativeFullscreen = false
                    AnalyticsHelper.logAdLoad("native_fullscreen", adUnitId, false)
                    AnalyticsHelper.logAdError("native_fullscreen", adUnitId, loadAdError.code.toString())
                    Log.w(TAG, "ImEx native fullscreen failed to load: ${loadAdError.message}")
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}
