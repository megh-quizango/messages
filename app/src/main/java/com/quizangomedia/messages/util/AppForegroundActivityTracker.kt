package com.quizangomedia.messages.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

object AppForegroundActivityTracker : Application.ActivityLifecycleCallbacks {
    
    private const val TAG = "AppForegroundTracker"
    var currentActivity: Activity? = null
        private set
    
    override fun onActivityStarted(activity: Activity) {
        Log.d(TAG, "=== onActivityStarted ===")
        Log.d(TAG, "Activity: ${activity.javaClass.simpleName}")
        Log.d(TAG, "Activity state - isFinishing: ${activity.isFinishing}, isDestroyed: ${activity.isDestroyed}")
        currentActivity = activity
        Log.d(TAG, "currentActivity set to: ${currentActivity?.javaClass?.simpleName}")
    }
    
    override fun onActivityStopped(activity: Activity) {
        Log.d(TAG, "=== onActivityStopped ===")
        Log.d(TAG, "Activity: ${activity.javaClass.simpleName}")
        if (currentActivity === activity) {
            Log.d(TAG, "Current activity stopped, clearing reference")
            currentActivity = null
        }
    }
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d(TAG, "onActivityCreated: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityResumed(activity: Activity) {
        Log.d(TAG, "onActivityResumed: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityPaused(activity: Activity) {
        Log.d(TAG, "onActivityPaused: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d(TAG, "onActivitySaveInstanceState: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "=== onActivityDestroyed ===")
        Log.d(TAG, "Activity: ${activity.javaClass.simpleName}")
        if (currentActivity === activity) {
            Log.d(TAG, "Current activity destroyed, clearing reference")
            currentActivity = null
        }
    }
}

