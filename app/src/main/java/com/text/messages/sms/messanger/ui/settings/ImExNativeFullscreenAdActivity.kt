package com.text.messages.sms.messanger.ui.settings

import android.app.Activity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.nativead.NativeAd
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityLanguageNativeFullscreenAdBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.ImExTransitionAdManager

class ImExNativeFullscreenAdActivity : BaseActivity() {

    private lateinit var binding: ActivityLanguageNativeFullscreenAdBinding
    private var nativeAd: NativeAd? = null
    private var hasNavigated = false

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

        nativeAd = ImExTransitionAdManager.consumeNativeFullscreenAd()
        if (nativeAd == null) {
            finishAd()
            return
        }

        setupDismissActions()
        bindNativeAd(nativeAd!!)
    }

    private fun setupDismissActions() {
        binding.buttonCloseAd.setOnClickListener {
            finishAd()
        }
        binding.textContinue.setOnClickListener {
            finishAd()
        }
    }

    private fun finishAd() {
        if (hasNavigated) {
            return
        }
        hasNavigated = true
        setResult(Activity.RESULT_OK)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
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

        binding.adMediaView.mediaContent = ad.mediaContent
        binding.adCallToActionView.text = ad.callToAction ?: getString(R.string.open)

        nativeAdView.setNativeAd(ad)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        nativeAd = null
        ImExTransitionAdManager.reloadAfterNativeDismiss(applicationContext)
        super.onDestroy()
    }
}
