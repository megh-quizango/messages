package com.text.messages.sms.messanger.ui.manageapps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityManageAppsDetailBinding
import com.text.messages.sms.messanger.databinding.NativeManageAppAdLayoutBinding
import com.text.messages.sms.messanger.util.AdLoadingShimmerHelper
import com.text.messages.sms.messanger.util.AdConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper
import com.text.messages.sms.messanger.util.NextGenAdHelper
import com.text.messages.sms.messanger.util.RemoteConfigHelper
import com.text.messages.sms.messanger.util.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageAppsDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityManageAppsDetailBinding
    private lateinit var adapter: BackgroundAppsAdapter
    private var nativeAd: NativeAd? = null
    private var nativeAdView: NativeAdView? = null
    private var adaptiveBannerView: AdView? = null
    private val backgroundApps = mutableListOf<BackgroundApp>()
    private val stoppedApps = mutableSetOf<String>()
    private var discoveredAppsCount = 0

    data class BackgroundApp(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val isStopped: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityManageAppsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        applyManageAppsChrome()
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get RAM used percentage from intent
        val ramUsedPercentage = intent.getIntExtra("ram_used_percentage", 56)
        binding.textRamPercent.text = ramUsedPercentage.toString()

        setupBackButton()
        setupRecyclerView()
        setupDoneButton()
        initializeNativeAdView()
        loadNativeAd()
        fetchBackgroundApps()
        
        // Keep the manage-apps flow on the reference blue, independent of the selected app theme.
        binding.buttonDone.backgroundTintList = null
        binding.buttonDone.backgroundTintList = ColorStateList.valueOf(MANAGE_APPS_BLUE)
        
        // Apply theme after views are laid out
        binding.root.post {
            ThemeManager.applyTheme(this, binding.root)
            applyManageAppsChrome()
            binding.root.post { applyManageAppsChrome() }
        }
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = BackgroundAppsAdapter(
            onStopClick = { packageName ->
                stopBackgroundApp(packageName)
            }
        )
        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = adapter
    }

    private fun setupDoneButton() {
        binding.buttonDone.setOnClickListener {
            val intent = Intent(this, ManageAppsCompleteActivity::class.java)
            intent.putExtra("stopped_count", stoppedApps.size)
            startActivity(intent)
            finish()
        }
    }

    private fun fetchBackgroundApps() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.recyclerViewApps.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val apps = getRunningBackgroundApps()
            withContext(Dispatchers.Main) {
                backgroundApps.clear()
                backgroundApps.addAll(apps)
                discoveredAppsCount = apps.size
                
                binding.progressIndicator.visibility = View.GONE
                binding.recyclerViewApps.visibility = View.VISIBLE
                
                updateAppsTotal()
                adapter.submitList(backgroundApps.map { 
                    it.copy(isStopped = stoppedApps.contains(it.packageName))
                })
            }
        }
    }

    private fun getRunningBackgroundApps(): List<BackgroundApp> {
        val apps = mutableListOf<BackgroundApp>()
        val packageManager = packageManager
        
        try {
            // Get all installed apps on the device
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            installedApps.forEach { appInfo ->
                // Include all apps (both user-installed and system apps)
                // Filter out system apps that shouldn't be shown, but keep updated system apps
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    
                    val appName = if (appInfo.packageName == this.packageName) {
                        getString(R.string.settings_app_name)
                    } else {
                        packageManager.getApplicationLabel(appInfo).toString()
                    }
                    val icon = packageManager.getApplicationIcon(appInfo)
                    
                    apps.add(BackgroundApp(
                        packageName = appInfo.packageName,
                        appName = appName,
                        icon = icon
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return apps.sortedBy { it.appName }
    }

    private fun stopBackgroundApp(packageName: String) {
        if (stoppedApps.contains(packageName)) return

        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stoppedApps.add(packageName)
        backgroundApps.removeAll { it.packageName == packageName }
        adapter.submitList(backgroundApps.toList())
        updateAppsTotal()
    }

    private fun updateAppsTotal() {
        val total = if (discoveredAppsCount > 0) discoveredAppsCount else backgroundApps.size
        binding.textAppsTotal.text = getString(R.string.manage_apps_total_apps_format, total)
    }

    private fun applyManageAppsChrome() {
        val white = Color.WHITE
        val blue = MANAGE_APPS_BLUE
        binding.root.setBackgroundColor(MANAGE_APPS_HEADER_BLUE)
        binding.headerContainer.setBackgroundColor(MANAGE_APPS_HEADER_BLUE)
        binding.barContainer.setBackgroundColor(MANAGE_APPS_CAPTION_OVERLAY)
        binding.textHeading.setTextColor(white)
        binding.textRamPercent.setTextColor(white)
        binding.textRamUsed.setTextColor(white)
        binding.textBackgroundApps.setTextColor(white)
        binding.textAppsTotal.setTextColor(white)
        binding.buttonBack.imageTintList = ColorStateList.valueOf(white)
        binding.buttonBack.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        binding.buttonDone.backgroundTintList = ColorStateList.valueOf(blue)
        binding.buttonDone.setTextColor(white)
    }

    private fun initializeNativeAdView() {
        showManageAppsAdLoading()

        // Pre-inflate the native ad view structure so the layout is complete from the start
        nativeAdView = layoutInflater.inflate(R.layout.native_manage_app_ad_layout, binding.nativeAdContainer, false) as NativeAdView
        nativeAdView!!.visibility = View.GONE
        binding.nativeAdContainer.addView(nativeAdView)
        
        val adBinding = NativeManageAppAdLayoutBinding.bind(nativeAdView!!)
        
        // Apply theme colors to native ad
        val blue = MANAGE_APPS_BLUE
        
        // Apply theme to entire ad view (will handle background)
        ThemeManager.applyTheme(this, nativeAdView!!)
        
        // Match the fixed-blue reference ad treatment.
        val adLabel = nativeAdView!!.findViewById<android.widget.TextView>(R.id.nativeAdLabel)
        adLabel?.setBackgroundColor(blue)
        
        adBinding.nativeAdCallToAction.backgroundTintList = ColorStateList.valueOf(blue)
        
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
        NextGenAdHelper.loadNative(
            adUnitId = nativeAdUnitId,
            preferLandscape = true,
            onLoaded = { ad ->
                ad.adEventCallback = object : NativeAdEventCallback {
                    override fun onAdClicked() {
                        AnalyticsHelper.logAdClick("native", nativeAdUnitId)
                    }

                    override fun onAdImpression() {
                        AnalyticsHelper.logAdImpression("native", nativeAdUnitId)
                    }
                }
                nativeAd = ad
                adaptiveBannerView?.visibility = View.GONE
                populateNativeAdView(ad)
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, true)
            },
            onFailed = { loadAdError ->
                AnalyticsHelper.logAdLoad("native", nativeAdUnitId, false)
                AnalyticsHelper.logAdError("native", nativeAdUnitId, loadAdError.code.toString())
                loadAdaptiveBanner()
            }
        )
    }

    private fun showManageAppsAdLoading() {
        nativeAdView?.visibility = View.GONE
        adaptiveBannerView?.visibility = View.GONE
        AdLoadingShimmerHelper.showNativeLoading(binding.nativeAdContainer, nativeAdView)
    }

    private fun loadAdaptiveBanner() {
        val bannerAdUnitId = AdConfig.resolveManageAppsAdaptiveBannerAdUnitId(this)
        if (bannerAdUnitId.isBlank()) {
            nativeAdView?.visibility = View.GONE
            adaptiveBannerView?.visibility = View.GONE
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
            nativeAdView?.visibility = View.GONE

            val adWidthPx = binding.nativeAdContainer.width
                .takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - binding.nativeAdContainer.paddingLeft - binding.nativeAdContainer.paddingRight)
            val adSize = getManageAppsAdaptiveAdSize(adWidthPx)
            val slotHeightPx = measureManageAppsAdSlotHeightPx(adWidthPx)
            val bannerView = getOrCreateAdaptiveBannerView(bannerAdUnitId)
            bannerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                slotHeightPx
            )
            bannerView.visibility = View.GONE
            NextGenAdHelper.loadBanner(
                activity = this,
                adView = bannerView,
                adUnitId = bannerAdUnitId,
                adSize = adSize,
                onLoaded = { bannerAd ->
                    bannerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        bannerAd.getAdSize().getHeightInPixels(this@ManageAppsDetailActivity)
                    )
                    nativeAdView?.visibility = View.GONE
                    AdLoadingShimmerHelper.showNativeContent(binding.nativeAdContainer, bannerView)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, true)
                },
                onFailed = { loadAdError ->
                    nativeAdView?.visibility = View.GONE
                    bannerView.visibility = View.GONE
                    AdLoadingShimmerHelper.hideNative(binding.nativeAdContainer, bannerView)
                    AnalyticsHelper.logAdLoad("banner", bannerAdUnitId, false)
                    AnalyticsHelper.logAdError("banner", bannerAdUnitId, loadAdError.code.toString())
                },
                onClicked = {
                    AnalyticsHelper.logAdClick("banner", bannerAdUnitId)
                },
                onImpression = {
                    AnalyticsHelper.logAdImpression("banner", bannerAdUnitId)
                }
            )
        }
    }

    private fun getOrCreateAdaptiveBannerView(adUnitId: String): AdView {
        val existing = adaptiveBannerView
        if (existing != null && existing.getTag(R.id.ad_unit_id_tag) == adUnitId) {
            return existing
        }

        existing?.let {
            binding.nativeAdContainer.removeView(it)
            it.destroy()
        }

        return AdView(this).apply {
            setTag(R.id.ad_unit_id_tag, adUnitId)
            visibility = View.GONE
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
        val widthSpec = View.MeasureSpec.makeMeasureSpec(adWidthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        adView.measure(widthSpec, heightSpec)
        return adView.measuredHeight.coerceAtLeast((220 * resources.displayMetrics.density).toInt())
    }
    
    private fun populateNativeAdView(ad: NativeAd) {
        // Use the pre-inflated view instead of creating a new one
        val adView = nativeAdView ?: return
        val adBinding = NativeManageAppAdLayoutBinding.bind(adView)
        
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
            adBinding.nativeAdIcon.visibility = View.VISIBLE
        } else {
            adBinding.nativeAdIcon.visibility = View.GONE
        }
        
        val mediaContent = ad.mediaContent
        adBinding.nativeAdMedia.visibility = if (mediaContent == null) View.GONE else View.VISIBLE
        adBinding.nativeAdMedia.mediaContent = mediaContent
        
        adView.registerNativeAd(ad, adBinding.nativeAdMedia)
        AdLoadingShimmerHelper.showNativeContent(binding.nativeAdContainer, adView)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        adaptiveBannerView?.destroy()
        super.onDestroy()
    }

    companion object {
        private val MANAGE_APPS_HEADER_BLUE = Color.parseColor("#2569F1")
        private val MANAGE_APPS_CAPTION_OVERLAY = Color.parseColor("#33020202")
        private val MANAGE_APPS_BLUE = Color.parseColor("#0C56CF")
    }
}

