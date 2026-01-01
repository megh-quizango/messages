package com.quizangomedia.messages.ui.manageapps

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityManageAppsCompleteBinding
import com.quizangomedia.messages.databinding.NativeAdLayoutBinding

class ManageAppsCompleteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageAppsCompleteBinding
    private var nativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityManageAppsCompleteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val stoppedCount = intent.getIntExtra("stopped_count", 0)
        binding.textStoppedApps.text = "Stopped $stoppedCount apps running in the background"

        setupBackButton()
        loadNativeAd()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
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
        val adView = layoutInflater.inflate(R.layout.native_ad_layout, binding.nativeAdContainer, false) as NativeAdView
        binding.nativeAdContainer.removeAllViews()
        binding.nativeAdContainer.addView(adView)
        
        val adBinding = NativeAdLayoutBinding.bind(adView)
        
        adView.headlineView = adBinding.nativeAdHeadline
        adView.bodyView = adBinding.nativeAdBody
        adView.callToActionView = adBinding.nativeAdCallToAction
        adView.iconView = adBinding.nativeAdIcon
        
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

