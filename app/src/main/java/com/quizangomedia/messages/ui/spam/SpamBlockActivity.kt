package com.quizangomedia.messages.ui.spam

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivitySpamBlockBinding
import com.quizangomedia.messages.ui.blocking.CustomBlockingActivity

class SpamBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpamBlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivitySpamBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupToolbar()
        setupOptions()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupOptions() {
        binding.cardKeywords.setOnClickListener {
            startActivity(Intent(this, CustomBlockingActivity::class.java))
        }
        
        binding.cardNumbers.setOnClickListener {
            startActivity(Intent(this, CustomBlockingActivity::class.java))
        }
        
        binding.cardMessageBlocked.setOnClickListener {
            startActivity(Intent(this, CustomBlockingActivity::class.java))
        }
    }
}

