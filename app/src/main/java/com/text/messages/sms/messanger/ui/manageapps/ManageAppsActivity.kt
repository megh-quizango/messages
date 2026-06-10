package com.text.messages.sms.messanger.ui.manageapps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.AnalyticsHelper

class ManageAppsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("ManageAppsActivity", "ManageAppsActivity")

        startActivity(
            Intent(this, ManageAppsDetailActivity::class.java).apply {
                putExtra("ram_used_percentage", getRamUsedPercentage())
            }
        )
        finish()
        overridePendingTransition(0, 0)
    }

    private fun getRamUsedPercentage(): Int {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val usedRam = memInfo.totalMem - memInfo.availMem
            ((usedRam.toFloat() / memInfo.totalMem.toFloat()) * 100).toInt()
        } catch (e: Exception) {
            0
        }
    }
}
