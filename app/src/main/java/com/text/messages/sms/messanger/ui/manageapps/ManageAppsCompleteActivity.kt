package com.text.messages.sms.messanger.ui.manageapps

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityManageAppsCompleteBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.ThemeManager

class ManageAppsCompleteActivity : BaseActivity() {

    private lateinit var binding: ActivityManageAppsCompleteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityManageAppsCompleteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupNavigationBar(this)

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

        binding.buttonBack.setOnClickListener { finish() }
        applyManageAppsChrome()
    }

    override fun onResume() {
        super.onResume()
        applyManageAppsChrome()
    }

    private fun applyManageAppsChrome() {
        val light = Color.parseColor("#D8D8D8")
        binding.root.setBackgroundResource(R.drawable.gradient_animation)
        binding.textHeading.setTextColor(light)
        binding.finalTextTop.setTextColor(light)
        binding.textStoppedApps.setTextColor(light)
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
}
