package com.text.messages.sms.messanger.ui.manageapps

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private fun applyManageAppsChrome() {
        binding.textHeading.setTextColor(Color.WHITE)
        binding.finalTextTop.setTextColor(Color.WHITE)
        binding.textStoppedApps.setTextColor(Color.WHITE)
        binding.buttonBack.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.buttonBack.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }
}
