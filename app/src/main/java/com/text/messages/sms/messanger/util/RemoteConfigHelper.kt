package com.text.messages.sms.messanger.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object RemoteConfigHelper {
    private const val TAG = "RemoteConfigHelper"
    
    private lateinit var remoteConfig: FirebaseRemoteConfig
    
    // Default ad unit IDs (fallback values)
    private const val DEFAULT_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val DEFAULT_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val DEFAULT_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val DEFAULT_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    
    // Remote Config keys
    private const val KEY_BANNER_AD_UNIT_ID = "banner_ad_unit_id"
    private const val KEY_NATIVE_AD_UNIT_ID = "native_ad_unit_id"
    private const val KEY_APP_OPEN_AD_UNIT_ID = "app_open_ad_unit_id"
    private const val KEY_ADMOB_APP_ID = "admob_app_id"
    
    fun initialize(remoteConfigInstance: FirebaseRemoteConfig) {
        remoteConfig = remoteConfigInstance
        
        // Configure Remote Config settings
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour in production, can be lower for testing
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Set default values (async, but getter methods have fallbacks)
        val defaultValues = mapOf(
            KEY_BANNER_AD_UNIT_ID to DEFAULT_BANNER_AD_UNIT_ID,
            KEY_NATIVE_AD_UNIT_ID to DEFAULT_NATIVE_AD_UNIT_ID,
            KEY_APP_OPEN_AD_UNIT_ID to DEFAULT_APP_OPEN_AD_UNIT_ID,
            KEY_ADMOB_APP_ID to DEFAULT_ADMOB_APP_ID
        )
        remoteConfig.setDefaultsAsync(defaultValues)
        
        // Fetch and activate
        fetchAndActivate()
    }
    
    private fun fetchAndActivate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Remote Config fetch ${if (updated) "succeeded" else "no changes"}")
                } else {
                    Log.e(TAG, "Remote Config fetch failed", task.exception)
                }
            }
    }
    
    fun getBannerAdUnitId(): String {
        return if (::remoteConfig.isInitialized) {
            val adUnitId = remoteConfig.getString(KEY_BANNER_AD_UNIT_ID)
            // Return default if Remote Config returns empty string
            if (adUnitId.isBlank()) {
                DEFAULT_BANNER_AD_UNIT_ID
            } else {
                adUnitId
            }
        } else {
            DEFAULT_BANNER_AD_UNIT_ID
        }
    }
    
    fun getNativeAdUnitId(): String {
        return if (::remoteConfig.isInitialized) {
            remoteConfig.getString(KEY_NATIVE_AD_UNIT_ID)
        } else {
            DEFAULT_NATIVE_AD_UNIT_ID
        }
    }
    
    fun getAppOpenAdUnitId(): String {
        return if (::remoteConfig.isInitialized) {
            remoteConfig.getString(KEY_APP_OPEN_AD_UNIT_ID)
        } else {
            DEFAULT_APP_OPEN_AD_UNIT_ID
        }
    }
    
    fun getAdMobAppId(): String {
        return if (::remoteConfig.isInitialized) {
            remoteConfig.getString(KEY_ADMOB_APP_ID)
        } else {
            DEFAULT_ADMOB_APP_ID
        }
    }
    
    fun fetchRemoteConfig() {
        if (::remoteConfig.isInitialized) {
            remoteConfig.fetch()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        remoteConfig.activate()
                        Log.d(TAG, "Remote Config fetched and activated")
                    } else {
                        Log.e(TAG, "Remote Config fetch failed", task.exception)
                    }
                }
        }
    }
}

