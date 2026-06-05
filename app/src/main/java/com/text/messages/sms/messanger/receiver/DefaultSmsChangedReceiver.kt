package com.text.messages.sms.messanger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.text.messages.sms.messanger.util.ConversationCache
import com.text.messages.sms.messanger.util.DefaultSmsHelper

class DefaultSmsChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEFAULT_SMS_PACKAGE_CHANGED) return

        val isDefaultSms = DefaultSmsHelper.isDefaultSmsApp(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_DEFAULT_SMS_SET, isDefaultSms)
            .apply()

        ConversationCache.clear()
        Log.d(TAG, "Default SMS package changed. isDefaultSms=$isDefaultSms")
    }

    companion object {
        private const val TAG = "DefaultSmsChangedReceiver"
        private const val PREFS_NAME = "MessagesPrefs"
        private const val KEY_IS_DEFAULT_SMS_SET = "IS_DEFAULT_SMS_SET"
        private const val ACTION_DEFAULT_SMS_PACKAGE_CHANGED =
            "android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED"
    }
}

