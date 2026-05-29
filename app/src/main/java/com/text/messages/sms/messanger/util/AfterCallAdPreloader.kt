package com.text.messages.sms.messanger.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

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
        val appContext = context.applicationContext
        try {
            MobileAds.initialize(appContext) {
                loadNative(appContext, adUnitId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ad preload init failed", e)
            isLoading = false
        }
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

    private fun loadNative(context: Context, adUnitId: String) {
        com.google.android.gms.ads.AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                preloadedAd?.destroy()
                preloadedAd = nativeAd
                isLoading = false
                Log.d(TAG, "Native ad preloaded for after-call")
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    Log.d(TAG, "After-call ad preload failed: ${error.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
            .loadAd(AdRequest.Builder().build())
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
