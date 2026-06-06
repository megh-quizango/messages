package com.text.messages.sms.messanger.util

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest

object NextGenAdHelper {

    fun startInterstitialPreload(adUnitId: String) {
        if (adUnitId.isBlank()) return
        InterstitialAdPreloader.start(
            adUnitId,
            PreloadConfiguration(AdRequest.Builder(adUnitId).build())
        )
    }

    fun pollInterstitial(adUnitId: String): InterstitialAd? {
        return if (adUnitId.isBlank()) null else InterstitialAdPreloader.pollAd(adUnitId)
    }

    fun loadInterstitial(
        adUnitId: String,
        onLoaded: (InterstitialAd) -> Unit,
        onFailed: (LoadAdError) -> Unit
    ) {
        if (adUnitId.isBlank()) return
        InterstitialAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) = onLoaded(ad)
                override fun onAdFailedToLoad(adError: LoadAdError) = onFailed(adError)
            }
        )
    }

    fun showInterstitial(
        activity: Activity,
        ad: InterstitialAd,
        onShowed: () -> Unit,
        onClicked: () -> Unit,
        onDismissed: () -> Unit,
        onFailedToShow: (FullScreenContentError) -> Unit
    ) {
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() = onShowed()
            override fun onAdClicked() = onClicked()
            override fun onAdDismissedFullScreenContent() = onDismissed()
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                onFailedToShow(fullScreenContentError)
            }
        }
        ad.show(activity)
    }

    fun loadNative(
        adUnitId: String,
        preferLandscape: Boolean = true,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (LoadAdError) -> Unit,
        onCompleted: () -> Unit = {}
    ) {
        if (adUnitId.isBlank()) return
        val builder = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))
            .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
        if (preferLandscape) {
            builder.setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.LANDSCAPE)
        }
        NativeAdLoader.load(
            builder.build(),
            object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) = onLoaded(nativeAd)
                override fun onAdFailedToLoad(adError: LoadAdError) = onFailed(adError)
                override fun onAdLoadingCompleted() = onCompleted()
            }
        )
    }

    fun loadBanner(
        activity: Activity,
        adView: AdView,
        adUnitId: String,
        adSize: AdSize,
        onLoaded: (BannerAd) -> Unit,
        onFailed: (LoadAdError) -> Unit,
        onClicked: () -> Unit = {},
        onImpression: () -> Unit = {}
    ) {
        if (adUnitId.isBlank()) return
        adView.resize(adSize)
        adView.loadAd(
            BannerAdRequest.Builder(adUnitId, adSize).build(),
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    ad.adEventCallback = object : BannerAdEventCallback {
                        override fun onAdClicked() = onClicked()
                        override fun onAdImpression() = onImpression()
                    }
                    adView.registerBannerAd(ad, activity)
                    onLoaded(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) = onFailed(adError)
            }
        )
    }
}
