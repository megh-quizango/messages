package com.text.messages.sms.messanger.util

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.AdListener

/**
 * Helper extension functions for loading ads with Remote Config and Analytics
 */
fun AdView.loadBannerAdWithRemoteConfig() {
    val remoteConfigAdUnitId = RemoteConfigHelper.getBannerAdUnitId()
    
    // Only load when Remote Config provides a real ad unit id.
    if (remoteConfigAdUnitId.isBlank()) {
        android.util.Log.w("AdHelper", "Banner ad unit ID is blank in Remote Config, skipping banner load")
        this.visibility = android.view.View.GONE
        return
    }

    if (this.adUnitId != remoteConfigAdUnitId) {
        android.util.Log.d("AdHelper", "Applying Remote Config banner ad unit id: $remoteConfigAdUnitId")
    }
    this.visibility = android.view.View.VISIBLE
    this.adUnitId = remoteConfigAdUnitId
    
    // Ensure ad size is set (should be set in XML, but verify)
    if (this.adSize == null) {
        android.util.Log.e("AdHelper", "Ad size is not set, cannot load ad")
        return
    }
    
    val adRequest = AdRequest.Builder().build()
    val adUnitIdToUse = remoteConfigAdUnitId
    
    this.adListener = object : AdListener() {
        override fun onAdLoaded() {
            super.onAdLoaded()
            AnalyticsHelper.logAdLoad("banner", adUnitIdToUse, true)
        }
        
        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            super.onAdFailedToLoad(loadAdError)
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
    
    this.loadAd(adRequest)
}

