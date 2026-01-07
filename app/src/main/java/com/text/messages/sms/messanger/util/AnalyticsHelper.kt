package com.text.messages.sms.messanger.util

import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object AnalyticsHelper {
    private const val TAG = "AnalyticsHelper"
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    
    fun initialize(analyticsInstance: FirebaseAnalytics) {
        firebaseAnalytics = analyticsInstance
        Log.d(TAG, "Analytics initialized")
    }
    
    // Screen tracking
    fun logScreenView(screenName: String, screenClass: String? = null) {
        try {
            val bundle = android.os.Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                screenClass?.let { putString(FirebaseAnalytics.Param.SCREEN_CLASS, it) }
            }
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "Screen view logged: $screenName")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging screen view", e)
        }
    }
    
    // User actions
    fun logEvent(eventName: String, parameters: Map<String, Any>? = null) {
        try {
            val bundle = parameters?.let { params ->
                android.os.Bundle().apply {
                    params.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Double -> putDouble(key, value)
                            is Boolean -> putBoolean(key, value)
                            else -> putString(key, value.toString())
                        }
                    }
                }
            } ?: android.os.Bundle()
            firebaseAnalytics.logEvent(eventName, bundle)
            Log.d(TAG, "Event logged: $eventName")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging event: $eventName", e)
        }
    }
    
    // Ad events
    fun logAdImpression(adType: String, adUnitId: String) {
        logEvent("ad_impression", mapOf(
            "ad_type" to adType,
            "ad_unit_id" to adUnitId
        ))
    }
    
    fun logAdClick(adType: String, adUnitId: String) {
        logEvent("ad_click", mapOf(
            "ad_type" to adType,
            "ad_unit_id" to adUnitId
        ))
    }
    
    fun logAdLoad(adType: String, adUnitId: String, success: Boolean) {
        logEvent("ad_load", mapOf(
            "ad_type" to adType,
            "ad_unit_id" to adUnitId,
            "success" to success
        ))
    }
    
    fun logAdError(adType: String, adUnitId: String, errorCode: String) {
        logEvent("ad_error", mapOf(
            "ad_type" to adType,
            "ad_unit_id" to adUnitId,
            "error_code" to errorCode
        ))
    }
    
    // Message events
    fun logMessageSent(conversationId: String? = null) {
        logEvent("message_sent", conversationId?.let { mapOf("conversation_id" to it) })
    }
    
    fun logMessageReceived(conversationId: String? = null) {
        logEvent("message_received", conversationId?.let { mapOf("conversation_id" to it) })
    }
    
    fun logConversationOpened(conversationId: String? = null) {
        logEvent("conversation_opened", conversationId?.let { mapOf("conversation_id" to it) })
    }
    
    fun logConversationDeleted(conversationId: String? = null) {
        logEvent("conversation_deleted", conversationId?.let { mapOf("conversation_id" to it) })
    }
    
    // Settings events
    fun logSettingChanged(settingName: String, value: Any) {
        logEvent("setting_changed", mapOf(
            "setting_name" to settingName,
            "setting_value" to value.toString()
        ))
    }
    
    // Navigation events
    fun logNavigation(destination: String, source: String? = null) {
        logEvent("navigation", mapOf(
            "destination" to destination,
            "source" to (source ?: "unknown")
        ))
    }
    
    // Feature usage
    fun logFeatureUsed(featureName: String, additionalParams: Map<String, Any>? = null) {
        val params = mutableMapOf<String, Any>("feature_name" to featureName)
        additionalParams?.let { params.putAll(it) }
        logEvent("feature_used", params)
    }
    
    // User properties
    fun setUserProperty(name: String, value: String) {
        try {
            firebaseAnalytics.setUserProperty(name, value)
            Log.d(TAG, "User property set: $name = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user property", e)
        }
    }
}

