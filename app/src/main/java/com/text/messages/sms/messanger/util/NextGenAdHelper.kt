package com.text.messages.sms.messanger.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

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
                override fun onAdLoaded(ad: InterstitialAd) {
                    runOnMain { onLoaded(ad) }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    runOnMain { onFailed(adError) }
                }
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
            override fun onAdShowedFullScreenContent() {
                runOnMain { onShowed() }
            }

            override fun onAdClicked() {
                runOnMain { onClicked() }
            }

            override fun onAdDismissedFullScreenContent() {
                runOnMain { onDismissed() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                runOnMain { onFailedToShow(fullScreenContentError) }
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
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    runOnMain { onLoaded(nativeAd) }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    runOnMain { onFailed(adError) }
                }

                override fun onAdLoadingCompleted() {
                    runOnMain { onCompleted() }
                }
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
                    runOnMain {
                        ad.adEventCallback = object : BannerAdEventCallback {
                            override fun onAdClicked() {
                                runOnMain { onClicked() }
                            }

                            override fun onAdImpression() {
                                runOnMain { onImpression() }
                            }
                        }
                        adView.registerBannerAd(ad, activity)
                        onLoaded(ad)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    runOnMain { onFailed(adError) }
                }
            }
        )
    }
}
