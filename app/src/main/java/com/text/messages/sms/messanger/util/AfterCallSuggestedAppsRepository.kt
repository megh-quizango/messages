package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.Html
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object AfterCallSuggestedAppsRepository {
    private const val TAG = "AfterCallSuggestedApps"
    private const val PREFS = "after_call_suggested_apps"
    private const val KEY_CONFIG = "config_value"
    private const val KEY_APPS = "apps_json"
    private const val CACHE_DIR = "after_call_suggested_apps"

    data class SuggestedApp(
        val packageId: String,
        val name: String,
        val iconFile: File?,
        val isInstalled: Boolean
    )

    suspend fun getSuggestedApps(context: Context): List<SuggestedApp> {
        val appContext = context.applicationContext
        val configValue = RemoteConfigHelper.getAfterCallSuggestedAppPackageIdsRaw()
        readCache(appContext, configValue)?.let { return it }

        val packageIds = parsePackageIds(configValue)
        if (packageIds.isEmpty()) return emptyList()

        val resolved = packageIds
            .take(2)
            .mapNotNull { packageId -> resolvePackage(appContext, packageId) }

        writeCache(appContext, configValue, resolved)
        return resolved
    }

    fun createLaunchIntent(context: Context, packageId: String): Intent {
        return context.packageManager.getLaunchIntentForPackage(packageId)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageId"))
    }

    private fun parsePackageIds(value: String): List<String> {
        return value
            .split(',', '\n', '\r', '\t', ' ')
            .map { it.trim() }
            .filter { it.matches(Regex("[A-Za-z0-9_.]+")) }
            .distinct()
    }

    private fun resolvePackage(context: Context, packageId: String): SuggestedApp? {
        resolveInstalledApp(context, packageId)?.let { return it }
        return resolvePlayStoreApp(context, packageId)
    }

    private fun resolveInstalledApp(context: Context, packageId: String): SuggestedApp? {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageId, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageId, 0)
            }
            val name = packageManager.getApplicationLabel(applicationInfo).toString()
            val icon = packageManager.getApplicationIcon(applicationInfo)
            val iconFile = saveIcon(context, packageId, icon)
            SuggestedApp(packageId, name, iconFile, true)
        } catch (e: Exception) {
            null
        }
    }

    private fun resolvePlayStoreApp(context: Context, packageId: String): SuggestedApp? {
        return try {
            val detailsUrl = "https://play.google.com/store/apps/details?id=$packageId&hl=en&gl=US"
            val html = (URL(detailsUrl).openConnection() as HttpURLConnection).run {
                connectTimeout = 7_000
                readTimeout = 7_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                inputStream.bufferedReader().use { it.readText() }
            }
            val name = extractMetaContent(html, "og:title")
                ?.substringBefore(" - Apps on Google Play")
                ?.decodeHtml()
                ?.takeIf { it.isNotBlank() }
                ?: packageId.substringAfterLast('.')
            val iconUrl = extractMetaContent(html, "og:image")?.decodeHtml()
            val iconFile = iconUrl?.let { downloadIcon(context, packageId, it) }
            SuggestedApp(packageId, name, iconFile, false)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to fetch Play Store metadata for $packageId", e)
            SuggestedApp(packageId, packageId.substringAfterLast('.'), null, false)
        }
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Regex(
            "<meta[^>]+property=[\"']$property[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(html)?.groupValues?.getOrNull(1)
    }

    private fun String.decodeHtml(): String {
        return Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun saveIcon(context: Context, packageId: String, drawable: Drawable): File? {
        val bitmap = drawable.toBitmap() ?: return null
        return saveBitmap(context, packageId, bitmap)
    }

    private fun downloadIcon(context: Context, packageId: String, iconUrl: String): File? {
        return try {
            val bitmap = (URL(iconUrl).openConnection() as HttpURLConnection).run {
                connectTimeout = 7_000
                readTimeout = 7_000
                requestMethod = "GET"
                inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            } ?: return null
            saveBitmap(context, packageId, bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to download icon for $packageId", e)
            null
        }
    }

    private fun saveBitmap(context: Context, packageId: String, bitmap: Bitmap): File? {
        return try {
            val dir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
            val file = File(dir, "${packageId.sha1()}.png")
            file.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            file
        } catch (e: Exception) {
            Log.w(TAG, "Unable to cache icon for $packageId", e)
            null
        }
    }

    private fun Drawable.toBitmap(): Bitmap? {
        if (this is BitmapDrawable) return bitmap
        val width = intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = intrinsicHeight.takeIf { it > 0 } ?: 96
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                setBounds(0, 0, canvas.width, canvas.height)
                draw(canvas)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readCache(context: Context, configValue: String): List<SuggestedApp>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_CONFIG, null) != configValue) return null
        val appsJson = prefs.getString(KEY_APPS, null).orEmpty()
        if (appsJson.isBlank()) return null
        return try {
            val array = JSONArray(appsJson)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val iconPath = item.optString("iconPath").takeIf { it.isNotBlank() }
                SuggestedApp(
                    packageId = item.getString("packageId"),
                    name = item.getString("name"),
                    iconFile = iconPath?.let(::File)?.takeIf { it.exists() },
                    isInstalled = item.optBoolean("isInstalled")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeCache(context: Context, configValue: String, apps: List<SuggestedApp>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(
                JSONObject()
                    .put("packageId", app.packageId)
                    .put("name", app.name)
                    .put("iconPath", app.iconFile?.absolutePath.orEmpty())
                    .put("isInstalled", app.isInstalled)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG, configValue)
            .putString(KEY_APPS, array.toString())
            .apply()
    }

    private fun String.sha1(): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
