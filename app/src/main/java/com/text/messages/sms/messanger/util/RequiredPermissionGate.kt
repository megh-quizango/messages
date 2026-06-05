package com.text.messages.sms.messanger.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.text.messages.sms.messanger.MessagesApp
import com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity
import com.text.messages.sms.messanger.ui.overlaypermission.OverlayPermissionActivity

object RequiredPermissionGate : Application.ActivityLifecycleCallbacks {

    private const val TAG = "RequiredPermissionGate"
    private const val PREFS_NAME = "MessagesPrefs"
    private const val KEY_HAS_SEEN_WELCOME = "HAS_SEEN_WELCOME"
    private const val KEY_IS_LANGUAGE_SET = "IS_LANGUAGE_SET"
    private const val KEY_IS_DEFAULT_SMS_SET = "IS_DEFAULT_SMS_SET"
    private const val KEY_IS_OVERLAY_PERMISSION_SET = "IS_OVERLAY_PERMISSION_SET"
    private const val PROMPT_DEBOUNCE_MS = 1_500L

    private val skippedScreens = setOf(
        "LandingActivity",
        "ResumeActivity",
        "WelcomeActivity",
        "LanguageActivity",
        "LanguageTransitionAdActivity",
        "LanguageNativeFullscreenAdActivity",
        "DefaultSmsActivity",
        "OverlayPermissionActivity",
        "OverlayPermissionGuideActivity",
        "CallAfterActivity",
        "ThemeTransitionAdActivity",
        "ThemeNativeFullscreenAdActivity",
        "ImExTransitionAdActivity",
        "ImExNativeFullscreenAdActivity"
    )

    private var lastPromptElapsedMs = 0L

    override fun onActivityResumed(activity: Activity) {
        if (shouldSkip(activity)) return

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_SEEN_WELCOME, false)) return
        if (!prefs.getBoolean(KEY_IS_LANGUAGE_SET, false)) return

        val isDefaultSms = DefaultSmsHelper.isDefaultSmsApp(activity)
        prefs.edit().putBoolean(KEY_IS_DEFAULT_SMS_SET, isDefaultSms).apply()

        if (!isDefaultSms) {
            ConversationCache.clear()
            (activity.application as? MessagesApp)?.isMainReady = false
            Log.d(TAG, "Default SMS missing on ${activity.javaClass.simpleName}")
            if (activity.javaClass.simpleName != "MainActivity") {
                launchDefaultSmsPrompt(activity)
            }
            return
        }

        val hasOverlayPermission = PermissionManager.hasOverlayPermission(activity)
        prefs.edit().putBoolean(KEY_IS_OVERLAY_PERMISSION_SET, hasOverlayPermission).apply()

        if (!hasOverlayPermission) {
            Log.d(TAG, "Overlay permission missing on ${activity.javaClass.simpleName}")
            launchOverlayPrompt(activity)
        }
    }

    private fun shouldSkip(activity: Activity): Boolean {
        return activity.isFinishing ||
            activity.isDestroyed ||
            activity.javaClass.simpleName in skippedScreens
    }

    private fun launchDefaultSmsPrompt(activity: Activity) {
        if (!canLaunchPrompt()) return
        AppOpenManager.suppressAppOpenFor(3_000L)
        activity.startActivity(
            Intent(activity, DefaultSmsActivity::class.java)
                .putExtra("from_settings", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun launchOverlayPrompt(activity: Activity) {
        if (!canLaunchPrompt()) return
        AppOpenManager.suppressAppOpenFor(3_000L)
        activity.startActivity(
            Intent(activity, OverlayPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun canLaunchPrompt(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPromptElapsedMs < PROMPT_DEBOUNCE_MS) return false
        lastPromptElapsedMs = now
        return true
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
