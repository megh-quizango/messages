package com.text.messages.sms.messanger.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.text.messages.sms.messanger.R

object RemoteConfigHelper {
    private const val TAG = "RemoteConfigHelper"

    @Volatile
    private var remoteConfig: FirebaseRemoteConfig? = null

    // Leave ad unit defaults blank so ad loaders only use fetched/cached Remote Config values.
    private const val DEFAULT_BANNER_AD_UNIT_ID = ""
    private const val DEFAULT_NATIVE_AD_UNIT_ID = ""
    private const val DEFAULT_NATIVE_VIDEO_AD_UNIT_ID = ""
    private const val DEFAULT_APP_OPEN_AD_UNIT_ID = ""
    private const val DEFAULT_APP_OPEN_RESUME_AD_UNIT_ID = ""
    private const val DEFAULT_ADMOB_APP_ID = ""
    private const val DEFAULT_LANGUAGE_NATIVE_BANNER_AD_UNIT_ID = ""
    private const val DEFAULT_LANGUAGE_FALLBACK_BANNER_AD_UNIT_ID = ""
    private const val DEFAULT_LANGUAGE_ADAPTIVE_BANNER_ONLY = false
    private const val DEFAULT_AFTER_CALL_ADAPTIVE_BANNER_AD_UNIT_ID = ""
    private const val DEFAULT_AFTER_CALL_ADAPTIVE_BANNER_ONLY = false
    private const val DEFAULT_EXIT_NATIVE_BANNER_AD_UNIT_ID = ""
    private const val DEFAULT_EXIT_FALLBACK_BANNER_AD_UNIT_ID = ""
    private const val DEFAULT_EXIT_ADAPTIVE_BANNER_ONLY = false
    private const val DEFAULT_MANAGE_APPS_ADAPTIVE_BANNER_AD_UNIT_ID = ""
    private const val DEFAULT_MANAGE_APPS_ADAPTIVE_BANNER_ONLY = false
    private const val DEFAULT_LANGUAGE_INTERSTITIAL_AD_UNIT_ID = ""
    private const val DEFAULT_LANGUAGE_NATIVE_FULLSCREEN_AD_UNIT_ID = ""
    private const val DEFAULT_LANGUAGE_NATIVE_FULLSCREEN_ONLY = false
    private const val DEFAULT_THEME_INTERSTITIAL_AD_UNIT_ID = ""
    private const val DEFAULT_THEME_NATIVE_FULLSCREEN_AD_UNIT_ID = ""
    private const val DEFAULT_THEME_NATIVE_FULLSCREEN_ONLY = false
    private const val DEFAULT_IMEX_INTERSTITIAL_AD_UNIT_ID = ""
    private const val DEFAULT_IMEX_NATIVE_FULLSCREEN_AD_UNIT_ID = ""
    private const val DEFAULT_IMEX_NATIVE_FULLSCREEN_ONLY = false

    // Remote Config keys
    private const val KEY_BANNER_AD_UNIT_ID = "banner_ad_unit_id"
    private const val KEY_NATIVE_AD_UNIT_ID = "native_ad_unit_id"
    private const val KEY_AFTER_CALL_NATIVE_AD_UNIT_ID = "after_call_native_ad_unit_id"
    private const val KEY_AFTER_CALL_ADAPTIVE_BANNER_AD_UNIT_ID = "after_call_adaptive_banner_ad_unit_id"
    private const val KEY_AFTER_CALL_ADAPTIVE_BANNER_ONLY = "after_call_adaptive_banner_only"
    private const val KEY_NATIVE_VIDEO_AD_UNIT_ID = "native_video_ad_unit_id"
    private const val KEY_APP_OPEN_AD_UNIT_ID = "app_open_ad_unit_id"
    private const val KEY_APP_OPEN_RESUME_AD_UNIT_ID = "app_open_resume_ad_unit_id"
    private const val KEY_ADMOB_APP_ID = "admob_app_id"
    private const val KEY_LANGUAGE_NATIVE_BANNER_AD_UNIT_ID = "native_banner_ad_language"
    private const val KEY_LANGUAGE_FALLBACK_BANNER_AD_UNIT_ID = "banner_language_fallback"
    private const val KEY_LANGUAGE_ADAPTIVE_BANNER_ONLY = "language_adaptive_banner_only"
    private const val KEY_EXIT_NATIVE_BANNER_AD_UNIT_ID = "native_banner_exit"
    private const val KEY_EXIT_FALLBACK_BANNER_AD_UNIT_ID = "banner_exit_fallback"
    private const val KEY_EXIT_ADAPTIVE_BANNER_ONLY = "exit_adaptive_banner_only"
    private const val KEY_MANAGE_APPS_ADAPTIVE_BANNER_AD_UNIT_ID = "manage_apps_adaptive_banner_ad_unit_id"
    private const val KEY_MANAGE_APPS_ADAPTIVE_BANNER_ONLY = "manage_apps_adaptive_banner_only"
    private const val KEY_LANGUAGE_INTERSTITIAL_AD_UNIT_ID = "interstitial_language"
    private const val KEY_LANGUAGE_NATIVE_FULLSCREEN_AD_UNIT_ID = "native_fullscreen_language"
    private const val KEY_LANGUAGE_NATIVE_FULLSCREEN_ONLY = "language_native_fullscreen_only"
    private const val KEY_THEME_INTERSTITIAL_AD_UNIT_ID = "interstitial_theme"
    private const val KEY_THEME_NATIVE_FULLSCREEN_AD_UNIT_ID = "native_fullscreen_theme"
    private const val KEY_THEME_NATIVE_FULLSCREEN_ONLY = "theme_native_fullscreen_only"
    private const val KEY_IMEX_INTERSTITIAL_AD_UNIT_ID = "interstitial_imex"
    private const val KEY_IMEX_NATIVE_FULLSCREEN_AD_UNIT_ID = "native_fullscreen_imex"
    private const val KEY_IMEX_NATIVE_FULLSCREEN_ONLY = "imex_native_fullscreen_only"

