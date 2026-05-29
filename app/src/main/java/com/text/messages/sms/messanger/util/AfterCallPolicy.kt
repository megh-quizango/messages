package com.text.messages.sms.messanger.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Guards shared by both call receivers (mirrors reference app checks).
 */
object AfterCallPolicy {

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Skip after-call when this app is the default home launcher (avoids duplicate prompts). */
    fun isDefaultHomeLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun shouldProcessAfterCall(context: Context): Boolean {
        return hasPhoneStatePermission(context) && !isDefaultHomeLauncher(context)
    }
}
