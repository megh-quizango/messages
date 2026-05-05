package com.text.messages.sms.messanger.ui.launcher

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.text.messages.sms.messanger.databinding.ActivityProxyBinding
import com.text.messages.sms.messanger.ui.base.BaseActivity

class ProxyActivity : BaseActivity() {

    private lateinit var binding: ActivityProxyBinding

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || HomeRoleHelper.isHomeRoleHeld(this)) {
            launchHomeAndFinish()
        } else {
            binding.statusText.text = "Not set as default launcher yet."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (HomeRoleHelper.isHomeRoleHeld(this)) {
            launchHomeAndFinish()
            return
        }

        binding = ActivityProxyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.setDefaultButton.setOnClickListener { requestHomeRole() }
        binding.openSettingsButton.setOnClickListener { openHomeSettings() }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && HomeRoleHelper.isHomeRoleHeld(this)) {
            launchHomeAndFinish()
        }
    }

    private fun requestHomeRole() {
        val intent = HomeRoleHelper.createRequestRoleIntent(this)
        if (intent != null) {
            requestRoleLauncher.launch(intent)
        } else {
            openHomeSettings()
        }
    }

    private fun openHomeSettings() {
        val intent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> Intent(Settings.ACTION_HOME_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        runCatching { startActivity(intent) }
    }

    private fun launchHomeAndFinish() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }
}

