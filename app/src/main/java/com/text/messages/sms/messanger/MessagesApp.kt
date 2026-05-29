package com.text.messages.sms.messanger

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import android.content.Context
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.text.messages.sms.messanger.data.database.AppDatabase
import com.text.messages.sms.messanger.BuildConfig
import com.text.messages.sms.messanger.util.AdConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.AppForegroundActivityTracker
import com.text.messages.sms.messanger.util.AppOpenManager
import com.text.messages.sms.messanger.util.LocaleHelper
import com.text.messages.sms.messanger.util.OnboardingInstallGuard
import com.text.messages.sms.messanger.util.RemoteConfigHelper
import java.util.concurrent.Executors

class MessagesApp : Application(), DefaultLifecycleObserver {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    companion object {
        lateinit var instance: MessagesApp
            private set

        /** Thread-safe lazy database initialization - constructed on first access */
        val database: AppDatabase by lazy {
            AppDatabase.getDatabase(instance)
        }
    }

    lateinit var appOpenManager: AppOpenManager

    // Flag to track when MainActivity is ready
    var isMainReady = false

    override fun onCreate() {
        super<Application>.onCreate()
        instance = this
        Log.d("MessagesApp", "onCreate called")

        OnboardingInstallGuard.resetRestoredOnboardingStateIfNeeded(this)

        // IMMEDIATE: Only lightweight, non-blocking operations
        // 1. Activity lifecycle tracker (no I/O, just object registration)
        registerActivityLifecycleCallbacks(AppForegroundActivityTracker)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // 2. Notification channels (lightweight, required for SMS receiver)
        com.text.messages.sms.messanger.util.NotificationHelper.initialize(this)
        com.text.messages.sms.messanger.util.AfterCallNotificationHelper.initialize(this)

        // DEFERRED: All heavy SDK initialization after first frame
        Handler(Looper.getMainLooper()).post {
            initializeSdksDeferred()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        Log.d("MessagesApp", "=== onStart called ===")
        Log.d("MessagesApp", "isMainReady: $isMainReady")
    }

    /**
     * Runs after the first frame is rendered.
     * Firebase, Facebook, AdMob, and AppOpenManager are initialized here
     * to avoid blocking cold-start performance.
     */
    private fun initializeSdksDeferred() {
        val bgExecutor = Executors.newSingleThreadExecutor()

        // Firebase init on background thread, then enable collection on main
        bgExecutor.execute {
            try {
                // Manually initialize Firebase since auto-init provider is removed
                FirebaseApp.initializeApp(this@MessagesApp)

                // Switch to main thread for Firebase API calls
                Handler(Looper.getMainLooper()).post {
                    try {
                        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
                        RemoteConfigHelper.initialize(Firebase.remoteConfig)
                        AnalyticsHelper.initialize(FirebaseAnalytics.getInstance(this@MessagesApp))
                        AnalyticsHelper.logEvent("app_start")
                        Log.d("MessagesApp", "Firebase SDKs initialized (deferred)")
                    } catch (e: Exception) {
                        Log.e("MessagesApp", "Firebase post-init failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MessagesApp", "Firebase init failed", e)
            }
        }

        // AdMob init (callback-based, safe on main thread after first frame)
        MobileAds.initialize(this) {
            Log.d("MessagesApp", "AdMob SDK initialized (deferred) appId=${BuildConfig.ADMOB_APP_ID}")
            AdConfig.logResolvedIds(this@MessagesApp)
        }

        // AppOpenManager registration (after first frame)
        appOpenManager = AppOpenManager(this)
        registerActivityLifecycleCallbacks(appOpenManager)

        // Facebook SDK on background thread
        bgExecutor.execute {
            try {
                @Suppress("DEPRECATION")
                FacebookSdk.sdkInitialize(applicationContext)
                Handler(Looper.getMainLooper()).post {
                    try {
                        AppEventsLogger.activateApp(this@MessagesApp)
                        Log.d("MessagesApp", "Facebook SDK initialized (deferred)")
                    } catch (e: Exception) {
                        Log.e("MessagesApp", "Facebook activateApp failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MessagesApp", "Facebook init failed", e)
            }
        }

        // Room database is now lazy-initialized via companion object
        // No explicit init needed - first access triggers construction
    }
}
