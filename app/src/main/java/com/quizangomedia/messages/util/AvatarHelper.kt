package com.quizangomedia.messages.util

import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import com.quizangomedia.messages.R

object AvatarHelper {
    
    private const val TAG = "AvatarHelper"
    
    // Predefined color palette for consistent avatar backgrounds
    private val avatarColors = listOf(
        Color.parseColor("#FF6B9D"),  // Pink
        Color.parseColor("#4ECDC4"),  // Teal
        Color.parseColor("#95E1D3"),  // Light teal
        // Light blue - will be replaced with theme light color dynamically
        Color.parseColor("#E6F0FF"),  // Light blue
        Color.parseColor("#FFD93D"),  // Yellow
        Color.parseColor("#6BCF7F"),  // Green
        Color.parseColor("#FF9F66"),  // Orange
        Color.parseColor("#A8E6CF"),  // Mint
        Color.parseColor("#FFB6C1"),  // Light pink
        Color.parseColor("#87CEEB"),  // Sky blue
        Color.parseColor("#DDA0DD"),  // Plum
        Color.parseColor("#98D8C8")   // Seafoam
    )
    
    /**
     * Get a consistent color for a given identifier (name or phone number)
     * The same identifier will always return the same color
     * If the color is #E6F0FF, it will be replaced with theme light color if context is provided
     */
    fun getColorForIdentifier(identifier: String, context: android.content.Context? = null): Int {
        val hash = identifier.hashCode()
        val index = Math.abs(hash) % avatarColors.size
        var color = avatarColors[index]
        
        // Replace #E6F0FF with theme light color if context is provided
        if (context != null && color == Color.parseColor("#E6F0FF")) {
            color = try {
                Color.parseColor(AppPreferences.getThemeColorLight(context))
            } catch (e: Exception) {
                color // Fallback to original color
            }
        }
        
        return color
    }
    
    /**
     * Get the first letter from a name or phone number
     */
    fun getFirstLetter(name: String?, address: String): String {
        val result = when {
            !name.isNullOrEmpty() -> {
                // Get first letter from name, handling multi-word names
                val trimmedName = name.trim()
                val firstWord = trimmedName.split(" ").firstOrNull() ?: ""
                val letter = firstWord.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                Log.d(TAG, "getFirstLetter: name='$name', trimmed='$trimmedName', firstWord='$firstWord', letter='$letter'")
                letter
            }
            address.isNotEmpty() -> {
                // For phone numbers, use first digit
                val trimmedAddress = address.trim()
                val firstChar = trimmedAddress.firstOrNull()
                val result = if (firstChar != null && firstChar.isDigit()) {
                    firstChar.toString()
                } else {
                    "#"
                }
                Log.d(TAG, "getFirstLetter: address='$address', trimmed='$trimmedAddress', firstChar='$firstChar', result='$result'")
                result
            }
            else -> {
                Log.d(TAG, "getFirstLetter: both name and address are empty, returning '#'")
                "#"
            }
        }
        Log.d(TAG, "getFirstLetter: final result='$result'")
        return result
    }
    
    /**
     * Check if a string looks like a phone number
     */
    private fun looksLikePhoneNumber(text: String): Boolean {
        // Remove common phone number characters
        val cleaned = text.replace(Regex("[\\s\\-\\(\\)\\+]"), "")
        // Check if it's mostly digits and has reasonable length for a phone number
        val digitCount = cleaned.count { it.isDigit() }
        return cleaned.length >= 7 && digitCount >= cleaned.length * 0.7
    }
    