    fun initialize(remoteConfigInstance: FirebaseRemoteConfig) {
        remoteConfig = remoteConfigInstance

        // Configure Remote Config settings
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour in production
            .build()
        remoteConfigInstance.setConfigSettingsAsync(configSettings)

        // Set default values
        val defaultValues = mapOf(
            KEY_BANNER_AD_UNIT_ID to DEFAULT_BANNER_AD_UNIT_ID,
            KEY_NATIVE_AD_UNIT_ID to DEFAULT_NATIVE_AD_UNIT_ID,
            KEY_NATIVE_VIDEO_AD_UNIT_ID to DEFAULT_NATIVE_VIDEO_AD_UNIT_ID,
            KEY_APP_OPEN_AD_UNIT_ID to DEFAULT_APP_OPEN_AD_UNIT_ID,
            KEY_APP_OPEN_RESUME_AD_UNIT_ID to DEFAULT_APP_OPEN_RESUME_AD_UNIT_ID,
            KEY_ADMOB_APP_ID to DEFAULT_ADMOB_APP_ID,
            KEY_LANGUAGE_NATIVE_BANNER_AD_UNIT_ID to DEFAULT_LANGUAGE_NATIVE_BANNER_AD_UNIT_ID,
            KEY_LANGUAGE_FALLBACK_BANNER_AD_UNIT_ID to DEFAULT_LANGUAGE_FALLBACK_BANNER_AD_UNIT_ID,
            KEY_LANGUAGE_ADAPTIVE_BANNER_ONLY to DEFAULT_LANGUAGE_ADAPTIVE_BANNER_ONLY,
            KEY_AFTER_CALL_ADAPTIVE_BANNER_AD_UNIT_ID to DEFAULT_AFTER_CALL_ADAPTIVE_BANNER_AD_UNIT_ID,
            KEY_AFTER_CALL_ADAPTIVE_BANNER_ONLY to DEFAULT_AFTER_CALL_ADAPTIVE_BANNER_ONLY,
            KEY_EXIT_NATIVE_BANNER_AD_UNIT_ID to DEFAULT_EXIT_NATIVE_BANNER_AD_UNIT_ID,
            KEY_EXIT_FALLBACK_BANNER_AD_UNIT_ID to DEFAULT_EXIT_FALLBACK_BANNER_AD_UNIT_ID,
            KEY_EXIT_ADAPTIVE_BANNER_ONLY to DEFAULT_EXIT_ADAPTIVE_BANNER_ONLY,
            KEY_MANAGE_APPS_ADAPTIVE_BANNER_AD_UNIT_ID to DEFAULT_MANAGE_APPS_ADAPTIVE_BANNER_AD_UNIT_ID,
            KEY_MANAGE_APPS_ADAPTIVE_BANNER_ONLY to DEFAULT_MANAGE_APPS_ADAPTIVE_BANNER_ONLY,
            KEY_LANGUAGE_INTERSTITIAL_AD_UNIT_ID to DEFAULT_LANGUAGE_INTERSTITIAL_AD_UNIT_ID,
            KEY_LANGUAGE_NATIVE_FULLSCREEN_AD_UNIT_ID to DEFAULT_LANGUAGE_NATIVE_FULLSCREEN_AD_UNIT_ID,
            KEY_LANGUAGE_NATIVE_FULLSCREEN_ONLY to DEFAULT_LANGUAGE_NATIVE_FULLSCREEN_ONLY,
            KEY_THEME_INTERSTITIAL_AD_UNIT_ID to DEFAULT_THEME_INTERSTITIAL_AD_UNIT_ID,
            KEY_THEME_NATIVE_FULLSCREEN_AD_UNIT_ID to DEFAULT_THEME_NATIVE_FULLSCREEN_AD_UNIT_ID,
            KEY_THEME_NATIVE_FULLSCREEN_ONLY to DEFAULT_THEME_NATIVE_FULLSCREEN_ONLY,
            KEY_IMEX_INTERSTITIAL_AD_UNIT_ID to DEFAULT_IMEX_INTERSTITIAL_AD_UNIT_ID,
            KEY_IMEX_NATIVE_FULLSCREEN_AD_UNIT_ID to DEFAULT_IMEX_NATIVE_FULLSCREEN_AD_UNIT_ID,
            KEY_IMEX_NATIVE_FULLSCREEN_ONLY to DEFAULT_IMEX_NATIVE_FULLSCREEN_ONLY
        )
        remoteConfigInstance.setDefaultsAsync(defaultValues)
        remoteConfigInstance.setDefaultsAsync(R.xml.remote_config_defaults)

        // Fetch and activate
        fetchAndActivate()
    }

