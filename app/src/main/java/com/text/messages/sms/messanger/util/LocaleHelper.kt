package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.annotation.StringRes
import com.text.messages.sms.messanger.R
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "MessagesPrefs"
    private const val SELECTED_LANGUAGE = "SELECTED_LANGUAGE"
    private const val SYSTEM_DEFAULT_CODE = "system"

    data class SupportedLanguage(
        val code: String,
        val localeTag: String?,
        @StringRes val labelResId: Int? = null
    ) {
        fun toLocale(): Locale? = localeTag?.let(Locale::forLanguageTag)
    }

    private val supportedLanguages = listOf(
        SupportedLanguage(SYSTEM_DEFAULT_CODE, null, R.string.system_default),
        SupportedLanguage("en", "en"),
        SupportedLanguage("hi", "hi-IN"),
        SupportedLanguage("es", "es-ES"),
        SupportedLanguage("de", "de-DE"),
        SupportedLanguage("ru", "ru-RU"),
        SupportedLanguage("fr", "fr-FR"),
        SupportedLanguage("nl", "nl-NL"),
        SupportedLanguage("pt", "pt-PT"),
        SupportedLanguage("id", "id-ID"),
        SupportedLanguage("ar", "ar"),
        SupportedLanguage("it", "it-IT"),
        SupportedLanguage("tr", "tr-TR"),
        SupportedLanguage("sv", "sv-SE"),
        SupportedLanguage("pl", "pl-PL"),
        SupportedLanguage("th", "th-TH"),
        SupportedLanguage("vi", "vi-VN")
    )

    private val legacyLanguageCodes = mapOf(
        "System Default" to SYSTEM_DEFAULT_CODE,
        "English" to "en",
        "Hindi" to "hi",
        "Español" to "es",
        "Deutsch" to "de",
        "Russian" to "ru",
        "French" to "fr",
        "Dutch" to "nl",
        "Portuguese" to "pt",
        "Indonesian" to "id",
        "Arabic" to "ar",
        "Italian" to "it",
        "Turkish" to "tr",
        "Swedish" to "sv",
        "Polish" to "pl",
        "Thai" to "th",
        "Vietnamese" to "vi"
    )

    fun getSupportedLanguages(): List<SupportedLanguage> = supportedLanguages

    /**
     * Get the saved language preference
     */
    fun getSavedLanguageCode(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedValue = prefs.getString(SELECTED_LANGUAGE, SYSTEM_DEFAULT_CODE) ?: SYSTEM_DEFAULT_CODE
        val normalizedValue = normalizeLanguageCode(savedValue)

        if (savedValue != normalizedValue) {
            prefs.edit().putString(SELECTED_LANGUAGE, normalizedValue).apply()
        }

        return normalizedValue
    }

    /**
     * Save the selected language preference
     */
    fun setLanguage(context: Context, languageCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE, normalizeLanguageCode(languageCode)).apply()
    }

    /**
     * Get the locale based on the saved language preference
     */
    fun getLocale(languageCode: String): Locale {
        val normalizedCode = normalizeLanguageCode(languageCode)
        return if (normalizedCode == SYSTEM_DEFAULT_CODE) {
            getSystemLocale()
        } else {
            supportedLanguages.firstOrNull { it.code == normalizedCode }?.toLocale() ?: getSystemLocale()
        }
    }

    fun getDisplayName(context: Context, languageCode: String): String {
        val normalizedCode = normalizeLanguageCode(languageCode)
        val language = supportedLanguages.firstOrNull { it.code == normalizedCode }
            ?: supportedLanguages.first()

        language.labelResId?.let(context::getString)?.let { return it }

        val locale = language.toLocale() ?: return context.getString(R.string.system_default)
        val name = locale.getDisplayLanguage(locale)
        return name.replaceFirstChar { firstChar ->
            if (firstChar.isLowerCase()) {
                firstChar.titlecase(locale)
            } else {
                firstChar.toString()
            }
        }
    }

    fun getLocalizedContext(context: Context): Context {
        return setLocale(context, getSavedLanguageCode(context))
    }

    fun getLocalizedString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        val localizedContext = getLocalizedContext(context)
        return if (formatArgs.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *formatArgs)
        }
    }

    /**
     * Apply the locale to the context
     */
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = getLocale(languageCode)
        Locale.setDefault(locale)

        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            return context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            @Suppress("DEPRECATION")
            configuration.setLayoutDirection(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
            return context
        }
    }

    /**
     * Get the context with the saved locale applied
     */
    fun onAttach(context: Context): Context {
        val languageCode = getSavedLanguageCode(context)
        return setLocale(context, languageCode)
    }

    /**
     * Update the app locale and return the updated context
     */
    fun updateLocale(context: Context, languageCode: String): Context {
        val normalizedCode = normalizeLanguageCode(languageCode)
        setLanguage(context, normalizedCode)
        return setLocale(context, normalizedCode)
    }

    private fun normalizeLanguageCode(value: String): String {
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            return SYSTEM_DEFAULT_CODE
        }

        if (supportedLanguages.any { it.code == trimmedValue }) {
            return trimmedValue
        }

        return legacyLanguageCodes[trimmedValue] ?: SYSTEM_DEFAULT_CODE
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }
}

