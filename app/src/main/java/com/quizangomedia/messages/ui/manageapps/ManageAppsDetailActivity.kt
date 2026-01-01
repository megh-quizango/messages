package com.quizangomedia.messages.ui.manageapps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
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
import com.quizangomedia.messages.databinding.ActivityManageAppsDetailBinding
import com.quizangomedia.messages.databinding.NativeAdLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageAppsDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageAppsDetailBinding
    private lateinit var adapter: BackgroundAppsAdapter
    private var nativeAd: NativeAd? = null
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
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get RAM used percentage from intent
        val ramUsedPercentage = intent.getIntExtra("ram_used_percentage", 56)
        binding.textRamUsed.text = "$ramUsedPercentage% Used"

        setupBackButton()
        setupRecyclerView()
        setupDoneButton()
        loadNativeAd()
        fetchBackgroundApps()
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
                
                binding.textAppsTotal.text = "Total: ${apps.size}"
                adapter.submitList(backgroundApps.map { 
                    it.copy(isStopped = stoppedApps.contains(it.packageName))
                })
            }
        }
    }

    private fun getRunningBackgroundApps(): List<BackgroundApp> {
        val apps = mutableListOf<BackgroundApp>()
        val packageManager = packageManager
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        try {
            val runningProcesses = activityManager.runningAppProcesses ?: return emptyList()
            val runningPackages = runningProcesses.mapNotNull { it.pkgList.firstOrNull() }.toSet()
            
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            installedApps.forEach { appInfo ->
                if (runningPackages.contains(appInfo.packageName)) {
                    // Skip system apps if needed
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return apps.sortedBy { it.appName }
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

