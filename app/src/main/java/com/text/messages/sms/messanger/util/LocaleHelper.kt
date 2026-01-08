package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

object LocaleHelper {
    
    private const val PREFS_NAME = "MessagesPrefs"
    private const val SELECTED_LANGUAGE = "SELECTED_LANGUAGE"
    
    /**
     * Get the saved language preference
     */
    fun getSavedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_LANGUAGE, "System Default") ?: "System Default"
    }
    
    /**
     * Save the selected language preference
     */
    fun setLanguage(context: Context, language: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply()
    }
    
    /**
     * Get the locale based on the saved language preference
     */
    fun getLocale(language: String): Locale {
        return when (language) {
            "English" -> Locale.ENGLISH
            "Hindi" -> Locale("hi", "IN")
            "Español" -> Locale("es", "ES")
            "Deutsch" -> Locale("de", "DE")
            "System Default" -> Locale.getDefault()
            else -> Locale.getDefault()
        }
    }
    
    /**
     * Apply the locale to the context
     */
    fun setLocale(context: Context, language: String): Context {
        val locale = getLocale(language)
        Locale.setDefault(locale)
        
        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            return context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
            return context
        }
    }
    
    /**
     * Get the context with the saved locale applied
     */
    fun onAttach(context: Context): Context {
        val language = getSavedLanguage(context)
        return setLocale(context, language)
    }
    
    /**
     * Update the app locale and return the updated context
     */
    fun updateLocale(context: Context, language: String): Context {
        setLanguage(context, language)
        return setLocale(context, language)
    }
}