    private fun fetchAndActivate() {
        val config = remoteConfig ?: return
        config.fetchAndActivate()
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
        val config = remoteConfig ?: return DEFAULT_BANNER_AD_UNIT_ID
        val adUnitId = config.getString(KEY_BANNER_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_BANNER_AD_UNIT_ID else adUnitId
    }

    fun getNativeAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_NATIVE_AD_UNIT_ID
        val adUnitId = config.getString(KEY_NATIVE_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_NATIVE_AD_UNIT_ID else adUnitId
    }

    /** Dedicated native unit for the after-call screen (Firebase key: after_call_native_ad_unit_id). */
    fun getAfterCallNativeAdUnitId(): String {
        val config = remoteConfig ?: return ""
        val adUnitId = config.getString(KEY_AFTER_CALL_NATIVE_AD_UNIT_ID).trim()
        return adUnitId
    }

    fun getAfterCallAdaptiveBannerAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_AFTER_CALL_ADAPTIVE_BANNER_AD_UNIT_ID
        val adUnitId = config.getString(KEY_AFTER_CALL_ADAPTIVE_BANNER_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_AFTER_CALL_ADAPTIVE_BANNER_AD_UNIT_ID else adUnitId
    }

    fun shouldUseAfterCallAdaptiveBannerOnly(): Boolean {
        val config = remoteConfig ?: return DEFAULT_AFTER_CALL_ADAPTIVE_BANNER_ONLY
        return config.getBoolean(KEY_AFTER_CALL_ADAPTIVE_BANNER_ONLY)
    }

    fun getAppOpenAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_APP_OPEN_AD_UNIT_ID
        val adUnitId = config.getString(KEY_APP_OPEN_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_APP_OPEN_AD_UNIT_ID else adUnitId
    }

    fun getAppOpenResumeAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_APP_OPEN_RESUME_AD_UNIT_ID
        val adUnitId = config.getString(KEY_APP_OPEN_RESUME_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_APP_OPEN_RESUME_AD_UNIT_ID else adUnitId
    }

    fun getNativeVideoAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_NATIVE_VIDEO_AD_UNIT_ID
        val adUnitId = config.getString(KEY_NATIVE_VIDEO_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_NATIVE_VIDEO_AD_UNIT_ID else adUnitId
    }

    fun getAdMobAppId(): String {
        val config = remoteConfig ?: return DEFAULT_ADMOB_APP_ID
        val appId = config.getString(KEY_ADMOB_APP_ID).trim()
        return if (appId.isBlank()) DEFAULT_ADMOB_APP_ID else appId
    }

    fun getLanguageNativeBannerAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_LANGUAGE_NATIVE_BANNER_AD_UNIT_ID
        val adUnitId = config.getString(KEY_LANGUAGE_NATIVE_BANNER_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_LANGUAGE_NATIVE_BANNER_AD_UNIT_ID else adUnitId
    }

    fun getLanguageFallbackBannerAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_LANGUAGE_FALLBACK_BANNER_AD_UNIT_ID
        val adUnitId = config.getString(KEY_LANGUAGE_FALLBACK_BANNER_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_LANGUAGE_FALLBACK_BANNER_AD_UNIT_ID else adUnitId
    }

    fun shouldUseLanguageAdaptiveBannerOnly(): Boolean {
        val config = remoteConfig ?: return DEFAULT_LANGUAGE_ADAPTIVE_BANNER_ONLY
        return config.getBoolean(KEY_LANGUAGE_ADAPTIVE_BANNER_ONLY)
    }

    fun getExitNativeBannerAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_EXIT_NATIVE_BANNER_AD_UNIT_ID
        val adUnitId = config.getString(KEY_EXIT_NATIVE_BANNER_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_EXIT_NATIVE_BANNER_AD_UNIT_ID else adUnitId
    }

    fun getExitFallbackBannerAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_EXIT_FALLBACK_BANNER_AD_UNIT_ID
        val adUnitId = config.getString(KEY_EXIT_FALLBACK_BANNER_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_EXIT_FALLBACK_BANNER_AD_UNIT_ID else adUnitId
    }

    fun shouldUseExitAdaptiveBannerOnly(): Boolean {
        val config = remoteConfig ?: return DEFAULT_EXIT_ADAPTIVE_BANNER_ONLY
        return config.getBoolean(KEY_EXIT_ADAPTIVE_BANNER_ONLY)
    }

    fun getManageAppsAdaptiveBannerAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_MANAGE_APPS_ADAPTIVE_BANNER_AD_UNIT_ID
        val adUnitId = config.getString(KEY_MANAGE_APPS_ADAPTIVE_BANNER_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_MANAGE_APPS_ADAPTIVE_BANNER_AD_UNIT_ID else adUnitId
    }

    fun shouldUseManageAppsAdaptiveBannerOnly(): Boolean {
        val config = remoteConfig ?: return DEFAULT_MANAGE_APPS_ADAPTIVE_BANNER_ONLY
        return config.getBoolean(KEY_MANAGE_APPS_ADAPTIVE_BANNER_ONLY)
    }

    fun getLanguageInterstitialAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_LANGUAGE_INTERSTITIAL_AD_UNIT_ID
        val adUnitId = config.getString(KEY_LANGUAGE_INTERSTITIAL_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_LANGUAGE_INTERSTITIAL_AD_UNIT_ID else adUnitId
    }

    fun getLanguageNativeFullscreenAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_LANGUAGE_NATIVE_FULLSCREEN_AD_UNIT_ID
        val adUnitId = config.getString(KEY_LANGUAGE_NATIVE_FULLSCREEN_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_LANGUAGE_NATIVE_FULLSCREEN_AD_UNIT_ID else adUnitId
    }

    fun shouldUseLanguageNativeFullscreenOnly(): Boolean {
        val config = remoteConfig ?: return DEFAULT_LANGUAGE_NATIVE_FULLSCREEN_ONLY
        return config.getBoolean(KEY_LANGUAGE_NATIVE_FULLSCREEN_ONLY)
    }

    fun getThemeInterstitialAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_THEME_INTERSTITIAL_AD_UNIT_ID
        val adUnitId = config.getString(KEY_THEME_INTERSTITIAL_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_THEME_INTERSTITIAL_AD_UNIT_ID else adUnitId
    }

    fun getThemeNativeFullscreenAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_THEME_NATIVE_FULLSCREEN_AD_UNIT_ID
        val adUnitId = config.getString(KEY_THEME_NATIVE_FULLSCREEN_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_THEME_NATIVE_FULLSCREEN_AD_UNIT_ID else adUnitId
    }

    fun shouldUseThemeNativeFullscreenOnly(): Boolean {
        val config = remoteConfig ?: return DEFAULT_THEME_NATIVE_FULLSCREEN_ONLY
        return config.getBoolean(KEY_THEME_NATIVE_FULLSCREEN_ONLY)
    }

    fun getImExInterstitialAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_IMEX_INTERSTITIAL_AD_UNIT_ID
        val adUnitId = config.getString(KEY_IMEX_INTERSTITIAL_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_IMEX_INTERSTITIAL_AD_UNIT_ID else adUnitId
    }

    fun getImExNativeFullscreenAdUnitId(): String {
        val config = remoteConfig ?: return DEFAULT_IMEX_NATIVE_FULLSCREEN_AD_UNIT_ID
        val adUnitId = config.getString(KEY_IMEX_NATIVE_FULLSCREEN_AD_UNIT_ID).trim()
        return if (adUnitId.isBlank()) DEFAULT_IMEX_NATIVE_FULLSCREEN_AD_UNIT_ID else adUnitId
    }

    fun shouldUseImExNativeFullscreenOnly(): Boolean {
        val config = remoteConfig ?: return DEFAULT_IMEX_NATIVE_FULLSCREEN_ONLY
        return config.getBoolean(KEY_IMEX_NATIVE_FULLSCREEN_ONLY)
    }

    fun fetchRemoteConfig() {
        val config = remoteConfig ?: return
        config.fetch()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    config.activate()
                    Log.d(TAG, "Remote Config fetched and activated")
                } else {
                    Log.e(TAG, "Remote Config fetch failed", task.exception)
                }
            }
    }
}
