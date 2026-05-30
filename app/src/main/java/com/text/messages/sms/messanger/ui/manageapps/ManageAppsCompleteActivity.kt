package com.text.messages.sms.messanger.ui.manageapps

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityManageAppsCompleteBinding
import com.text.messages.sms.messanger.databinding.NativeAdLayoutBinding
import com.text.messages.sms.messanger.util.AdLoadingShimmerHelper
import com.text.messages.sms.messanger.util.AdConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.RemoteConfigHelper
import com.text.messages.sms.messanger.util.ThemeManager

class ManageAppsCompleteActivity : BaseActivity() {

    private lateinit var binding: ActivityManageAppsCompleteBinding
    private var nativeAd: NativeAd? = null
    private var nativeAdView: NativeAdView? = null
    private var adaptiveBannerView: AdView? = null

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
        binding.textStoppedApps.text = resources.getQuantityString(
            R.plurals.manage_apps_stopped_apps_summary,
            stoppedCount,
            stoppedCount
        )

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
        showManageAppsAdLoading()

        // Pre-inflate the native ad view structure so the layout is complete from the start
        nativeAdView = layoutInflater.inflate(R.layout.native_ad_layout, binding.nativeAdContainer, false) as NativeAdView
        nativeAdView!!.visibility = android.view.View.GONE
        binding.nativeAdContainer.addView(nativeAdView)
        
        val adBinding = NativeAdLayoutBinding.bind(nativeAdView!!)
        
        // Register views with NativeAdView (will be populated when ad loads)
        nativeAdView!!.headlineView = adBinding.nativeAdHeadline
        nativeAdView!!.bodyView = adBinding.nativeAdBody
        nativeAdView!!.callToActionView = adBinding.nativeAdCallToAction
        nativeAdView!!.iconView = adBinding.nativeAdIcon
    }
    
    private fun loadNativeAd() {
        if (RemoteConfigHelper.shouldUseManageAppsAdaptiveBannerOnly()) {
            loadAdaptiveBanner()
            return
        }

        val nativeAdUnitId = AdConfig.resolveNativeAdUnitId(this)
        if (nativeAdUnitId.isBlank()) {
            loadAdaptiveBanner()
            return
        }
        showManageAppsAdLoading()
        val adLoader = AdLoader.Builder(this, nativeAdUnitId)
            .forNativeAd { ad ->
                nativeAd = ad
                adaptiveBannerView?.visibility = android.view.View.GONE
                populateNativeAdView(ad)
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, true)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
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

    private fun showManageAppsAdLoading() {
        nativeAdView?.visibility = android.view.View.GONE
        adaptiveBannerView?.visibility = android.view.View.GONE
        AdLoadingShimmerHelper.showNativeLoading(binding.nativeAdContainer, nativeAdView)
    }

    private fun loadAdaptiveBanner() {
        val bannerAdUnitId = AdConfig.resolveManageAppsAdaptiveBannerAdUnitId(this)
        if (bannerAdUnitId.isBlank()) {
            nativeAdView?.visibility = android.view.View.GONE
            adaptiveBannerView?.visibility = android.view.View.GONE
            AdLoadingShimmerHelper.hideNative(binding.nativeAdContainer, nativeAdView)
            return
        }

        showManageAppsAdLoading()
        binding.nativeAdContainer.post {
            if (isFinishing || isDestroyed) {
                return@post
            }

            nativeAd?.destroy()
            nativeAd = null
            nativeAdView?.visibility = android.view.View.GONE

            val adWidthPx = binding.nativeAdContainer.width
                .takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - binding.nativeAdContainer.paddingLeft - binding.nativeAdContainer.paddingRight)
            val adSize = getManageAppsAdaptiveAdSize(adWidthPx)
            val slotHeightPx = measureManageAppsAdSlotHeightPx(adWidthPx)
            val bannerView = getOrCreateAdaptiveBannerView(bannerAdUnitId)
            bannerView.setAdSize(adSize)
            bannerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                slotHeightPx
            )
            bannerView.visibility = android.view.View.GONE
            bannerView.adListener = object : com.google.android.gms.ads.AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    bannerView.adSize?.let { loadedAdSize ->
                        bannerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            loadedAdSize.getHeightInPixels(this@ManageAppsCompleteActivity)
                        )
                    }
                    nativeAdView?.visibility = android.view.View.GONE
                    AdLoadingShimmerHelper.showNativeContent(binding.nativeAdContainer, bannerView)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, true)
                }

                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    nativeAdView?.visibility = android.view.View.GONE
                    bannerView.visibility = android.view.View.GONE
                    AdLoadingShimmerHelper.hideNative(binding.nativeAdContainer, bannerView)
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
            binding.nativeAdContainer.removeView(it)
            it.destroy()
        }

        return AdView(this).apply {
            this.adUnitId = adUnitId
            visibility = android.view.View.GONE
            binding.nativeAdContainer.addView(
                this,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            adaptiveBannerView = this
        }
    }

    private fun getManageAppsAdaptiveAdSize(adWidthPx: Int): AdSize {
        val adWidthDp = (adWidthPx / resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val slotHeightDp = (measureManageAppsAdSlotHeightPx(adWidthPx) / resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(50)
        return AdSize.getInlineAdaptiveBannerAdSize(adWidthDp, slotHeightDp)
    }

    private fun measureManageAppsAdSlotHeightPx(adWidthPx: Int): Int {
        val adView = nativeAdView ?: return (220 * resources.displayMetrics.density).toInt()
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(adWidthPx, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        adView.measure(widthSpec, heightSpec)
        return adView.measuredHeight.coerceAtLeast((220 * resources.displayMetrics.density).toInt())
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
        AdLoadingShimmerHelper.showNativeContent(binding.nativeAdContainer, adView)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        adaptiveBannerView?.destroy()
        super.onDestroy()
    }
}

