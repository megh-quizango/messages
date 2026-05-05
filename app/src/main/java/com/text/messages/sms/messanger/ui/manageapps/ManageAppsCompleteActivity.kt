package com.text.messages.sms.messanger.ui.manageapps

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityManageAppsCompleteBinding
import com.text.messages.sms.messanger.databinding.NativeAdLayoutBinding
import com.text.messages.sms.messanger.util.ThemeManager

class ManageAppsCompleteActivity : BaseActivity() {

    private lateinit var binding: ActivityManageAppsCompleteBinding
    private var nativeAd: NativeAd? = null
    private var nativeAdView: NativeAdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityManageAppsCompleteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val stoppedCount = intent.getIntExtra("stopped_count", 0)
        binding.textStoppedApps.text = "Stopped $stoppedCount apps running in the background"

        setupBackButton()
        initializeNativeAdView()
        loadNativeAd()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun initializeNativeAdView() {
        // Pre-inflate the native ad view structure so the layout is complete from the start
        nativeAdView = layoutInflater.inflate(R.layout.native_ad_layout, binding.nativeAdContainer, false) as NativeAdView
        binding.nativeAdContainer.addView(nativeAdView)
        
        val adBinding = NativeAdLayoutBinding.bind(nativeAdView!!)
        
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
        
        if (ad.images.isNotEmpty() && ad.images[0].drawable != null) {
            adBinding.nativeAdMedia.setImageDrawable(ad.images[0].drawable)
            adBinding.nativeAdMedia.visibility = android.view.View.VISIBLE
        } else {
            adBinding.nativeAdMedia.visibility = android.view.View.GONE
        }
        
        adView.setNativeAd(ad)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
    }
}

