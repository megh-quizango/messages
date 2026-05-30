package com.text.messages.sms.messanger.ui.personalize

import android.app.Activity
import android.content.Intent

object PersonalizationSaveAdNavigator {

    fun showAdThenFinish(activity: Activity) {
        activity.startActivity(
            Intent(activity, ThemeTransitionAdActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
        activity.finish()
    }
}
