package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "MessagesPrefs"
    
    // Theme preferences
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_THEME_COLOR_LIGHT = "theme_color_light"
    private const val KEY_THEME_WALLPAPER = "theme_wallpaper"
    private const val DEFAULT_THEME_COLOR = "#0C56CF"
    private const val DEFAULT_THEME_COLOR_LIGHT = "#E6F0FF"
    private const val KEY_CALLER_THEME_INDEX = "caller_theme_index"
    private const val DEFAULT_CALLER_THEME_INDEX = 3
    
    // Bubble preferences
    private const val KEY_BUBBLE_COLOR = "bubble_color"
    private const val DEFAULT_BUBBLE_COLOR = "#0C56CF"
    
    // Font preferences
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_FONT_FAMILY = "font_family"
    private const val DEFAULT_FONT_SIZE = 16f
    private const val DEFAULT_FONT_FAMILY = 0 // Typeface.DEFAULT
    
    // SIM card icons preference
    private const val KEY_COLOR_SIM_CARD_ICONS = "color_sim_card_icons"
    private const val DEFAULT_COLOR_SIM_CARD_ICONS = false
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Theme methods
    fun getThemeColor(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR
    }
    
    fun setThemeColor(context: Context, color: String) {
        // Use commit() instead of apply() for immediate persistence
        getPrefs(context).edit().putString(KEY_THEME_COLOR, color).commit()
    }
    
    fun getThemeColorLight(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_COLOR_LIGHT, DEFAULT_THEME_COLOR_LIGHT) ?: DEFAULT_THEME_COLOR_LIGHT
    }
    
    fun setThemeColorLight(context: Context, color: String) {
        // Use commit() instead of apply() for immediate persistence
        getPrefs(context).edit().putString(KEY_THEME_COLOR_LIGHT, color).commit()
    }

    fun getThemeWallpaper(context: Context): String? {
        return getPrefs(context).getString(KEY_THEME_WALLPAPER, null)
    }

    fun setThemeWallpaper(context: Context, wallpaperName: String?) {
        val editor = getPrefs(context).edit()
        if (wallpaperName.isNullOrBlank()) {
            editor.remove(KEY_THEME_WALLPAPER)
        } else {
            editor.putString(KEY_THEME_WALLPAPER, wallpaperName)
        }
        editor.commit()
    }

    data class CallerTheme(
        val index: Int,
        val topColor: String,
        val bottomColor: String,
        val buttonColor: String
    )

    val callerThemes: List<CallerTheme> = listOf(
        CallerTheme(0, "#1690FF", "#1660C8", "#0CB7FF"),
        CallerTheme(1, "#FF7378", "#DB0008", "#FF5A61"),
        CallerTheme(2, "#FFA750", "#D86C00", "#FF9D3A"),
        CallerTheme(3, "#479DA2", "#034D57", "#469CA1"),
        CallerTheme(4, "#6643E8", "#213359", "#7E66E0"),
        CallerTheme(5, "#E45A42", "#671629", "#D84A38"),
        CallerTheme(6, "#A92C80", "#550F3E", "#A2297B"),
        CallerTheme(7, "#3785C0", "#12476F", "#3582BC")
    )

    fun getCallerThemeIndex(context: Context): Int {
        return getPrefs(context)
            .getInt(KEY_CALLER_THEME_INDEX, DEFAULT_CALLER_THEME_INDEX)
            .coerceIn(callerThemes.indices)
    }

    fun setCallerThemeIndex(context: Context, index: Int) {
        getPrefs(context).edit()
            .putInt(KEY_CALLER_THEME_INDEX, index.coerceIn(callerThemes.indices))
            .commit()
    }

    fun getCallerTheme(context: Context): CallerTheme {
        return callerThemes[getCallerThemeIndex(context)]
    }
    
    // Bubble methods
    fun getBubbleColor(context: Context): String {
        return getPrefs(context).getString(KEY_BUBBLE_COLOR, DEFAULT_BUBBLE_COLOR) ?: DEFAULT_BUBBLE_COLOR
    }
    
    fun setBubbleColor(context: Context, color: String) {
        getPrefs(context).edit().putString(KEY_BUBBLE_COLOR, color).apply()
    }
    
    // Font methods
    fun getFontSize(context: Context): Float {
        return getPrefs(context).getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    }
    
    fun setFontSize(context: Context, size: Float) {
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE, size).apply()
    }
    
    fun getFontFamily(context: Context): Int {
        return getPrefs(context).getInt(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY)
    }
    
    fun setFontFamily(context: Context, family: Int) {
        getPrefs(context).edit().putInt(KEY_FONT_FAMILY, family).apply()
    }
    
    // SIM card icons methods
    fun getColorSimCardIcons(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_COLOR_SIM_CARD_ICONS, DEFAULT_COLOR_SIM_CARD_ICONS)
    }
    
    fun setColorSimCardIcons(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COLOR_SIM_CARD_ICONS, enabled).apply()
    }
    
    // Helper to convert color to much lighter version (for #E6F0FF equivalent)
    // This creates a very light tinted version suitable for container backgrounds
    fun getLighterColor(colorHex: String): String {
        val color = android.graphics.Color.parseColor(colorHex)
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        
        // Mix with white (92% white, 8% original) to create a much lighter version
        // This ensures containers and text fields have a very subtle tint
        val newR = (r * 0.08f + 255 * 0.92f).toInt().coerceIn(0, 255)
        val newG = (g * 0.08f + 255 * 0.92f).toInt().coerceIn(0, 255)
        val newB = (b * 0.08f + 255 * 0.92f).toInt().coerceIn(0, 255)
        
        return String.format("#%02X%02X%02X", newR, newG, newB)
    }
}

