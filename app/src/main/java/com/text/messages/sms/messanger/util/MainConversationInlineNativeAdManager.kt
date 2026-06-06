package com.text.messages.sms.messanger.util

import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback

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

        NextGenAdHelper.loadNative(
            adUnitId = adUnitId,
            preferLandscape = true,
            onLoaded = { nativeAd ->
                nativeAd.adEventCallback = object : NativeAdEventCallback {
                    override fun onAdClicked() {
                        AnalyticsHelper.logAdClick(adType, adUnitId)
                    }

                    override fun onAdImpression() {
                        AnalyticsHelper.logAdImpression(adType, adUnitId)
                    }
                }
                nativeAds[slotIndex]?.destroy()
                nativeAds[slotIndex] = nativeAd
                loadingSlots[slotIndex] = false
                AnalyticsHelper.logAdLoad(adType, adUnitId, true)
                onAdsChanged()
            },
            onFailed = { loadAdError ->
                loadingSlots[slotIndex] = false
                AnalyticsHelper.logAdLoad(adType, adUnitId, false)
                AnalyticsHelper.logAdError(adType, adUnitId, loadAdError.code.toString())
                Log.w(TAG, "loadSlot: failed slot=$slotIndex code=${loadAdError.code} message=${loadAdError.message}")
                onAdsChanged()
            }
        )
    }
}
