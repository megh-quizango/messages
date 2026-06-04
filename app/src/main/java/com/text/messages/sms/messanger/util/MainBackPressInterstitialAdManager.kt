package com.text.messages.sms.messanger.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object MainBackPressInterstitialAdManager {

    private const val TAG = "MainBackPressInterstitial"
    private const val AD_TYPE = "main_back_interstitial"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var currentAdUnitId: String? = null

    fun preload(context: Context) {
        val appContext = context.applicationContext
        val adUnitId = AdConfig.resolveMainBackInterstitialAdUnitId(appContext).trim()
        if (adUnitId.isBlank()) {
            destroy()
            return
        }

        if (currentAdUnitId != null && currentAdUnitId != adUnitId) {
            destroy()
        }
        currentAdUnitId = adUnitId

        if (isLoading || interstitialAd != null) return

        isLoading = true
        InterstitialAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    AnalyticsHelper.logAdLoad(AD_TYPE, adUnitId, true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    AnalyticsHelper.logAdLoad(AD_TYPE, adUnitId, false)
                    AnalyticsHelper.logAdError(AD_TYPE, adUnitId, loadAdError.code.toString())
                    Log.w(TAG, "Main back interstitial failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    fun showIfAvailable(activity: Activity, onFinish: () -> Unit): Boolean {
        val ad = interstitialAd ?: return false
        val adUnitId = currentAdUnitId ?: AdConfig.resolveMainBackInterstitialAdUnitId(activity)
        interstitialAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AnalyticsHelper.logAdImpression(AD_TYPE, adUnitId)
            }

            override fun onAdClicked() {
                AnalyticsHelper.logAdClick(AD_TYPE, adUnitId)
            }

            override fun onAdDismissedFullScreenContent() {
                onFinish()
                preload(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                AnalyticsHelper.logAdError(AD_TYPE, adUnitId, adError.code.toString())
                onFinish()
                preload(activity.applicationContext)
            }
        }

        ad.show(activity)
        return true
    }

    fun destroy() {
        interstitialAd = null
        isLoading = false
        currentAdUnitId = null
    }
}
