package com.text.messages.sms.messanger.ui.language

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.text.messages.sms.messanger.databinding.ActivityLanguageNativeFullscreenAdBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.LanguageTransitionAdManager

class LanguageNativeFullscreenAdActivity : BaseActivity() {

    private lateinit var binding: ActivityLanguageNativeFullscreenAdBinding
    private var nativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLanguageNativeFullscreenAdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        nativeAd = LanguageTransitionAdManager.consumeNativeFullscreenAd()
        if (nativeAd == null) {
            finish()
            return
        }

        setupDismissActions()
        bindNativeAd(nativeAd!!)
    }

    private fun setupDismissActions() {
        binding.buttonCloseAd.setOnClickListener {
            finish()
        }
        binding.textContinue.setOnClickListener {
            finish()
        }
    }

    private fun bindNativeAd(ad: NativeAd) {
        val nativeAdView = binding.nativeFullscreenAdView
        nativeAdView.mediaView = binding.adMediaView
        nativeAdView.headlineView = binding.adHeadlineView
        nativeAdView.bodyView = binding.adBodyView
        nativeAdView.iconView = binding.adIconView
        nativeAdView.callToActionView = binding.adCallToActionView

        binding.adHeadlineView.text = ad.headline ?: ""
        binding.adBodyView.text = ad.body ?: ""

        val icon = ad.icon
        if (icon != null) {
            binding.adIconView.setImageDrawable(icon.drawable)
            binding.adIconView.visibility = android.view.View.VISIBLE
        } else {
            binding.adIconView.visibility = android.view.View.GONE
        }

        val mediaView = binding.adMediaView
        mediaView.mediaContent = ad.mediaContent

        binding.adCallToActionView.text = ad.callToAction ?: getString(com.text.messages.sms.messanger.R.string.open)

        nativeAdView.setNativeAd(ad)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        nativeAd = null
        LanguageTransitionAdManager.reloadAfterNativeDismiss(applicationContext)
        super.onDestroy()
    }
}
