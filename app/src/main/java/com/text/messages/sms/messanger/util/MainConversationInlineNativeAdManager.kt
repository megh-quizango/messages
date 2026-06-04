package com.text.messages.sms.messanger.util

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

object MainConversationInlineNativeAdManager {
    private const val TAG = "MainConversationInlineNativeAd"
    private const val SLOT_COUNT = 2

    private val nativeAds = arrayOfNulls<NativeAd>(SLOT_COUNT)
    private val loadingSlots = BooleanArray(SLOT_COUNT)
    private var currentAdUnitId: String? = null

    fun load(context: Context, onAdsChanged: () -> Unit) {
        val appContext = context.applicationContext
        val adUnitId = AdConfig.resolveMainConversationInlineNativeAdUnitId(appContext).trim()
        if (adUnitId.isBlank()) {
            Log.w(TAG, "load: main conversation inline native ad unit is blank")
            return
        }

        if (currentAdUnitId != null && currentAdUnitId != adUnitId) {
            destroy()
        }
        currentAdUnitId = adUnitId

        for (slotIndex in 0 until SLOT_COUNT) {
            if (nativeAds[slotIndex] != null || loadingSlots[slotIndex]) continue
            loadSlot(appContext, adUnitId, slotIndex, onAdsChanged)
        }
    }

    fun getAds(): List<NativeAd?> {
        return nativeAds.toList()
    }

    fun destroy() {
        nativeAds.forEachIndexed { index, nativeAd ->
            nativeAd?.destroy()
            nativeAds[index] = null
            loadingSlots[index] = false
        }
        currentAdUnitId = null
    }

    private fun loadSlot(
        context: Context,
        adUnitId: String,
        slotIndex: Int,
        onAdsChanged: () -> Unit
    ) {
        loadingSlots[slotIndex] = true
        val adType = "main_inline_native_${slotIndex + 1}"

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                nativeAds[slotIndex]?.destroy()
                nativeAds[slotIndex] = nativeAd
                loadingSlots[slotIndex] = false
                AnalyticsHelper.logAdLoad(adType, adUnitId, true)
                onAdsChanged()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    loadingSlots[slotIndex] = false
                    AnalyticsHelper.logAdLoad(adType, adUnitId, false)
                    AnalyticsHelper.logAdError(adType, adUnitId, loadAdError.code.toString())
                    Log.w(TAG, "loadSlot: failed slot=$slotIndex code=${loadAdError.code} message=${loadAdError.message}")
                    onAdsChanged()
                }

                override fun onAdClicked() {
                    AnalyticsHelper.logAdClick(adType, adUnitId)
                }

                override fun onAdImpression() {
                    AnalyticsHelper.logAdImpression(adType, adUnitId)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}
