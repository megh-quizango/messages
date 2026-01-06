package com.quizangomedia.messages.util

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.LayoutWindowBinding

class CallerWidgetWindow(private val context: Context) {

    companion object {
        private const val TAG = "CallerWidgetWindow"
    }

    private var mainView: View? = null
    private var binding: LayoutWindowBinding? = null

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun show() {
        try {
            // Inflate layout
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            binding = LayoutWindowBinding.inflate(inflater)
            mainView = binding?.root

            // Create layout parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = 0
            }

            // Add view to window manager
            windowManager.addView(mainView, params)
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
        mainView = null
        binding = null
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

