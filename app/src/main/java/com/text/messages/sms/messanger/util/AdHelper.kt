package com.text.messages.sms.messanger.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.text.messages.sms.messanger.R

/**
 * Helper extension functions for loading ads with Remote Config and Analytics.
 */
fun AdView.loadBannerAdWithRemoteConfig(): AdView {
    val adUnitIdToUse = AdConfig.resolveBannerAdUnitId(context).trim()

    if (adUnitIdToUse.isBlank()) {
        android.util.Log.w("AdHelper", "Banner ad unit ID is blank, skipping banner load")
        AdLoadingShimmerHelper.hideBanner(this)
        visibility = View.GONE
        return this
    }

    val adViewToLoad = ensureBannerAdView(adUnitIdToUse)
    if (adViewToLoad.visibility != View.VISIBLE) {
        return adViewToLoad
    }

    val activity = adViewToLoad.context as? Activity
    if (activity == null) {
        android.util.Log.w("AdHelper", "Banner context is not an Activity, skipping load")
        AdLoadingShimmerHelper.hideBanner(adViewToLoad)
        return adViewToLoad
    }

    val adWidth = adViewToLoad.width.takeIf { it > 0 }
        ?: (adViewToLoad.parent as? View)?.width?.takeIf { it > 0 }
        ?: adViewToLoad.resources.displayMetrics.widthPixels
    val adWidthDp = (adWidth / adViewToLoad.resources.displayMetrics.density).toInt().coerceAtLeast(320)
    val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(adViewToLoad.context, adWidthDp)

    AdLoadingShimmerHelper.showBannerLoading(adViewToLoad)
    adViewToLoad.resize(adSize)

    val adRequest = BannerAdRequest.Builder(adUnitIdToUse, adSize).build()
    adViewToLoad.loadAd(
        adRequest,
        object : AdLoadCallback<BannerAd> {
            private val mainHandler = Handler(Looper.getMainLooper())

            private fun runOnMain(block: () -> Unit) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    block()
                } else {
                    mainHandler.post(block)
                }
            }

            override fun onAdLoaded(ad: BannerAd) {
                runOnMain {
                    ad.adEventCallback = object : com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback {
                        override fun onAdClicked() {
                            runOnMain { AnalyticsHelper.logAdClick("banner", adUnitIdToUse) }
                        }

                        override fun onAdImpression() {
                            runOnMain { AnalyticsHelper.logAdImpression("banner", adUnitIdToUse) }
                        }
                    }
                    adViewToLoad.registerBannerAd(ad, activity)
                    AdLoadingShimmerHelper.showBannerContent(adViewToLoad)
                    AnalyticsHelper.logAdLoad("banner", adUnitIdToUse, true)
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                runOnMain {
                    AdLoadingShimmerHelper.hideBanner(adViewToLoad)
                    AnalyticsHelper.logAdLoad("banner", adUnitIdToUse, false)
                    AnalyticsHelper.logAdError("banner", adUnitIdToUse, loadAdError.code.toString())
                    android.util.Log.w(
                        "AdHelper",
                        "Banner failed: code=${loadAdError.code} message=${loadAdError.message} unit=$adUnitIdToUse"
                    )
                }
            }
        }
    )
    return adViewToLoad
}

private fun AdView.ensureBannerAdView(desiredAdUnitId: String): AdView {
    val currentAdUnitId = getTag(R.id.ad_unit_id_tag) as? String
    if (currentAdUnitId == null) {
        setTag(R.id.ad_unit_id_tag, desiredAdUnitId)
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
        setTag(R.id.ad_unit_id_tag, desiredAdUnitId)
        return this
    }

    android.util.Log.d(
        "AdHelper",
        "Replacing AdView to switch ad unit id from '$currentAdUnitId' to '$desiredAdUnitId'."
    )

    val replacement = AdView(context).apply {
        id = this@ensureBannerAdView.id
        layoutParams = copyLayoutParams(this@ensureBannerAdView.layoutParams)
        visibility = this@ensureBannerAdView.visibility
        setTag(R.id.ad_unit_id_tag, desiredAdUnitId)
    }

    val childIndex = parentGroup.indexOfChild(this)
    parentGroup.removeView(this)
    parentGroup.addView(replacement, childIndex)
    destroy()

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
