package com.text.messages.sms.messanger.ui.manageapps

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityManageAppsBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.AnalyticsHelper
import java.util.Locale

class ManageAppsActivity : BaseActivity() {

    private lateinit var binding: ActivityManageAppsBinding
    private val handler = Handler(Looper.getMainLooper())
    private var progressAnimator: ValueAnimator? = null
    private var didOpenDetail = false

    private val openDetailRunnable = Runnable { openDetailScreen() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("ManageAppsActivity", "ManageAppsActivity")

        enableEdgeToEdge()
        binding = ActivityManageAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyReferenceChrome()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val usedPercent = getRamUsedPercentage()
        val availablePercent = getAvailableSpacePercentage()
        binding.textRamPercentage.text = String.format(Locale.US, "%.2f%%", availablePercent)
        binding.progressBarRam.progress = availablePercent.toInt().coerceIn(0, 100)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonManage.setOnClickListener { openDetailScreen() }
        startLoadingAnimation(usedPercent)
    }

    override fun onResume() {
        super.onResume()
        applyReferenceChrome()
    }

    override fun onDestroy() {
        handler.removeCallbacks(openDetailRunnable)
        progressAnimator?.cancel()
        super.onDestroy()
    }

    private fun startLoadingAnimation(usedPercent: Int) {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofInt(0, usedPercent.coerceAtLeast(41)).apply {
            duration = 1800L
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.textLoadingPercentage.text = getString(R.string.manage_apps_percentage_format, value)
            }
            start()
        }
        binding.imageLoading.animate()
            .rotationBy(360f)
            .setDuration(1800L)
            .setInterpolator(LinearInterpolator())
            .start()
        handler.postDelayed(openDetailRunnable, 1900L)
    }

    private fun openDetailScreen() {
        if (didOpenDetail || isFinishing || isDestroyed) return
        didOpenDetail = true
        progressAnimator?.cancel()
        startActivity(
            Intent(this, ManageAppsDetailActivity::class.java).apply {
                putExtra("ram_used_percentage", getRamUsedPercentage())
            }
        )
        finish()
        overridePendingTransition(0, 0)
    }

    private fun applyReferenceChrome() {
        val light = Color.parseColor("#D8D8D8")
        binding.root.setBackgroundResource(R.drawable.gradient_animation)
        binding.textHeading.setTextColor(light)
        binding.textRamPercentage.setTextColor(light)
        binding.textAvailableSpace.setTextColor(light)
        binding.textLoadingPercentage.setTextColor(light)
        binding.textManagingApps.setTextColor(light)
        binding.buttonBack.imageTintList = ColorStateList.valueOf(light)
        binding.buttonBack.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        window.statusBarColor = Color.parseColor("#2569F1")
        window.navigationBarColor = Color.parseColor("#E0E0E0")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    }

    private fun getRamUsedPercentage(): Int {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val usedRam = memInfo.totalMem - memInfo.availMem
            ((usedRam.toFloat() / memInfo.totalMem.toFloat()) * 100).toInt()
        } catch (e: Exception) {
            56
        }
    }

    private fun getAvailableSpacePercentage(): Float {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            ((memInfo.availMem.toFloat() / memInfo.totalMem.toFloat()) * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            92.41f
        }
    }
}
