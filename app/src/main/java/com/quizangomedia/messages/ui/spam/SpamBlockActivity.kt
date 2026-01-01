package com.quizangomedia.messages.ui.spam

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivitySpamBlockBinding
import com.quizangomedia.messages.ui.blocking.BlockedConversationsActivity
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
        
        setupBackButton()
        setupOptions()
        setupBannerAd()
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupOptions() {
        binding.cardKeywords.setOnClickListener {
            val intent = Intent(this, CustomBlockingActivity::class.java)
            intent.putExtra("from_numbers", false)
            startActivity(intent)
        }
        
        binding.cardNumbers.setOnClickListener {
            val intent = Intent(this, CustomBlockingActivity::class.java)
            intent.putExtra("from_numbers", true)
            startActivity(intent)
        }
        
        binding.cardMessageBlocked.setOnClickListener {
            startActivity(Intent(this, BlockedConversationsActivity::class.java))
        }
    }
    
    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }
}

