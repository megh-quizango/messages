package com.text.messages.sms.messanger.util

import android.content.Context
import java.io.File

object OnboardingInstallGuard {

    private const val PREFS_NAME = "MessagesPrefs"
    private const val KEY_HAS_SEEN_WELCOME = "HAS_SEEN_WELCOME"
    private const val KEY_IS_LANGUAGE_SET = "IS_LANGUAGE_SET"
    private const val KEY_IS_DEFAULT_SMS_SET = "IS_DEFAULT_SMS_SET"
    private const val MARKER_FILE_NAME = "install_marker_v1"
    private const val FRESH_INSTALL_WINDOW_MS = 60_000L

    fun resetRestoredOnboardingStateIfNeeded(context: Context) {
        val markerFile = File(context.noBackupFilesDir, MARKER_FILE_NAME)
        if (markerFile.exists()) {
            return
        }

        if (isFreshInstall(context)) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_HAS_SEEN_WELCOME) || prefs.contains(KEY_IS_LANGUAGE_SET) || prefs.contains(KEY_IS_DEFAULT_SMS_SET)) {
                prefs.edit()
                    .remove(KEY_HAS_SEEN_WELCOME)
                    .remove(KEY_IS_LANGUAGE_SET)
                    .remove(KEY_IS_DEFAULT_SMS_SET)
                    .apply()
            }
        }

        markerFile.parentFile?.mkdirs()
        markerFile.writeText("initialized")
    }

    private fun isFreshInstall(context: Context): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return (packageInfo.lastUpdateTime - packageInfo.firstInstallTime) <= FRESH_INSTALL_WINDOW_MS
    }
}
