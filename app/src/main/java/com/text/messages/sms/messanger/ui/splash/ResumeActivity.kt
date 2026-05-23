package com.text.messages.sms.messanger.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.text.messages.sms.messanger.databinding.ActivityResumeBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.ui.main.MainActivity
import com.text.messages.sms.messanger.ui.main.MainViewModel

class ResumeActivity : BaseActivity() {

    private lateinit var binding: ActivityResumeBinding
    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityResumeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        preloadConversations()

        // Let the branded resume view render, then continue immediately.
        binding.root.post {
            navigateToMain()
        }
    }

    private fun preloadConversations() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        handler.post {
            try {
                val viewModel = ViewModelProvider(this@ResumeActivity)[MainViewModel::class.java]
                viewModel.preloadConversations("All")
            } catch (_: Exception) {
            }
        }
    }

    private fun navigateToMain() {
        if (hasNavigated || isFinishing || isDestroyed) {
            return
        }
        hasNavigated = true
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
