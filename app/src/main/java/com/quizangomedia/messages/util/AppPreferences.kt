package com.quizangomedia.messages.util

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "MessagesPrefs"
    
    // Theme preferences
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_THEME_COLOR_LIGHT = "theme_color_light"
    private const val DEFAULT_THEME_COLOR = "#0C56CF"
    private const val DEFAULT_THEME_COLOR_LIGHT = "#E6F0FF"
    
    // Bubble preferences
    private const val KEY_BUBBLE_COLOR = "bubble_color"
    private const val DEFAULT_BUBBLE_COLOR = "#0C56CF"
    
    // Font preferences
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_FONT_FAMILY = "font_family"
    private const val DEFAULT_FONT_SIZE = 16f
    private const val DEFAULT_FONT_FAMILY = 0 // Typeface.DEFAULT
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Theme methods
    fun getThemeColor(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR
    }
    
    fun setThemeColor(context: Context, color: String) {
        getPrefs(context).edit().putString(KEY_THEME_COLOR, color).apply()
    }
    
    fun getThemeColorLight(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_COLOR_LIGHT, DEFAULT_THEME_COLOR_LIGHT) ?: DEFAULT_THEME_COLOR_LIGHT
    }
    
    fun setThemeColorLight(context: Context, color: String) {
        getPrefs(context).edit().putString(KEY_THEME_COLOR_LIGHT, color).apply()
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