    /**
     * Load avatar into CircleImageView with proper fallbacks
     * @param imageView The CircleImageView to load the avatar into
     * @param textView Optional TextView to show first letter (for custom drawable)
     * @param photoUri Contact photo URI if available
     * @param contactName Contact name if available
     * @param address Phone number/address
     * @param context Context for loading resources
     */
    fun loadAvatar(
        imageView: CircleImageView,
        textView: TextView?,
        photoUri: String?,
        contactName: String?,
        address: String,
        context: android.content.Context
    ) {
        Log.d(TAG, "loadAvatar: Starting - photoUri='$photoUri', contactName='$contactName', address='$address'")
        
        // Determine if we have a name - check contactName first, then check if address looks like a name
        val hasContactName = !contactName.isNullOrEmpty() && contactName.trim().isNotEmpty()
        val addressIsName = !hasContactName && !looksLikePhoneNumber(address) && address.trim().isNotEmpty()
        val shouldShowLetter = hasContactName || addressIsName
        
        Log.d(TAG, "loadAvatar: hasContactName=$hasContactName, addressIsName=$addressIsName, shouldShowLetter=$shouldShowLetter")
        
        val identifier = contactName ?: address
        val color = getColorForIdentifier(identifier, context)
        val displayName = if (hasContactName) contactName!! else if (addressIsName) address else address
        val firstLetter = getFirstLetter(displayName, address)
        
        Log.d(TAG, "loadAvatar: identifier='$identifier', color=$color, displayName='$displayName', firstLetter='$firstLetter'")
        
        // Set background color
        imageView.setCircleBackgroundColor(color)
        Log.d(TAG, "loadAvatar: Set background color to $color")
        
        // Clear any default image first
        imageView.setImageDrawable(null)
        
        // If we have a photo URI, try to load it
        if (!photoUri.isNullOrEmpty()) {
            Log.d(TAG, "loadAvatar: Photo URI available, attempting to load")
            try {
                val uri = Uri.parse(photoUri)
                Picasso.get()
                    .load(uri)
                    .placeholder(R.drawable.ic_person_white)
                    .error(R.drawable.ic_person_white)
                    .into(imageView)
                // Hide text view when showing image
                textView?.visibility = android.view.View.GONE
                Log.d(TAG, "loadAvatar: Photo loaded successfully, textView hidden")
            } catch (e: Exception) {
                Log.e(TAG, "loadAvatar: Error loading photo, falling back to letter/icon", e)
                // If loading fails, fall back to letter/icon
                loadFallbackAvatar(imageView, textView, shouldShowLetter, firstLetter, context, color)
            }
        } else {
            Log.d(TAG, "loadAvatar: No photo URI, showing fallback avatar")
            // No photo - show first letter if we have a contact name, otherwise show person icon
            loadFallbackAvatar(imageView, textView, shouldShowLetter, firstLetter, context, color)
        }
    }
    
