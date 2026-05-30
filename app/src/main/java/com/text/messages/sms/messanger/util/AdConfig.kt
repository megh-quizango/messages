package com.text.messages.sms.messanger.util

import android.content.Context
import android.util.Log
import com.text.messages.sms.messanger.BuildConfig
import com.text.messages.sms.messanger.R

/**
 * Resolves ad unit IDs: Remote Config (when set) → local [admob_config.xml] defaults.
 * In debug with [BuildConfig.USE_TEST_ADS], uses Google sample test units.
 */
object AdConfig {

    private const val TAG = "AdConfig"

    fun resolveBannerAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getBannerAdUnitId(),
            productionResId = R.string.admob_banner_messages,
            testResId = R.string.admob_test_banner,
            label = "banner"
        )
    }

    fun resolveNativeAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getNativeAdUnitId(),
            productionResId = R.string.admob_native_messages,
            testResId = R.string.admob_test_native,
            label = "native"
        )
    }

    fun resolveAfterCallNativeAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getAfterCallNativeAdUnitId(),
            productionResId = R.string.admob_native_after_call,
            testResId = R.string.admob_test_native,
            label = "after_call_native"
        )
    }

    fun resolveAfterCallAdaptiveBannerAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getAfterCallAdaptiveBannerAdUnitId(),
            productionResId = R.string.admob_banner_after_call_fallback,
            testResId = R.string.admob_test_banner,
            label = "after_call_adaptive_banner"
        )
    }

    fun resolveExitNativeBannerAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getExitNativeBannerAdUnitId(),
            productionResId = R.string.admob_native_banner_exit,
            testResId = R.string.admob_test_native,
            label = "exit_native_banner"
        )
    }

    fun resolveExitAdaptiveBannerAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getExitFallbackBannerAdUnitId(),
            productionResId = R.string.admob_banner_exit_fallback,
            testResId = R.string.admob_test_banner,
            label = "exit_adaptive_banner"
        )
    }

    fun resolveManageAppsAdaptiveBannerAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getManageAppsAdaptiveBannerAdUnitId(),
            productionResId = R.string.admob_banner_manage_apps_fallback,
            testResId = R.string.admob_test_banner,
            label = "manage_apps_adaptive_banner"
        )
    }

    fun resolveAppOpenAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getAppOpenAdUnitId(),
            productionResId = R.string.admob_app_open_messages,
            testResId = R.string.admob_test_banner,
            label = "app_open"
        )
    }

    fun resolveAppOpenResumeAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getAppOpenResumeAdUnitId(),
            productionResId = R.string.admob_app_open_resume_messages,
            testResId = R.string.admob_test_banner,
            label = "app_open_resume"
        )
    }

    fun resolveThemeInterstitialAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getThemeInterstitialAdUnitId(),
            productionResId = R.string.admob_interstitial_theme,
            testResId = R.string.admob_test_interstitial,
            label = "theme_interstitial"
        )
    }

    fun resolveThemeNativeFullscreenAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getThemeNativeFullscreenAdUnitId(),
            productionResId = R.string.admob_native_fullscreen_theme,
            testResId = R.string.admob_test_native,
            label = "theme_native_fullscreen"
        )
    }

    fun resolveImExInterstitialAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getImExInterstitialAdUnitId(),
            productionResId = R.string.admob_interstitial_imex,
            testResId = R.string.admob_test_interstitial,
            label = "imex_interstitial"
        )
    }

    fun resolveImExNativeFullscreenAdUnitId(context: Context): String {
        return resolve(
            context = context,
            remoteValue = RemoteConfigHelper.getImExNativeFullscreenAdUnitId(),
            productionResId = R.string.admob_native_fullscreen_imex,
            testResId = R.string.admob_test_native,
            label = "imex_native_fullscreen"
        )
    }

    fun logResolvedIds(context: Context) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "Ads debug — banner=${resolveBannerAdUnitId(context)} " +
                "native=${resolveNativeAdUnitId(context)} " +
                "afterCall=${resolveAfterCallNativeAdUnitId(context)} " +
                "afterCallAdaptiveBanner=${resolveAfterCallAdaptiveBannerAdUnitId(context)} " +
                "exitNativeBanner=${resolveExitNativeBannerAdUnitId(context)} " +
                "exitAdaptiveBanner=${resolveExitAdaptiveBannerAdUnitId(context)} " +
                "manageAppsAdaptiveBanner=${resolveManageAppsAdaptiveBannerAdUnitId(context)} " +
                "appOpenResume=${resolveAppOpenResumeAdUnitId(context)} " +
                "themeInterstitial=${resolveThemeInterstitialAdUnitId(context)} " +
                "themeNativeFullscreen=${resolveThemeNativeFullscreenAdUnitId(context)} " +
                "imexInterstitial=${resolveImExInterstitialAdUnitId(context)} " +
                "imexNativeFullscreen=${resolveImExNativeFullscreenAdUnitId(context)} " +
                "useTestAds=${BuildConfig.USE_TEST_ADS}"
        )
    }

    private fun resolve(
        context: Context,
        remoteValue: String,
        productionResId: Int,
        testResId: Int,
        label: String
    ): String {
        val fromRemote = remoteValue.trim()
        if (fromRemote.isNotBlank()) {
            logIfTestPublisherMismatch(fromRemote, label)
            return fromRemote
        }

        val local = if (BuildConfig.USE_TEST_ADS) {
            context.getString(testResId)
        } else {
            context.getString(productionResId)
        }
        logIfTestPublisherMismatch(local, label)
        return local
    }

    /** Production units under 9014156375881181 fail with error 3 if manifest still uses the sample app id. */
    private fun logIfTestPublisherMismatch(adUnitId: String, label: String) {
        if (!BuildConfig.DEBUG) return
        val isProductionUnit = adUnitId.contains("9014156375881181")
        val manifestUsesSampleApp = BuildConfig.ADMOB_APP_ID.contains("3940256099942544")
        if (isProductionUnit && manifestUsesSampleApp) {
            Log.e(
                TAG,
                "Ad unit '$label' uses production publisher but APPLICATION_ID is the " +
                    "Google sample app. Set ADMOB_APPLICATION_ID in gradle.properties to your " +
                    "AdMob App ID (AdMob > App settings). adUnit=$adUnitId"
            )
        }
    }
}
