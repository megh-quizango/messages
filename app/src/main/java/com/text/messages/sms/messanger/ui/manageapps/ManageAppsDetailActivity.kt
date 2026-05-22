package com.text.messages.sms.messanger.ui.manageapps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityManageAppsDetailBinding
import com.text.messages.sms.messanger.databinding.NativeAdLayoutBinding
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
    private val backgroundApps = mutableListOf<BackgroundApp>()
    private val stoppedApps = mutableSetOf<String>()

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
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get RAM used percentage from intent
        val ramUsedPercentage = intent.getIntExtra("ram_used_percentage", 56)
        binding.textRamUsed.text = getString(R.string.manage_apps_ram_used_format, ramUsedPercentage)

        setupBackButton()
        setupRecyclerView()
        setupDoneButton()
        initializeNativeAdView()
        loadNativeAd()
        fetchBackgroundApps()
        
        // Apply theme to done button - set backgroundTint to null and apply theme color directly
        binding.buttonDone.backgroundTintList = null
        val themeColor = ThemeManager.getThemeColor(this)
        binding.buttonDone.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Apply theme after views are laid out
        binding.root.post {
            ThemeManager.applyTheme(this, binding.root)
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
                stoppedApps.add(packageName)
                // Update the list with stopped status
                val updatedList = backgroundApps.map { 
                    it.copy(isStopped = stoppedApps.contains(it.packageName))
                }
                adapter.submitList(updatedList)
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
                
                binding.progressIndicator.visibility = View.GONE
                binding.recyclerViewApps.visibility = View.VISIBLE
                
                binding.textAppsTotal.text = getString(R.string.manage_apps_total_apps_format, apps.size)
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
                    
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
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

    private fun initializeNativeAdView() {
        // Pre-inflate the native ad view structure so the layout is complete from the start
        nativeAdView = layoutInflater.inflate(R.layout.native_ad_layout, binding.nativeAdContainer, false) as NativeAdView
        binding.nativeAdContainer.addView(nativeAdView)
        
        val adBinding = NativeAdLayoutBinding.bind(nativeAdView!!)
        
        // Apply theme colors to native ad
        val themeColor = ThemeManager.getThemeColor(this)
        
        // Apply theme to entire ad view (will handle background)
        ThemeManager.applyTheme(this, nativeAdView!!)
        
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
        if (nativeAdUnitId.isBlank()) {
            binding.nativeAdContainer.visibility = View.GONE
            return
        }
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
        
        if (ad.images.isNotEmpty() && ad.images[0].drawable != null) {
            adBinding.nativeAdMedia.setImageDrawable(ad.images[0].drawable)
            adBinding.nativeAdMedia.visibility = View.VISIBLE
        } else {
            adBinding.nativeAdMedia.visibility = View.GONE
        }
        
        adView.setNativeAd(ad)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
    }
}

