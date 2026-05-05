package com.text.messages.sms.messanger.ui.launcher.model

import android.content.ComponentName

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val className: String
) {
    fun componentName(): ComponentName = ComponentName(packageName, className)
}

