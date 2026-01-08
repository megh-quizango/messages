package com.text.messages.sms.messanger.ui.language

import android.content.Context
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
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityLanguageBinding
import com.text.messages.sms.messanger.databinding.NativeAdLayoutBinding
import com.text.messages.sms.messanger.util.LocaleHelper
import com.text.messages.sms.messanger.util.ThemeManager

class LanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: LanguageAdapter
    private var selectedLanguage: String = "System Default"
    private var nativeAd: NativeAd? = null
    private var isFromSettings: Boolean = false
    private var nativeAdView: NativeAdView? = null
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.text.messages.sms.messanger.util.AnalyticsHelper.logScreenView("LanguageActivity", "LanguageActivity")
        
        // Check if opened from Settings
        isFromSettings = intent.getBooleanExtra("from_settings", false)
        
        enableEdgeToEdge()
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        sharedPreferences = getSharedPreferences("MessagesPrefs", MODE_PRIVATE)
        
        // Load saved language preference
        selectedLanguage = LocaleHelper.getSavedLanguage(this)
        
        setupRecyclerView()
        setupNextButton()
        initializeNativeAdView()
        loadNativeAd()
    }
    
    private fun setupRecyclerView() {
        val languages = listOf(
            LanguageItem("System Default", false),
            LanguageItem("English", false),
            LanguageItem("Hindi", false),
            LanguageItem("Español", false),
            LanguageItem("Deutsch", false)
        )
        
        // Find and mark the saved language as selected
        val savedLanguage = selectedLanguage
        val languagesWithSelection = languages.map { 
            LanguageItem(it.name, it.name == savedLanguage) 
        }
        
        adapter = LanguageAdapter(languagesWithSelection) { language ->
            selectedLanguage = language
            adapter.updateSelection(language)
        }
        
        binding.recyclerViewLanguages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewLanguages.adapter = adapter
    }
    
    private fun setupNextButton() {
        binding.buttonNext.setOnClickListener {
            // Save language preference and apply locale
            LocaleHelper.setLanguage(this, selectedLanguage)
            LocaleHelper.updateLocale(this, selectedLanguage)
            
            // Save additional preference
            sharedPreferences.edit()
                .putBoolean("IS_LANGUAGE_SET", true)
                .apply()
            
            // Restart the app to apply language changes
            restartApp()
        }
    }
    
    private fun restartApp() {
        // If opened from Settings, restart the app to apply language changes
        if (isFromSettings) {
            // Restart the entire app by going to MainActivity, which will show the new language
            val intent = Intent(this, com.text.messages.sms.messanger.ui.main.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            // For first-time setup, navigate to Default SMS Activity
            val intent = Intent(this, com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    private fun initializeNativeAdView() {
        // Pre-inflate the native ad view structure so the layout is complete from the start
        nativeAdView = layoutInflater.inflate(R.layout.native_ad_layout, binding.nativeAdFrame, false) as NativeAdView
        binding.nativeAdFrame.addView(nativeAdView)
        
        // Apply theme to the pre-inflated view
        val themeColor = ThemeManager.getThemeColor(this)
        ThemeManager.applyTheme(this, nativeAdView!!)
        
        val adBinding = NativeAdLayoutBinding.bind(nativeAdView!!)
        
        // Apply theme to "Ad" label background
        val adLabel = nativeAdView!!.findViewById<android.widget.TextView>(R.id.nativeAdLabel)
        adLabel?.setBackgroundColor(themeColor)
        
        // Apply theme to info icon
        val infoIcon = nativeAdView!!.findViewById<android.widget.ImageView>(R.id.nativeAdInfoIcon)
        infoIcon?.imageTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Apply theme to call to action button
        adBinding.nativeAdCallToAction.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Register views with NativeAdView (will be populated when ad loads)
        nativeAdView!!.headlineView = adBinding.nativeAdHeadline
        nativeAdView!!.bodyView = adBinding.nativeAdBody
        nativeAdView!!.callToActionView = adBinding.nativeAdCallToAction
        nativeAdView!!.iconView = adBinding.nativeAdIcon
    }
    
    private fun loadNativeAd() {
        val nativeAdUnitId = com.text.messages.sms.messanger.util.RemoteConfigHelper.getNativeAdUnitId()
        val adLoader = AdLoader.Builder(this, nativeAdUnitId)
            .forNativeAd { ad ->
                nativeAd = ad
                populateNativeAdView(ad)
                com.text.messages.sms.messanger.util.AnalyticsHelper.logAdLoad("native", nativeAdUnitId, true)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    com.text.messages.sms.messanger.util.AnalyticsHelper.logAdLoad("native", nativeAdUnitId, false)
                    com.text.messages.sms.messanger.util.AnalyticsHelper.logAdError("native", nativeAdUnitId, loadAdError.code.toString())
                }
                
                override fun onAdClicked() {
                    super.onAdClicked()
                    com.text.messages.sms.messanger.util.AnalyticsHelper.logAdClick("native", nativeAdUnitId)
                }
                
                override fun onAdImpression() {
                    super.onAdImpression()
                    com.text.messages.sms.messanger.util.AnalyticsHelper.logAdImpression("native", nativeAdUnitId)
                }
            })
            .build()
        
        adLoader.loadAd(AdRequest.Builder().build())
    }
    
    private fun populateNativeAdView(ad: NativeAd) {
        // Use the pre-inflated view instead of creating a new one
        val adView = nativeAdView ?: return
        val adBinding = NativeAdLayoutBinding.bind(adView)
        
        // Set ad assets (view structure already exists, just populate with data)
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

