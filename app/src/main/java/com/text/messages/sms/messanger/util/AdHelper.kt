package com.text.messages.sms.messanger.util

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdListener
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Helper extension functions for loading ads with Remote Config and Analytics
 */
fun AdView.loadBannerAdWithRemoteConfig(): AdView {
    val adUnitIdToUse = AdConfig.resolveBannerAdUnitId(context).trim()

    if (adUnitIdToUse.isBlank()) {
        android.util.Log.w("AdHelper", "Banner ad unit ID is blank, skipping banner load")
        AdLoadingShimmerHelper.hideBanner(this)
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
            android.util.Log.w(
                "AdHelper",
                "Banner failed: code=${loadAdError.code} domain=${loadAdError.domain} " +
                    "message=${loadAdError.message} unit=$adUnitIdToUse"
            )
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
    val currentAdUnitId = this.adUnitId.trim()

    if (currentAdUnitId.isBlank()) {
        android.util.Log.d("AdHelper", "Applying banner ad unit id: $desiredAdUnitId")
        this.adUnitId = desiredAdUnitId
        return this
    }

    if (currentAdUnitId == desiredAdUnitId) {
        return this
    }

    val parentGroup = parent as? ViewGroup
    if (parentGroup == null) {
        android.util.Log.w(
            "AdHelper",
            "AdView already has ad unit id '$currentAdUnitId' and has no parent for replacement. Using existing view."
        )
        return this
    }

    android.util.Log.d(
        "AdHelper",
        "Replacing AdView to switch ad unit id from '$currentAdUnitId' to '$desiredAdUnitId'."
    )

    val currentAdSize = this.adSize
    if (currentAdSize == null) {
        android.util.Log.w("AdHelper", "Current AdView has no ad size set. Using existing view.")
        return this
    }

    val replacement = AdView(context).apply {
        id = this@ensureBannerAdView.id
        layoutParams = copyLayoutParams(this@ensureBannerAdView.layoutParams)
        setAdSize(currentAdSize)
        adUnitId = desiredAdUnitId
        visibility = this@ensureBannerAdView.visibility
    }

    val childIndex = parentGroup.indexOfChild(this)
    parentGroup.removeView(this)
    parentGroup.addView(replacement, childIndex)
    this.destroy()

    return replacement
}

private fun copyLayoutParams(layoutParams: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
    return when (layoutParams) {
        is ConstraintLayout.LayoutParams -> ConstraintLayout.LayoutParams(layoutParams)
        is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(layoutParams)
        is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(layoutParams)
        is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(layoutParams)
        else -> ViewGroup.LayoutParams(layoutParams)
    }
}

