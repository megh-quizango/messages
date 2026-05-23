package com.text.messages.sms.messanger.util

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdListener
import android.view.View
import android.view.ViewGroup

/**
 * Helper extension functions for loading ads with Remote Config and Analytics
 */
fun AdView.loadBannerAdWithRemoteConfig(): AdView {
    val remoteConfigAdUnitId = RemoteConfigHelper.getBannerAdUnitId().trim()
    val fallbackAdUnitId = this.adUnitId?.trim().orEmpty()
    val adUnitIdToUse = remoteConfigAdUnitId.ifBlank { fallbackAdUnitId }

    if (adUnitIdToUse.isBlank()) {
        android.util.Log.w("AdHelper", "Banner ad unit ID is blank in both Remote Config and XML, skipping banner load")
        this.visibility = View.GONE
        return this
    }

    val adViewToLoad = ensureBannerAdView(adUnitIdToUse)
    if (adViewToLoad.visibility != View.VISIBLE) {
        return adViewToLoad
    }
    AdLoadingShimmerHelper.showBannerLoading(adViewToLoad)

    if (adViewToLoad.adSize == null) {
        android.util.Log.e("AdHelper", "Ad size is not set, cannot load ad")
        AdLoadingShimmerHelper.hideBanner(adViewToLoad)
        return adViewToLoad
    }

    val adRequest = AdRequest.Builder().build()

    adViewToLoad.adListener = object : AdListener() {
        override fun onAdLoaded() {
            super.onAdLoaded()
            AdLoadingShimmerHelper.showBannerContent(adViewToLoad)
            AnalyticsHelper.logAdLoad("banner", adUnitIdToUse, true)
        }
        
        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            super.onAdFailedToLoad(loadAdError)
            AdLoadingShimmerHelper.hideBanner(adViewToLoad)
            AnalyticsHelper.logAdLoad("banner", adUnitIdToUse, false)
            AnalyticsHelper.logAdError("banner", adUnitIdToUse, loadAdError.code.toString())
        }
        
        override fun onAdClicked() {
            super.onAdClicked()
            AnalyticsHelper.logAdClick("banner", adUnitIdToUse)
        }
        
        override fun onAdImpression() {
            super.onAdImpression()
            AnalyticsHelper.logAdImpression("banner", adUnitIdToUse)
        }
    }

    adViewToLoad.loadAd(adRequest)
    return adViewToLoad
}

private fun AdView.ensureBannerAdView(desiredAdUnitId: String): AdView {
    val currentAdUnitId = this.adUnitId?.trim().orEmpty()

    if (currentAdUnitId.isBlank()) {
        android.util.Log.d("AdHelper", "Applying banner ad unit id: $desiredAdUnitId")
        this.adUnitId = desiredAdUnitId
        return this
    }

    if (currentAdUnitId == desiredAdUnitId) {
        return this
    }

    android.util.Log.d(
        "AdHelper",
        "Updating existing AdView ad unit id from '$currentAdUnitId' to '$desiredAdUnitId'."
    )
    this.adUnitId = desiredAdUnitId
    return this
}

