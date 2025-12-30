package com.quizangomedia.messages.ui.defaultsms

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityDefaultSmsBinding
import com.quizangomedia.messages.ui.main.MainActivity

class DefaultSmsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDefaultSmsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityDefaultSmsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupButton()
    }
    
    private fun setupButton() {
        binding.buttonSetDefault.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(intent)
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
            
            // Mark default SMS as set
            getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DEFAULT_SMS_SET", true)
                .apply()
            
            // Navigate to MainActivity after setting default
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

