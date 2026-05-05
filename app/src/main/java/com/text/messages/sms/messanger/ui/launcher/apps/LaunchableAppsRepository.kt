package com.text.messages.sms.messanger.ui.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.text.messages.sms.messanger.ui.launcher.model.LaunchableApp
import java.util.Locale

object LaunchableAppsRepository {

    private const val PREFS = "LauncherAppsPrefs"
    private const val KEY_JSON = "apps_json"

    private val gson = Gson()

    @Volatile
    private var memoryCache: List<LaunchableApp>? = null

    fun getCachedApps(context: Context): List<LaunchableApp> {
        memoryCache?.let { return it }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<LaunchableApp>>() {}.type
            gson.fromJson<List<LaunchableApp>>(json, type)
        }.getOrElse { emptyList() }.also { memoryCache = it }
    }

    fun refreshApps(context: Context): List<LaunchableApp> {
        val apps = queryLaunchableApps(context)
        memoryCache = apps
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_JSON, gson.toJson(apps)).apply()
        return apps
    }

    private fun queryLaunchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val selfPackage = context.packageName
        val list = ArrayList<LaunchableApp>(resolved.size)
        for (ri in resolved) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName ?: continue
            if (pkg == selfPackage) continue // Self-hide
            val label = runCatching { ri.loadLabel(pm)?.toString() }.getOrNull().orEmpty()
            list.add(
                LaunchableApp(
                    label = if (label.isBlank()) pkg else label,
                    packageName = pkg,
                    className = ai.name ?: ""
                )
            )
        }

        return list
            .distinctBy { it.packageName + "/" + it.className }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }
}
