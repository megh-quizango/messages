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
    
    // Ensure ad unit ID is valid
    if (remoteConfigAdUnitId.isBlank()) {
        android.util.Log.e("AdHelper", "Banner ad unit ID is empty, cannot load ad")
        return
    }
    
    // AdView requires adUnitId to be set (either in XML or programmatically)
    // Since XML already has it set, we'll use the existing value
    // Remote Config values will be used when you update the XML files
    val adUnitIdToUse = this.adUnitId ?: remoteConfigAdUnitId
    
    // Log which ad unit ID is being used for analytics
    if (this.adUnitId != null && this.adUnitId != remoteConfigAdUnitId) {
        android.util.Log.d("AdHelper", "Using XML adUnitId: ${this.adUnitId}, Remote Config has: $remoteConfigAdUnitId")
    }
    
    // Ensure ad size is set (should be set in XML, but verify)
    if (this.adSize == null) {
        android.util.Log.e("AdHelper", "Ad size is not set, cannot load ad")
        return
    }
    
    val adRequest = AdRequest.Builder().build()
    
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

