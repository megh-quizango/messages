package com.quizangomedia.messages.ui.manageapps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityManageAppsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageAppsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityManageAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupRamDisplay()
        setupBannerAd()
        startLoadingApps()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRamDisplay() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val totalRam = memInfo.totalMem
        val availableRam = memInfo.availMem
        val usedRam = totalRam - availableRam
        val availablePercentage = ((availableRam.toFloat() / totalRam.toFloat()) * 100).toInt()
        
        binding.textRamPercentage.text = "$availablePercentage%"
        binding.progressBarRam.progress = availablePercentage
    }

    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }

    private fun startLoadingApps() {
        // Show loading indicator
        binding.imageLoading.visibility = View.VISIBLE
        binding.textLoadingPercentage.visibility = View.VISIBLE
        binding.imagePhone.visibility = View.GONE
        binding.buttonManage.visibility = View.GONE
        
        // Start rotation animation
        startRotationAnimation()
        
        // Simulate app loading with progress updates
        loadAppsWithProgress()
    }

    private fun startRotationAnimation() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.anim_rotate)
        binding.imageLoading.startAnimation(animation)
    }

    private fun stopRotationAnimation() {
        binding.imageLoading.clearAnimation()
    }

    private fun loadAppsWithProgress() {
        var progress = 0
        
        val handler = Handler(Looper.getMainLooper())
        val updateProgress = object : Runnable {
            override fun run() {
                progress += 2
                if (progress <= 100) {
                    binding.textLoadingPercentage.text = "$progress%"
                    handler.postDelayed(this, 50) // Update every 50ms
                } else {
                    // Loading complete
                    finishLoading()
                }
            }
        }
        handler.post(updateProgress)
        
        // Also fetch apps in background
        CoroutineScope(Dispatchers.IO).launch {
            fetchInstalledApps()
        }
    }

    private suspend fun fetchInstalledApps() {
        try {
            val packageManager = packageManager
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            // Filter to only user-installed apps (optional)
            val userApps = apps.filter { 
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
            
            // Simulate processing time
            withContext(Dispatchers.IO) {
                Thread.sleep(2000) // Give time for progress animation
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun finishLoading() {
        stopRotationAnimation()
        
        // Hide loading indicator
        binding.imageLoading.visibility = View.GONE
        binding.textLoadingPercentage.visibility = View.GONE
        
        // Show phone image and manage button
        binding.imagePhone.visibility = View.VISIBLE
        binding.buttonManage.visibility = View.VISIBLE
        
        // Setup manage button click
        binding.buttonManage.setOnClickListener {
            val intent = Intent(this, ManageAppsDetailActivity::class.java)
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRam = memInfo.totalMem
            val availableRam = memInfo.availMem
            val usedRam = totalRam - availableRam
            val usedPercentage = ((usedRam.toFloat() / totalRam.toFloat()) * 100).toInt()
            intent.putExtra("ram_used_percentage", usedPercentage)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRotationAnimation()
    }
}