    private fun loadFallbackAvatar(
        imageView: CircleImageView,
        textView: TextView?,
        hasContactName: Boolean,
        firstLetter: String,
        context: android.content.Context,
        backgroundColor: Int
    ) {
        Log.d(TAG, "loadFallbackAvatar: hasContactName=$hasContactName, firstLetter='$firstLetter', textView=${textView != null}, imageView.width=${imageView.width}, imageView.height=${imageView.height}")
        
        // Helper function to actually set up the avatar
        fun setupAvatar() {
            if (hasContactName) {
                    Log.d(TAG, "loadFallbackAvatar: Has contact name, showing first letter")
                    // Has contact name - show first letter
                    // Clear the image drawable (don't use setImageResource as it requires dimensions)
                    // The background color was already set on the CircleImageView earlier
                    imageView.setImageDrawable(null)
                    Log.d(TAG, "loadFallbackAvatar: Cleared image drawable")
                    
                    // Ensure text view is visible and on top
                    if (textView != null) {
                        val letter = firstLetter.trim().take(1).uppercase()
                        val finalLetter = if (letter.isNotEmpty()) letter else "#"
                        Log.d(TAG, "loadFallbackAvatar: Setting text to '$finalLetter' (from firstLetter='$firstLetter')")
                        
                        // Set text first - use setText with BufferType to ensure it's set
                        textView.setText(finalLetter, android.widget.TextView.BufferType.NORMAL)
                        // Set text color to dark grey (matching XML @color/gray_dark)
                        val darkGreyColor = ContextCompat.getColor(context, R.color.gray_dark)
                        textView.setTextColor(darkGreyColor)
                        // Set text size to fit in 56dp circle (14sp for better fit)
                        textView.textSize = 14f * context.resources.displayMetrics.scaledDensity
                        
                        // Create a circular drawable with the background color for the TextView
                        val drawable = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(backgroundColor)
                        }
                        textView.background = drawable
                        
                        // Remove any elevation/shadow for consistent appearance
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            textView.elevation = 0f
                            textView.translationZ = 0f
                        }
                        
                        // Hide the CircleImageView when showing the letter
                        imageView.visibility = android.view.View.GONE
                        
                        // Ensure it's visible
                        textView.visibility = android.view.View.VISIBLE
                        // Ensure alpha is 1.0 (fully opaque)
                        textView.alpha = 1.0f
                        
                        // Force layout and redraw
                        textView.requestLayout()
                        textView.invalidate()
                        
                        // Post again to ensure everything is laid out and visible
                        textView.post {
                            // Force a complete redraw
                            textView.invalidate()
                            textView.requestLayout()
                            val parentView = textView.parent as? android.view.View
                            parentView?.invalidate()
                            parentView?.requestLayout()
                            
                            // Also invalidate the imageView to ensure proper layering
                            imageView.invalidate()
                            
                            // Log detailed state
                            Log.d(TAG, "loadFallbackAvatar: TextView after post - visibility=${textView.visibility}, text='${textView.text}', textColor=${textView.currentTextColor}, width=${textView.width}, height=${textView.height}, alpha=${textView.alpha}, isShown=${textView.isShown}, hasFocus=${textView.hasFocus()}")
                            
                            // Try to force a draw by calling onDraw
                            textView.postDelayed({
                                textView.invalidate()
                                Log.d(TAG, "loadFallbackAvatar: TextView after delayed post - text='${textView.text}', visibility=${textView.visibility}")
                            }, 100)
                        }
                    } else {
                        Log.e(TAG, "loadFallbackAvatar: TextView is NULL! Cannot display letter")
                    }
                } else {
                    Log.d(TAG, "loadFallbackAvatar: No contact name, showing person icon")
                    // No contact name (phone number only) - show simple person icon (white for visibility on colored background)
                    // Show the CircleImageView
                    imageView.visibility = android.view.View.VISIBLE
                    // Clear any existing image first, then set the simple person icon
                    imageView.setImageDrawable(null)
                    // Remove any elevation/shadow for consistent appearance
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        imageView.elevation = 0f
                        imageView.translationZ = 0f
                    }
                    // Set image - it will work even if dimensions are 0 initially
                    imageView.setImageResource(R.drawable.ic_person_white)
                    Log.d(TAG, "loadFallbackAvatar: Person icon set")
                    // Hide text view
                    textView?.visibility = android.view.View.GONE
                    Log.d(TAG, "loadFallbackAvatar: Person icon set, textView hidden")
                }
        }
        
        // Check if view already has dimensions - if so, set up immediately
        if (imageView.width > 0 && imageView.height > 0) {
            setupAvatar()
        } else {
            // Use ViewTreeObserver to wait for layout to complete
            val viewTreeObserver = imageView.viewTreeObserver
            val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // Remove listener to avoid multiple calls
                    if (imageView.viewTreeObserver.isAlive) {
                        imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                    
                    Log.d(TAG, "loadFallbackAvatar: After layout - imageView.width=${imageView.width}, imageView.height=${imageView.height}, textView.width=${textView?.width}, textView.height=${textView?.height}")
                    
                    setupAvatar()
                }
            }
            viewTreeObserver.addOnGlobalLayoutListener(listener)
        }
    }
}

