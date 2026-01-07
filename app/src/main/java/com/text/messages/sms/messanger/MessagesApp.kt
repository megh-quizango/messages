package com.text.messages.sms.messanger

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.text.messages.sms.messanger.data.model.Contact
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.data.model.Message
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.AppForegroundActivityTracker
import com.text.messages.sms.messanger.util.AppOpenAdManager
import com.text.messages.sms.messanger.util.RemoteConfigHelper
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

class MessagesApp : Application(), DefaultLifecycleObserver {
    
    companion object {
        lateinit var realm: Realm
            private set
        lateinit var instance: MessagesApp
            private set
    }
    
    lateinit var appOpenAdManager: AppOpenAdManager
    
    // Flag to track when MainActivity is ready (CRITICAL for App Open Ad dismiss button)
    var isMainReady = false
    
    fun isAppOpenAdManagerInitialized(): Boolean {
        return ::appOpenAdManager.isInitialized
    }
    
    override fun onCreate() {
        super<Application>.onCreate()
        instance = this
        
        android.util.Log.d("MessagesApp", "onCreate called")
        
        // Initialize Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        
        // Initialize Firebase Remote Config
        RemoteConfigHelper.initialize(Firebase.remoteConfig)
        
        // Initialize Firebase Analytics
        AnalyticsHelper.initialize(FirebaseAnalytics.getInstance(this))
        
        // Log app start event
        AnalyticsHelper.logEvent("app_start")
        
        // Register activity lifecycle tracker
        registerActivityLifecycleCallbacks(AppForegroundActivityTracker)
        
        // Register ProcessLifecycleOwner observer for app-level lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Initialize AdMob SDK
        MobileAds.initialize(this) {
            android.util.Log.d("MessagesApp", "AdMob SDK initialized")
            // Initialize App Open Ad Manager after SDK is ready
            appOpenAdManager = AppOpenAdManager(this)
            val appOpenAdUnitId = RemoteConfigHelper.getAppOpenAdUnitId()
            appOpenAdManager.loadAd(appOpenAdUnitId)
        }
        
        initRealmSafely()
        com.text.messages.sms.messanger.util.NotificationHelper.initialize(this)
        com.text.messages.sms.messanger.util.AfterCallNotificationHelper.initialize(this)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        android.util.Log.d("MessagesApp", "=== onStart called ===")
        android.util.Log.d("MessagesApp", "isMainReady: $isMainReady")
        android.util.Log.d("MessagesApp", "manager initialized: ${::appOpenAdManager.isInitialized}")
        
        val app = this
        
        // CRITICAL: Only show App Open Ad when MainActivity is ready
        // Never show on LandingActivity, permission screens, or setup flows
        if (!app.isMainReady) {
            android.util.Log.d("MessagesApp", "MainActivity not ready yet, skipping App Open Ad")
            android.util.Log.d("MessagesApp", "App Open Ads must only show on stable MainActivity")
            return
        }
        
//        // Only show ad if manager is initialized and we're on MainActivity
//        if (::appOpenAdManager.isInitialized) {
//            val activity = AppForegroundActivityTracker.currentActivity
//            if (activity is com.text.messages.sms.messanger.ui.main.MainActivity) {
//                android.util.Log.d("MessagesApp", "MainActivity ready and active - showing App Open Ad")
//                appOpenAdManager.loadAndShowAppOpenAd(activity)
//            } else {
//                android.util.Log.d("MessagesApp", "Current activity is not MainActivity: ${activity?.javaClass?.simpleName}")
//            }
//        } else {
//            android.util.Log.d("MessagesApp", "Ad manager not initialized yet")
//        }
    }
    
    private fun initRealmSafely() {
        try {
            val config = RealmConfiguration.Builder(
                schema = setOf(
                    Message::class,
                    Conversation::class,
                    Contact::class
                )
            )
                .schemaVersion(15)
                .build()
            
            realm = Realm.open(config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

