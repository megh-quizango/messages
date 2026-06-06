package com.text.messages.sms.messanger.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Preloads a native ad during RINGING/OFFHOOK so [CallAfterActivity] can show it faster.
 */
object AfterCallAdPreloader {

    private const val TAG = "AfterCallAdPreloader"

    @Volatile
    private var preloadedAd: NativeAd? = null

    @Volatile
    private var isLoading = false

    fun preloadIfNeeded(context: Context) {
        if (!hasNetwork(context)) return
        if (preloadedAd != null || isLoading) return
        val adUnitId = AdConfig.resolveAfterCallNativeAdUnitId(context)
        if (adUnitId.isBlank()) return

        isLoading = true
        loadNative(adUnitId)
    }

    fun consumePreloadedAd(): NativeAd? {
        val ad = preloadedAd
        preloadedAd = null
        return ad
    }

    fun clear() {
        preloadedAd?.destroy()
        preloadedAd = null
        isLoading = false
    }

    private fun loadNative(adUnitId: String) {
        NextGenAdHelper.loadNative(
            adUnitId = adUnitId,
            preferLandscape = true,
            onLoaded = { nativeAd ->
                preloadedAd?.destroy()
                preloadedAd = nativeAd
                isLoading = false
                Log.d(TAG, "Native ad preloaded for after-call")
            },
            onFailed = { error ->
                isLoading = false
                Log.d(TAG, "After-call ad preload failed: ${error.message}")
            }
        )
    }

    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }
}
