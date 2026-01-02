package com.quizangomedia.messages.ui.language

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityLanguageBinding
import com.quizangomedia.messages.databinding.NativeAdLayoutBinding

class LanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: LanguageAdapter
    private var selectedLanguage: String = "System Default"
    private var nativeAd: NativeAd? = null
    private var isFromSettings: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if opened from Settings
        isFromSettings = intent.getBooleanExtra("from_settings", false)
        
        enableEdgeToEdge()
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        setupRecyclerView()
        setupNextButton()
        loadNativeAd()
    }
    
    private fun setupRecyclerView() {
        val languages = listOf(
            LanguageItem("System Default", true),
            LanguageItem("English", false),
            LanguageItem("Hindi", false),
            LanguageItem("Español", false),
            LanguageItem("Deutsch", false)
        )
        
        adapter = LanguageAdapter(languages) { language ->
            selectedLanguage = language
            adapter.updateSelection(language)
        }
        
        binding.recyclerViewLanguages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewLanguages.adapter = adapter
    }
    
    private fun setupNextButton() {
        binding.buttonNext.setOnClickListener {
            // Save language preference
            sharedPreferences.edit()
                .putString("SELECTED_LANGUAGE", selectedLanguage)
                .putBoolean("IS_LANGUAGE_SET", true)
                .apply()
            
            // If opened from Settings, return to Settings
            if (isFromSettings) {
                finish()
            } else {
                // Navigate to Default SMS Activity (for first-time setup)
                startActivity(Intent(this, com.quizangomedia.messages.ui.defaultsms.DefaultSmsActivity::class.java))
                finish()
            }
        }
    }
    
    private fun loadNativeAd() {
        val adLoader = AdLoader.Builder(this, "ca-app-pub-3940256099942544/2247696110")
            .forNativeAd { ad ->
                nativeAd = ad
                populateNativeAdView(ad)
            }
            .build()
        
        adLoader.loadAd(AdRequest.Builder().build())
    }
    
    private fun populateNativeAdView(ad: NativeAd) {
        val adView = layoutInflater.inflate(R.layout.native_ad_layout, binding.nativeAdFrame, false) as NativeAdView
        binding.nativeAdFrame.removeAllViews()
        binding.nativeAdFrame.addView(adView)
        
        val adBinding = NativeAdLayoutBinding.bind(adView)
        
        // Register views with NativeAdView
        adView.headlineView = adBinding.nativeAdHeadline
        adView.bodyView = adBinding.nativeAdBody
        adView.callToActionView = adBinding.nativeAdCallToAction
        adView.iconView = adBinding.nativeAdIcon
        
        // Set ad assets
        if (ad.headline != null) {
            adBinding.nativeAdHeadline.text = ad.headline
        }
        if (ad.body != null) {
            adBinding.nativeAdBody.text = ad.body
        }
        if (ad.callToAction != null) {
            adBinding.nativeAdCallToAction.text = ad.callToAction
        }
        
        val icon = ad.icon
        if (icon != null) {
            adBinding.nativeAdIcon.setImageDrawable(icon.drawable)
            adBinding.nativeAdIcon.visibility = android.view.View.VISIBLE
        } else {
            adBinding.nativeAdIcon.visibility = android.view.View.GONE
        }
        
        // Handle main image
        if (ad.images.isNotEmpty() && ad.images[0].drawable != null) {
            adBinding.nativeAdMedia.setImageDrawable(ad.images[0].drawable)
            adBinding.nativeAdMedia.visibility = android.view.View.VISIBLE
        } else {
            adBinding.nativeAdMedia.visibility = android.view.View.GONE
        }
        
        // Register the view
        adView.setNativeAd(ad)
    }
    
    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
    }
}

data class LanguageItem(
    val name: String,
    val isSelected: Boolean
)

