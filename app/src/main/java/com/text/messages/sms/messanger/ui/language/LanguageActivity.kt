package com.text.messages.sms.messanger.ui.language

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityLanguageBinding
import com.text.messages.sms.messanger.databinding.NativeAdLayoutBinding
import com.text.messages.sms.messanger.util.AdLoadingShimmerHelper
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.AppOpenManager
import com.text.messages.sms.messanger.util.LanguageTransitionAdManager
import com.text.messages.sms.messanger.util.LocaleHelper
import com.text.messages.sms.messanger.util.RemoteConfigHelper
import com.text.messages.sms.messanger.util.ThemeManager

class LanguageActivity : BaseActivity() {

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: LanguageAdapter
    private var selectedLanguageCode: String = "system"
    private var nativeAd: NativeAd? = null
    private var isFromSettings: Boolean = false
    private var nativeAdView: NativeAdView? = null
    private var adaptiveBannerView: AdView? = null

    private val nativeFullscreenFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        restartApp()
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
        selectedLanguageCode = LocaleHelper.getSavedLanguageCode(this)

        LanguageTransitionAdManager.preload(applicationContext)
        setupRecyclerView()
        setupConfirmAction()
        initializeNativeAdView()
        loadLanguageAd()
    }
    
    private fun setupRecyclerView() {
        val savedLanguageCode = selectedLanguageCode
        val languages = LocaleHelper.getSupportedLanguages().map { language ->
            LanguageItem(
                code = language.code,
                displayName = LocaleHelper.getDisplayName(this, language.code),
                isSelected = language.code == savedLanguageCode
            )
        }
        
        adapter = LanguageAdapter(languages) { languageCode ->
            selectedLanguageCode = languageCode
            adapter.updateSelection(languageCode)
        }
        
        binding.recyclerViewLanguages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewLanguages.adapter = adapter
    }
    
    private fun setupConfirmAction() {
        binding.buttonConfirmLanguage.setOnClickListener {
            // Save language preference and apply locale
            LocaleHelper.updateLocale(this, selectedLanguageCode)

            // Save additional preference
            sharedPreferences.edit()
                .putBoolean("IS_LANGUAGE_SET", true)
                .apply()

            showTransitionAdThenContinue()
        }
    }

    private fun showTransitionAdThenContinue() {
        AppOpenManager.suppressAppOpenFor(8000L)
        if (LanguageTransitionAdManager.shouldUseNativeFullscreenOnly()) {
            launchNativeFullscreenFallbackOrContinue()
            return
        }

        val showedInterstitial = LanguageTransitionAdManager.showInterstitialIfAvailable(
            activity = this,
            onDismiss = { restartApp() },
            onFallbackToNative = { launchNativeFullscreenFallbackOrContinue() }
        )

        if (!showedInterstitial) {
            launchNativeFullscreenFallbackOrContinue()
        }
    }

    private fun launchNativeFullscreenFallbackOrContinue() {
        if (LanguageTransitionAdManager.hasNativeFullscreenAd()) {
            nativeFullscreenFallbackLauncher.launch(
                Intent(this, LanguageNativeFullscreenAdActivity::class.java)
            )
        } else {
            restartApp()
        }
    }
    
    private fun restartApp() {
        // If opened from Settings, restart the app to apply language changes
        if (isFromSettings) {
            // Restart the entire app by going to MainActivity, which will show the new language
            val intent = Intent(this, com.text.messages.sms.messanger.ui.main.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
        } else {
            // For first-time setup, navigate to Default SMS Activity
            val intent = Intent(this, com.text.messages.sms.messanger.ui.defaultsms.DefaultSmsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
        }
    }
    
    private fun initializeNativeAdView() {
        showLanguageAdLoading()

        // Pre-inflate the native ad view structure so the layout is complete from the start
        nativeAdView = layoutInflater.inflate(R.layout.native_ad_layout_language, binding.nativeAdFrame, false) as NativeAdView
        nativeAdView!!.visibility = android.view.View.GONE
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

    private fun loadLanguageAd() {
        if (RemoteConfigHelper.shouldUseLanguageAdaptiveBannerOnly()) {
            loadAdaptiveBanner()
        } else {
            loadNativeAd()
        }
    }

    private fun showLanguageAdLoading() {
        nativeAdView?.visibility = android.view.View.GONE
        adaptiveBannerView?.visibility = android.view.View.GONE
        AdLoadingShimmerHelper.showNativeLoading(
            binding.nativeAdFrame,
            shimmerLayoutRes = R.layout.layout_language_ad_shimmer
        )
    }

    private fun loadNativeAd() {
        val nativeAdUnitId = RemoteConfigHelper.getLanguageNativeBannerAdUnitId()
        if (nativeAdUnitId.isBlank()) {
            loadAdaptiveBanner()
            return
        }
        showLanguageAdLoading()
        val adLoader = AdLoader.Builder(this, nativeAdUnitId)
            .forNativeAd { ad ->
                nativeAd = ad
                populateNativeAdView(ad)
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, true)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    AnalyticsHelper.logAdLoad("native", nativeAdUnitId, false)
                    AnalyticsHelper.logAdError("native", nativeAdUnitId, loadAdError.code.toString())
                    loadAdaptiveBanner()
                }
                
                override fun onAdClicked() {
                    super.onAdClicked()
                    AnalyticsHelper.logAdClick("native", nativeAdUnitId)
                }
                
                override fun onAdImpression() {
                    super.onAdImpression()
                    AnalyticsHelper.logAdImpression("native", nativeAdUnitId)
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
        AdLoadingShimmerHelper.showNativeContent(binding.nativeAdFrame, adView)
    }

    private fun loadAdaptiveBanner() {
        val bannerAdUnitId = RemoteConfigHelper.getLanguageFallbackBannerAdUnitId()
        if (bannerAdUnitId.isBlank()) {
            nativeAdView?.visibility = android.view.View.GONE
            adaptiveBannerView?.visibility = android.view.View.GONE
            AdLoadingShimmerHelper.hideNative(binding.nativeAdFrame)
            return
        }

        showLanguageAdLoading()
        binding.nativeAdFrame.post {
            if (isFinishing || isDestroyed) {
                return@post
            }

            val adWidthPx = binding.nativeAdFrame.width
                .takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - binding.nativeAdFrame.paddingLeft - binding.nativeAdFrame.paddingRight)
            val adSize = getLanguageAdaptiveAdSize(adWidthPx)
            val slotHeightPx = measureLanguageAdSlotHeightPx(adWidthPx)
            val bannerView = getOrCreateAdaptiveBannerView(bannerAdUnitId)
            bannerView.setAdSize(adSize)
            bannerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                slotHeightPx
            )
            bannerView.visibility = android.view.View.GONE
            bannerView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    bannerView.adSize?.let { loadedAdSize ->
                        bannerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            loadedAdSize.getHeightInPixels(this@LanguageActivity)
                        )
                    }
                    AdLoadingShimmerHelper.showNativeContent(binding.nativeAdFrame, bannerView)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    nativeAdView?.visibility = android.view.View.GONE
                    bannerView.visibility = android.view.View.GONE
                    AdLoadingShimmerHelper.hideNative(binding.nativeAdFrame)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, false)
                    AnalyticsHelper.logAdError("banner", bannerAdUnitId, loadAdError.code.toString())
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    AnalyticsHelper.logAdClick("banner", bannerAdUnitId)
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    AnalyticsHelper.logAdImpression("banner", bannerAdUnitId)
                }
            }
            bannerView.loadAd(AdRequest.Builder().build())
        }
    }

    private fun getOrCreateAdaptiveBannerView(adUnitId: String): AdView {
        val existing = adaptiveBannerView
        if (existing != null && existing.adUnitId == adUnitId) {
            return existing
        }

        existing?.let {
            binding.nativeAdFrame.removeView(it)
            it.destroy()
        }

        return AdView(this).apply {
            this.adUnitId = adUnitId
            visibility = android.view.View.GONE
            binding.nativeAdFrame.addView(
                this,
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            adaptiveBannerView = this
        }
    }

    private fun getLanguageAdaptiveAdSize(adWidthPx: Int): AdSize {
        val adWidthDp = (adWidthPx / resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val slotHeightDp = (measureLanguageAdSlotHeightPx(adWidthPx) / resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(50)
        return AdSize.getInlineAdaptiveBannerAdSize(adWidthDp, slotHeightDp)
    }

    private fun measureLanguageAdSlotHeightPx(adWidthPx: Int): Int {
        val adView = nativeAdView ?: return (220 * resources.displayMetrics.density).toInt()
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(adWidthPx, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        adView.measure(widthSpec, heightSpec)
        return adView.measuredHeight.coerceAtLeast((220 * resources.displayMetrics.density).toInt())
    }
    
    override fun onDestroy() {
        nativeAd?.destroy()
        adaptiveBannerView?.destroy()
        adaptiveBannerView = null
        super.onDestroy()
    }
}

data class LanguageItem(
    val code: String,
    val displayName: String,
    val isSelected: Boolean
)

