package com.quizangomedia.messages.util

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.lang.reflect.Method

object ThemeManager {
    
    private val PRIMARY_COLOR = "#0C56CF"
    private val LIGHT_COLOR = "#E6F0FF"
    
    /**
     * Apply theme colors to a view and its children recursively
     * Replaces all instances of #0C56CF with theme color and #E6F0FF with light theme color
     * This method is called immediately and also after layout to ensure all components are updated
     */
    fun applyTheme(context: Context, rootView: View) {
        val themeColor = AppPreferences.getThemeColor(context)
        val themeColorLight = AppPreferences.getThemeColorLight(context)
        
        // Always apply theme to ensure it works even if default colors are used
        // Apply immediately
        applyThemeToView(context, rootView, themeColor, themeColorLight)
        
        // Also apply after layout to catch any views that weren't ready
        rootView.post {
            applyThemeToView(context, rootView, themeColor, themeColorLight)
        }
    }
    
    private fun applyThemeToView(context: Context, view: View, themeColor: String, themeColorLight: String) {
        try {
            val themeColorInt = Color.parseColor(themeColor)
            val themeColorLightInt = Color.parseColor(themeColorLight)
            val primaryColorInt = Color.parseColor(PRIMARY_COLOR)
            val lightColorInt = Color.parseColor(LIGHT_COLOR)
            
            // Handle MaterialCardView cardBackgroundColor
            if (view is MaterialCardView) {
                val currentColor = view.cardBackgroundColor?.defaultColor
                if (currentColor == primaryColorInt) {
                    view.setCardBackgroundColor(themeColorInt)
                } else if (currentColor == lightColorInt) {
                    view.setCardBackgroundColor(themeColorLightInt)
                }
            }
            
            // Handle background drawable
            // Always check background drawable, even if backgroundTintList is set
            val background = view.background
            if (background != null && view.backgroundTintList == null) {
                val newBackground = applyThemeToDrawable(background.mutate(), themeColor, themeColorLight, primaryColorInt, lightColorInt)
                if (newBackground != null && newBackground !== background) {
                    view.background = newBackground
                }
            } else if (background != null) {
                // Even if backgroundTintList is set, still try to apply theme to drawable
                // This handles cases where both are set
                applyThemeToDrawable(background.mutate(), themeColor, themeColorLight, primaryColorInt, lightColorInt)
            }
            
            // Handle backgroundTint
            try {
                val backgroundTint = view.backgroundTintList
                if (backgroundTint != null) {
                    val currentTintColor = backgroundTint.defaultColor
                    if (currentTintColor == primaryColorInt) {
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    } else if (currentTintColor == lightColorInt) {
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColorLightInt)
                    }
                }
            } catch (e: Exception) {
                // Some views don't support backgroundTintList
            }
            
            // Handle TextView textColor
            if (view is TextView) {
                try {
                    val textColors = view.textColors
                    val currentTextColor = textColors?.defaultColor
                    if (currentTextColor == primaryColorInt) {
                        view.setTextColor(themeColorInt)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle ImageView tint
            if (view is ImageView) {
                try {
                    val tintList = view.imageTintList
                    if (tintList != null) {
                        val currentTintColor = tintList.defaultColor
                        if (currentTintColor == primaryColorInt) {
                            view.setColorFilter(themeColorInt, PorterDuff.Mode.SRC_IN)
                            view.imageTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                        }
                    } else {
                        // Check if background is transparent or matches theme color
                        val background = view.background
                        if (background is ColorDrawable) {
                            val bgColor = background.color
                            if (bgColor == primaryColorInt || bgColor == android.graphics.Color.TRANSPARENT) {
                                // Apply theme color tint
                                view.imageTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle ProgressBar
            if (view is ProgressBar) {
                try {
                    val progressTint = view.progressTintList
                    if (progressTint != null && progressTint.defaultColor == primaryColorInt) {
                        view.progressTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    } else if (progressTint == null) {
                        // Apply theme color if no tint is set
                        view.progressTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    }
                    val indeterminateTint = view.indeterminateTintList
                    if (indeterminateTint != null && indeterminateTint.defaultColor == primaryColorInt) {
                        view.indeterminateTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    } else if (indeterminateTint == null) {
                        // Apply theme color if no tint is set
                        view.indeterminateTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle SeekBar
            if (view is SeekBar) {
                try {
                    val progressTint = view.progressTintList
                    if (progressTint != null && progressTint.defaultColor == primaryColorInt) {
                        view.progressTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    }
                    val thumbTint = view.thumbTintList
                    if (thumbTint != null && thumbTint.defaultColor == primaryColorInt) {
                        view.thumbTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle Switch - only apply theme if custom drawables are not set
            if (view is Switch) {
                try {
                    // Only apply theme if custom drawables are not already set
                    // Custom drawables are set when thumbDrawable and trackDrawable are StateListDrawable
                    // This indicates the toggle has been styled using applyToggleTheme()
                    val hasCustomDrawables = view.thumbDrawable is StateListDrawable && 
                                           view.trackDrawable is StateListDrawable
                    
                    if (!hasCustomDrawables) {
                        // Only apply default theme if custom drawables are not set
                        val thumbTint = view.thumbTintList
                        if (thumbTint != null && thumbTint.defaultColor == primaryColorInt) {
                            view.thumbTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                        } else if (thumbTint == null) {
                            // Apply theme color if no tint is set
                            view.thumbTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                        }
                        val trackTint = view.trackTintList
                        if (trackTint != null && trackTint.defaultColor == primaryColorInt) {
                            view.trackTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                        }
                    } else {
                        // If custom drawables are set (via applyToggleTheme), preserve them
                        // Re-apply theme to update colors if theme changed
                        // IMPORTANT: Don't change visibility - preserve whatever was set
                        applyToggleTheme(view, context)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle RadioButton
            if (view is RadioButton) {
                try {
                    val buttonTint = view.buttonTintList
                    if (buttonTint != null && buttonTint.defaultColor == primaryColorInt) {
                        view.buttonTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    } else if (buttonTint == null) {
                        // Apply theme color if no tint is set
                        view.buttonTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle MaterialButton
            if (view is MaterialButton) {
                try {
                    val backgroundTint = view.backgroundTintList
                    if (backgroundTint != null) {
                        val currentTintColor = backgroundTint.defaultColor
                        if (currentTintColor == primaryColorInt) {
                            view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                        } else if (currentTintColor == lightColorInt) {
                            view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColorLightInt)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle TextInputLayout
            if (view is TextInputLayout) {
                try {
                    // Box background color
                    val boxBackgroundColor = view.boxBackgroundColor
                    if (boxBackgroundColor == lightColorInt) {
                        view.setBoxBackgroundColor(themeColorLightInt)
                    }
                    // Box stroke color (focus color)
                    view.setBoxStrokeColor(themeColorInt)
                    // Default hint text color
                    view.setDefaultHintTextColor(android.content.res.ColorStateList.valueOf(themeColorInt))
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle BottomNavigationView
            if (view is BottomNavigationView) {
                try {
                    // For BottomNavigationView, always apply theme color directly
                    // since it's typically set via XML with hardcoded color
                    view.setBackgroundColor(themeColorInt)
                    
                    // Also try to apply to background drawable if it exists
                    val background = view.background
                    if (background != null && background !is ColorDrawable) {
                        applyThemeToDrawable(background.mutate(), themeColor, themeColorLight, primaryColorInt, lightColorInt)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle ExtendedFloatingActionButton
            if (view is ExtendedFloatingActionButton) {
                try {
                    // For ExtendedFloatingActionButton, always apply theme color to backgroundTintList
                    // since it's typically set via XML with hardcoded color
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColorInt)
                    
                    // Also try to apply to background drawable if it exists
                    val background = view.background
                    if (background != null) {
                        applyThemeToDrawable(background.mutate(), themeColor, themeColorLight, primaryColorInt, lightColorInt)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Handle CircularProgressIndicator
            if (view is CircularProgressIndicator) {
                try {
                    // Always apply theme colors since we can't reliably read the current colors
                    view.setIndicatorColor(themeColorInt)
                    view.setTrackColor(themeColorLightInt)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
        } catch (e: Exception) {
            // Ignore errors for individual views
        }
        
        // Recursively apply to children
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeToView(context, view.getChildAt(i), themeColor, themeColorLight)
            }
        }
    }
    
    private fun applyThemeToDrawable(
        drawable: Drawable,
        themeColor: String,
        themeColorLight: String,
        primaryColorInt: Int,
        lightColorInt: Int
    ): Drawable? {
        return when (drawable) {
            is GradientDrawable -> {
                try {
                    val mutable = drawable.mutate()
                    if (mutable is GradientDrawable) {
                        // Try to get the color using reflection
                        val color = getGradientDrawableColor(mutable)
                        if (color == primaryColorInt) {
                            mutable.setColor(Color.parseColor(themeColor))
                        } else if (color == lightColorInt) {
                            mutable.setColor(Color.parseColor(themeColorLight))
                        } else if (color == null) {
                            // If we can't detect the color, try using reflection to access mColor field
                            val colorField: java.lang.reflect.Field? = try {
                                GradientDrawable::class.java.getDeclaredField("mColor")
                            } catch (e: NoSuchFieldException) {
                                null
                            }
                            
                            if (colorField != null) {
                                colorField.isAccessible = true
                                val colorStateList = colorField.get(mutable) as? android.content.res.ColorStateList
                                if (colorStateList != null) {
                                    val currentColor = colorStateList.defaultColor
                                    if (currentColor == primaryColorInt) {
                                        colorField.set(mutable, android.content.res.ColorStateList.valueOf(Color.parseColor(themeColor)))
                                    } else if (currentColor == lightColorInt) {
                                        colorField.set(mutable, android.content.res.ColorStateList.valueOf(Color.parseColor(themeColorLight)))
                                    }
                                } else {
                                    // Try to set color directly if mColor is not a ColorStateList
                                    try {
                                        mutable.setColor(Color.parseColor(themeColorLight)) // Default to light for containers
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            } else {
                                // Fallback: try to set color directly
                                try {
                                    mutable.setColor(Color.parseColor(themeColorLight)) // Default to light for containers
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    }
                    drawable
                } catch (e: Exception) {
                    drawable
                }
            }
            is ColorDrawable -> {
                try {
                    val color = drawable.color
                    if (color == primaryColorInt) {
                        ColorDrawable(Color.parseColor(themeColor))
                    } else if (color == lightColorInt) {
                        ColorDrawable(Color.parseColor(themeColorLight))
                    } else {
                        drawable
                    }
                } catch (e: Exception) {
                    drawable
                }
            }
            is LayerDrawable -> {
                // Recursively apply to layers
                for (i in 0 until drawable.numberOfLayers) {
                    val layer = drawable.getDrawable(i)
                    applyThemeToDrawable(layer, themeColor, themeColorLight, primaryColorInt, lightColorInt)
                }
                drawable
            }
            is StateListDrawable -> {
                // Apply to all states
                for (i in 0 until drawable.stateCount) {
                    val stateDrawable = drawable.getStateDrawable(i)
                    if (stateDrawable != null) {
                        applyThemeToDrawable(stateDrawable, themeColor, themeColorLight, primaryColorInt, lightColorInt)
                    }
                }
                drawable
            }
            else -> {
                // For other drawables, try to apply color filter if it matches
                try {
                    val colorFilter = drawable.colorFilter
                    if (colorFilter == null) {
                        // Try to wrap and tint
                        val wrapped = DrawableCompat.wrap(drawable.mutate())
                        // We can't easily detect the color, so we'll leave it as is
                        // The view-level checks will handle most cases
                    }
                    drawable
                } catch (e: Exception) {
                    drawable
                }
            }
        }
    }
    
    /**
     * Use reflection to get the color from a GradientDrawable
     */
    private fun getGradientDrawableColor(drawable: GradientDrawable): Int? {
        return try {
            // Try to get the color using reflection
            val method: Method = GradientDrawable::class.java.getDeclaredMethod("getColor")
            method.isAccessible = true
            val colorStateList = method.invoke(drawable) as? android.content.res.ColorStateList
            colorStateList?.defaultColor
        } catch (e: Exception) {
            try {
                // Alternative: check if it's a solid color by examining the constant state
                // This is a fallback - we'll return null and let the view-level checks handle it
                null
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * Apply theme to a dialog's root view
     * Call this after creating a dialog but before showing it
     */
    fun applyThemeToDialog(context: Context, dialogView: View) {
        applyTheme(context, dialogView)
        // Also apply after layout
        dialogView.post {
            applyTheme(context, dialogView)
        }
    }
    
    /**
     * Apply theme to a bottom sheet's root view
     * Call this after creating a bottom sheet but before showing it
     */
    fun applyThemeToBottomSheet(context: Context, bottomSheetView: View) {
        applyTheme(context, bottomSheetView)
        // Also apply after layout
        bottomSheetView.post {
            applyTheme(context, bottomSheetView)
        }
    }
    
    /**
     * Aggressively apply theme to MaterialDatePicker and MaterialTimePicker dialogs
     * This method finds and themes all internal Material component views
     */
    fun applyThemeToMaterialPicker(context: Context, rootView: View) {
        val themeColor = getThemeColor(context)
        val themeColorLight = getThemeColorLight(context)
        val primaryColorInt = Color.parseColor(PRIMARY_COLOR)
        
        // Common Material Design purple colors that need to be replaced
        val purpleColors = listOf(
            Color.parseColor("#6200EE"), // Material Design default purple
            Color.parseColor("#6750A4"), // Material Design 3 purple
            Color.parseColor("#6366F1"), // Another purple variant
            Color.parseColor("#7B1FA2"), // Material purple variant
            Color.parseColor("#9C27B0"), // Material purple
            primaryColorInt
        )
        
        // Light purple colors (for time picker hour containers)
        val lightPurpleColors = listOf(
            Color.parseColor("#E1BEE7"), // Light purple
            Color.parseColor("#F3E5F5"), // Very light purple
            Color.parseColor("#E8EAF6"), // Light indigo
            Color.parseColor("#C5CAE9")  // Light indigo variant
        )
        
        // Ultra-aggressive theming: find and replace ALL views with primary/purple colors
        fun aggressiveTheme(view: View) {
            try {
                // Check background color - replace any purple or primary color
                val bg = view.background
                if (bg is ColorDrawable) {
                    val bgColor = bg.color
                    if (bgColor == primaryColorInt || purpleColors.contains(bgColor) || lightPurpleColors.contains(bgColor)) {
                        view.setBackgroundColor(themeColor)
                    } else if (bgColor == Color.BLACK || bgColor == Color.parseColor("#000000")) {
                        // Replace black backgrounds with theme color (for buttons)
                        view.setBackgroundColor(themeColor)
                    }
                } else if (bg is GradientDrawable) {
                    val color = getGradientDrawableColor(bg)
                    if (color == primaryColorInt || (color != null && (purpleColors.contains(color) || lightPurpleColors.contains(color)))) {
                        bg.setColor(themeColor)
                        view.background = bg
                    }
                }
                
                // Check background tint
                val bgTint = view.backgroundTintList
                if (bgTint != null) {
                    val tintColor = bgTint.defaultColor
                    if (tintColor == primaryColorInt || purpleColors.contains(tintColor)) {
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
                    }
                }
                
                // For buttons, apply theme color if background matches
                if (view is Button || view is MaterialButton) {
                    val btnBg = view.background
                    if (btnBg is ColorDrawable) {
                        val btnBgColor = btnBg.color
                        if (btnBgColor == primaryColorInt || purpleColors.contains(btnBgColor) || lightPurpleColors.contains(btnBgColor) ||
                            btnBgColor == Color.BLACK || btnBgColor == Color.parseColor("#000000")) {
                            view.setBackgroundColor(themeColor)
                        }
                    }
                    // Also update background tint if it matches
                    val btnBgTint = view.backgroundTintList
                    if (btnBgTint != null) {
                        val tintColor = btnBgTint.defaultColor
                        if (tintColor == primaryColorInt || purpleColors.contains(tintColor) || lightPurpleColors.contains(tintColor)) {
                            view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
                        }
                    }
                }
                
                // Check text color - replace purple/primary with theme, fix black text on colored backgrounds
                if (view is TextView) {
                    val textColor = view.currentTextColor
                    if (textColor == primaryColorInt || purpleColors.contains(textColor)) {
                        view.setTextColor(themeColor)
                    }
                    // If text is black and background is theme-colored, make text white
                    if (textColor == Color.BLACK || textColor == Color.parseColor("#000000")) {
                        val parentBg = (view.parent as? View)?.background
                        val viewBg = view.background
                        val hasColoredBg = (parentBg is ColorDrawable && (purpleColors.contains((parentBg as ColorDrawable).color) || 
                            (parentBg as ColorDrawable).color == themeColor)) ||
                            (viewBg is ColorDrawable && (purpleColors.contains((viewBg as ColorDrawable).color) || 
                            (viewBg as ColorDrawable).color == themeColor))
                        if (hasColoredBg) {
                            view.setTextColor(Color.WHITE)
                        }
                    }
                }
                
                // Check for ImageView with color filter (for clock hands, icons, etc.)
                if (view is ImageView) {
                    val colorFilter = view.colorFilter
                    if (colorFilter != null) {
                        // Replace any color filter with theme color (for clock hands, etc.)
                        view.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
                    }
                    // Also check if background is purple/primary
                    val imgBg = view.background
                    if (imgBg is ColorDrawable) {
                        val imgBgColor = imgBg.color
                        if (imgBgColor == primaryColorInt || purpleColors.contains(imgBgColor) || lightPurpleColors.contains(imgBgColor)) {
                            view.setBackgroundColor(themeColor)
                        }
                    }
                }
                
                // Recursively apply to children
                if (view is android.view.ViewGroup) {
                    for (i in 0 until view.childCount) {
                        aggressiveTheme(view.getChildAt(i))
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // Apply immediately
        applyTheme(context, rootView)
        aggressiveTheme(rootView)
        findAndThemeViews(rootView, themeColor, themeColorLight, purpleColors, lightPurpleColors, primaryColorInt)
        
        // Apply with many delayed attempts
        val delays = listOf(50, 100, 200, 300, 500, 800, 1000, 1500, 2000, 3000)
        delays.forEach { delay ->
            rootView.postDelayed({
                applyTheme(context, rootView)
                aggressiveTheme(rootView)
                findAndThemeViews(rootView, themeColor, themeColorLight, purpleColors, lightPurpleColors, primaryColorInt)
            }, delay.toLong())
        }
    }
    
    private fun findAndThemeViews(view: View, themeColor: Int, themeColorLight: Int, purpleColors: List<Int>, lightPurpleColors: List<Int>, primaryColorInt: Int) {
        try {
            val primaryColorInt = Color.parseColor(PRIMARY_COLOR)
            
            // Check if this view has a background color that matches primary color
            val background = view.background
            if (background is ColorDrawable) {
                val bgColor = background.color
                if (bgColor == primaryColorInt) {
                    view.setBackgroundColor(themeColor)
                }
            } else if (background is GradientDrawable) {
                // Check gradient drawable color
                val color = getGradientDrawableColor(background)
                if (color == primaryColorInt) {
                    background.setColor(themeColor)
                    view.background = background
                }
            }
            
            // Check background tint
            val backgroundTint = view.backgroundTintList
            if (backgroundTint != null) {
                val tintColor = backgroundTint.defaultColor
                if (tintColor == primaryColorInt) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
                }
            }
            
            // Check text color - be more aggressive
            if (view is TextView) {
                val textColor = view.currentTextColor
                if (textColor == primaryColorInt || view.textColors?.defaultColor == primaryColorInt) {
                    view.setTextColor(themeColor)
                }
            }
            
            // Check for MaterialButton - be more aggressive
            if (view is MaterialButton) {
                val bgTint = view.backgroundTintList
                if (bgTint != null) {
                    val tintColor = bgTint.defaultColor
                    if (tintColor == primaryColorInt || purpleColors.contains(tintColor) || lightPurpleColors.contains(tintColor)) {
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
                    }
                }
                // Also check text color
                if (view.currentTextColor == primaryColorInt || purpleColors.contains(view.currentTextColor)) {
                    view.setTextColor(themeColor)
                }
            }
            
            // Check for Button - be more aggressive
            if (view is Button) {
                val bgTint = view.backgroundTintList
                if (bgTint != null) {
                    val tintColor = bgTint.defaultColor
                    if (tintColor == primaryColorInt || purpleColors.contains(tintColor) || lightPurpleColors.contains(tintColor)) {
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
                    }
                }
                // Also check text color
                if (view.currentTextColor == primaryColorInt || purpleColors.contains(view.currentTextColor)) {
                    view.setTextColor(themeColor)
                }
            }
            
            // Check for MaterialCardView (time picker containers)
            if (view is MaterialCardView) {
                val cardBgColor = view.cardBackgroundColor.defaultColor
                if (purpleColors.contains(cardBgColor) || lightPurpleColors.contains(cardBgColor) || 
                    cardBgColor == Color.BLACK || cardBgColor == Color.parseColor("#000000")) {
                    view.setCardBackgroundColor(themeColor)
                }
            }
            
            // Try to find Material picker specific views by resource name
            try {
                val resources = view.context.resources
                
                // Try to find header selection view (the purple header)
                try {
                    val headerSelectionId = resources.getIdentifier(
                        "mtrl_picker_header_selection",
                        "id",
                        "com.google.android.material"
                    )
                    if (headerSelectionId != 0) {
                        val headerView = view.findViewById<View>(headerSelectionId)
                        headerView?.setBackgroundColor(themeColor)
                        // Also try to find text views inside header
                        if (headerView is android.view.ViewGroup) {
                            for (i in 0 until headerView.childCount) {
                                val child = headerView.getChildAt(i)
                                if (child is TextView) {
                                    child.setTextColor(android.graphics.Color.WHITE)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Try to find confirm button (OK button)
                try {
                    val confirmButtonId = resources.getIdentifier(
                        "confirm_button",
                        "id",
                        "com.google.android.material"
                    )
                    if (confirmButtonId != 0) {
                        val confirmButton = view.findViewById<Button>(confirmButtonId)
                        confirmButton?.let { btn ->
                            btn.setTextColor(themeColor)
                            // Also try to set background color directly
                            val bgDrawable = btn.background
                            if (bgDrawable is ColorDrawable) {
                                val bgColor = bgDrawable.color
                                if (bgColor == primaryColorInt || purpleColors.contains(bgColor) || lightPurpleColors.contains(bgColor) ||
                                    bgColor == Color.BLACK || bgColor == Color.parseColor("#000000")) {
                                    btn.setBackgroundColor(themeColor)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Try to find cancel button
                try {
                    val cancelButtonId = resources.getIdentifier(
                        "cancel_button",
                        "id",
                        "com.google.android.material"
                    )
                    if (cancelButtonId != 0) {
                        val cancelButton = view.findViewById<Button>(cancelButtonId)
                        cancelButton?.let { btn ->
                            btn.setTextColor(themeColor)
                            val bgDrawable = btn.background
                            if (bgDrawable is ColorDrawable) {
                                val bgColor = bgDrawable.color
                                if (bgColor == primaryColorInt || purpleColors.contains(bgColor) || lightPurpleColors.contains(bgColor) ||
                                    bgColor == Color.BLACK || bgColor == Color.parseColor("#000000")) {
                                    btn.setBackgroundColor(themeColor)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Try to find header toggle button
                try {
                    val headerToggleId = resources.getIdentifier(
                        "mtrl_picker_header_toggle",
                        "id",
                        "com.google.android.material"
                    )
                    if (headerToggleId != 0) {
                        val toggleButton = view.findViewById<Button>(headerToggleId)
                        toggleButton?.let { btn ->
                            btn.setTextColor(android.graphics.Color.WHITE)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Try to find all containers and buttons in the picker
                try {
                    if (view is android.view.ViewGroup) {
                        for (i in 0 until view.childCount) {
                            val child = view.getChildAt(i)
                            // Check for MaterialCardView containers (time picker hour/minute containers)
                            if (child is MaterialCardView) {
                                val cardBgColor = child.cardBackgroundColor.defaultColor
                                if (purpleColors.contains(cardBgColor) || lightPurpleColors.contains(cardBgColor) ||
                                    cardBgColor == Color.BLACK || cardBgColor == Color.parseColor("#000000")) {
                                    child.setCardBackgroundColor(themeColor)
                                }
                            }
                            // Check for buttons
                            if (child is Button || child is MaterialButton) {
                                val btnBg = child.background
                                if (btnBg is ColorDrawable) {
                                    val btnBgColor = btnBg.color
                                    if (btnBgColor == primaryColorInt || purpleColors.contains(btnBgColor) || lightPurpleColors.contains(btnBgColor) ||
                                        btnBgColor == Color.BLACK || btnBgColor == Color.parseColor("#000000")) {
                                        child.setBackgroundColor(themeColor)
                                    }
                                }
                                // Apply theme color to text if it matches primary
                                if (child is TextView && (child.currentTextColor == primaryColorInt || purpleColors.contains(child.currentTextColor))) {
                                    child.setTextColor(themeColor)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Ultra-aggressive: Find views by traversing and checking colors directly
                try {
                    findViewsByColor(view, primaryColorInt, themeColor)
                } catch (e: Exception) {
                    // Ignore
                }
            } catch (e: Exception) {
                // Ignore
            }
            
        } catch (e: Exception) {
            // Ignore errors for individual views
        }
        
        // Recursively apply to children
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndThemeViews(view.getChildAt(i), themeColor, themeColorLight, purpleColors, lightPurpleColors, primaryColorInt)
            }
        }
    }
    
    /**
     * Ultra-aggressive: Find all views with a specific color and replace them
     */
    private fun findViewsByColor(view: View, targetColor: Int, replacementColor: Int) {
        try {
            // Check background
            val bg = view.background
            if (bg is ColorDrawable) {
                val bgColor = bg.color
                if (bgColor == targetColor) {
                    view.setBackgroundColor(replacementColor)
                } else if (bgColor == Color.BLACK || bgColor == Color.parseColor("#000000")) {
                    // Also replace black backgrounds for buttons
                    if (view is Button || view is MaterialButton) {
                        view.setBackgroundColor(replacementColor)
                    }
                }
            } else if (bg is GradientDrawable) {
                val color = getGradientDrawableColor(bg)
                if (color == targetColor || (color != null && color == Color.BLACK)) {
                    bg.setColor(replacementColor)
                    view.background = bg
                }
            }
            
            // Check background tint
            val bgTint = view.backgroundTintList
            if (bgTint != null) {
                val tintColor = bgTint.defaultColor
                if (tintColor == targetColor) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(replacementColor)
                }
            }
            
            // Check text color
            if (view is TextView) {
                val textColor = view.currentTextColor
                if (textColor == targetColor) {
                    view.setTextColor(replacementColor)
                }
            }
            
            // Recursively check children
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    findViewsByColor(view.getChildAt(i), targetColor, replacementColor)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Get theme color for programmatic use
     */
    fun getThemeColor(context: Context): Int {
        return Color.parseColor(AppPreferences.getThemeColor(context))
    }
    
    /**
     * Get light theme color for programmatic use
     */
    fun getThemeColorLight(context: Context): Int {
        return Color.parseColor(AppPreferences.getThemeColorLight(context))
    }
    
    /**
     * Apply theme-based styling to a Switch toggle
     * This creates a pill-shaped toggle with theme-based colors:
     * - Track: White background with theme border when checked, gray when unchecked
     * - Thumb: Theme color circle when checked, gray when unchecked
     * 
     * @param switchToggle The Switch to style
     * @param context The context to get theme colors
     */
    fun applyToggleTheme(switchToggle: Switch, context: Context) {
        val themeColor = getThemeColor(context)
        val density = context.resources.displayMetrics.density
        
        // Calculate proper dimensions for track and thumb
        val trackWidth = (36 * density).toInt()
        val trackHeight = (20 * density).toInt()
        val thumbSize = (14 * density).toInt()
        
        // Create track drawable: white background with theme border when checked, gray when unchecked
        val checkedTrackDrawable = GradientDrawable().apply {
            cornerRadius = 10f * density
            setColor(Color.WHITE)
            setStroke((2 * density).toInt(), themeColor)
        }
        val uncheckedTrackDrawable = GradientDrawable().apply {
            cornerRadius = 10f * density
            setColor(Color.parseColor("#E0E0E0"))
        }
        
        // Set intrinsic bounds for drawables so they render properly
        checkedTrackDrawable.setBounds(0, 0, trackWidth, trackHeight)
        uncheckedTrackDrawable.setBounds(0, 0, trackWidth, trackHeight)
        
        val trackStateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), checkedTrackDrawable)
            addState(intArrayOf(), uncheckedTrackDrawable)
        }
        trackStateList.setBounds(0, 0, trackWidth, trackHeight)
        
        // Create thumb drawable: theme color circle when checked, gray when unchecked
        val checkedThumbDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(themeColor)
        }
        val uncheckedThumbDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#9696A3"))
        }
        
        // Set intrinsic bounds for thumb drawables
        checkedThumbDrawable.setBounds(0, 0, thumbSize, thumbSize)
        uncheckedThumbDrawable.setBounds(0, 0, thumbSize, thumbSize)
        
        val thumbStateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), checkedThumbDrawable)
            addState(intArrayOf(), uncheckedThumbDrawable)
        }
        thumbStateList.setBounds(0, 0, thumbSize, thumbSize)
        
        // Apply drawables and disable tints to use custom drawables
        switchToggle.trackDrawable = trackStateList
        switchToggle.thumbDrawable = thumbStateList
        switchToggle.thumbTintList = null
        switchToggle.trackTintList = null
        switchToggle.thumbTintMode = null
        switchToggle.trackTintMode = null
        
        // Ensure Switch has proper minimum width and height
        switchToggle.switchMinWidth = trackWidth
        switchToggle.minHeight = trackHeight
        switchToggle.switchPadding = (4 * density).toInt()
        
        // Force the Switch to redraw with proper dimensions
        switchToggle.invalidate()
        switchToggle.requestLayout()
    }
}
