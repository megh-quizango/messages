package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.LayoutWindowBinding
import com.text.messages.sms.messanger.ui.main.MainActivity
import java.util.Locale

class CallerWidgetWindow(private val context: Context) {

    companion object {
        private const val TAG = "CallerWidgetWindow"
    }

    private var mainView: View? = null
    private var binding: LayoutWindowBinding? = null
    private val handler = Handler(Looper.getMainLooper())
    private val durationTicker = object : Runnable {
        override fun run() {
            updateDuration()
            handler.postDelayed(this, 1000L)
        }
    }

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun show() {
        try {
            if (mainView != null) {
                updateContent()
                return
            }

            // Inflate layout
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            binding = LayoutWindowBinding.inflate(inflater)
            mainView = binding?.root
            updateContent()

            // Create layout parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                x = 0
                y = dp(300)
            }

            // Add view to window manager
            windowManager.addView(mainView, params)
            handler.removeCallbacks(durationTicker)
            handler.post(durationTicker)
            Log.i(TAG, "addView SUCCESS")

        } catch (e: Exception) {
            Log.e(TAG, "addView FAILED: ${e.message}", e)
        }
    }

    fun hide() {
        mainView?.let { view ->
            try {
                windowManager.removeView(view)
                Log.i(TAG, "removeView SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "removeView FAILED: ${e.message}", e)
            }
        }
        handler.removeCallbacks(durationTicker)
        mainView = null
        binding = null
    }

    private fun updateContent() {
        val bound = binding ?: return
        val themeColor = parseColor(AppPreferences.getThemeColor(context), Color.parseColor("#347F80"))
        val darkerThemeColor = darkenColor(themeColor, 0.42f)
        bound.callerCadOverlayBg.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(themeColor, darkerThemeColor)
        ).apply {
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), Color.parseColor("#D4D4D4"))
        }
        bound.overlayAvatar.imageTintList = ColorStateList.valueOf(themeColor)
        bound.overlayName.text = resolveCallerDisplayName()
        bound.overlayClose.setOnClickListener { hide() }
        bound.overlayAppLink.setOnClickListener { openApp() }
        updateDuration()
    }

    private fun updateDuration() {
        val bound = binding ?: return
        val start = CallStateTracker.callStartTimeMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val elapsedSeconds = ((System.currentTimeMillis() - start).coerceAtLeast(0L) / 1000L)
        val minutes = elapsedSeconds / 60L
        val seconds = elapsedSeconds % 60L
        bound.overlayDuration.text = context.getString(
            R.string.caller_overlay_duration,
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        )
    }

    private fun resolveCallerDisplayName(): String {
        val number = CallStateTracker.phoneNumber
        if (number.isNullOrBlank()) {
            return context.getString(R.string.unknown_number)
        }
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else {
                    number
                }
            } ?: number
        } catch (e: Exception) {
            number
        }
    }

    private fun openApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            hide()
        }
    }

    private fun parseColor(value: String?, fallback: Int): Int {
        return try {
            Color.parseColor(value)
        } catch (e: Exception) {
            fallback
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        return Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun getWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
}

