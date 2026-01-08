package com.text.messages.sms.messanger.ui.base

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.text.messages.sms.messanger.util.LocaleHelper

/**
 * Base activity that ensures locale is applied to all activities
 */
open class BaseActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }
}

