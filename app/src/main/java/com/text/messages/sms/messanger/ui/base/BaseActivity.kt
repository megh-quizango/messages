package com.text.messages.sms.messanger.ui.base

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.text.messages.sms.messanger.util.LocaleHelper

/**
 * Base activity that ensures locale is applied and handles orientation
 * for different screen sizes (portrait on phones, rotation on tablets/foldables).
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyOrientationForScreenSize()
        super.onCreate(savedInstanceState)
    }

    private fun applyOrientationForScreenSize() {
        val screenWidthDp = resources.configuration.smallestScreenWidthDp
        if (screenWidthDp < 600) {
            // Phone: lock to portrait
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Tablet (>=600dp) and foldable: allow system default rotation
    }
}
