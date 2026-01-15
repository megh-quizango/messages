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
import android.content.Context
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.text.messages.sms.messanger.data.database.AppDatabase
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.AppForegroundActivityTracker
import com.text.messages.sms.messanger.util.LocaleHelper
import com.text.messages.sms.messanger.util.RemoteConfigHelper

class MessagesApp : Application(), DefaultLifecycleObserver {
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }
    
    companion object {
        lateinit var database: AppDatabase
            private set
        lateinit var instance: MessagesApp
            private set
    }

    // Flag to track when MainActivity is ready
    var isMainReady = false
    
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
            // App Open Ads are now handled by AppOpenAdManager singleton
            // Called from LandingActivity after splash
        }

        // Initialize Facebook SDK
        FacebookSdk.sdkInitialize(applicationContext)

        // Activate App Events for install tracking and analytics
        AppEventsLogger.activateApp(this)
        
        initDatabaseSafely()
        com.text.messages.sms.messanger.util.NotificationHelper.initialize(this)
        com.text.messages.sms.messanger.util.AfterCallNotificationHelper.initialize(this)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        android.util.Log.d("MessagesApp", "=== onStart called ===")
        android.util.Log.d("MessagesApp", "isMainReady: $isMainReady")
        // App Open Ads are handled by AppOpenAdManager singleton from LandingActivity
    }
    
    private fun initDatabaseSafely() {
        try {
            database = AppDatabase.getDatabase(this)
            android.util.Log.d("MessagesApp", "Room database initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("MessagesApp", "Error initializing Room database", e)
            e.printStackTrace()
        }
    }
}

