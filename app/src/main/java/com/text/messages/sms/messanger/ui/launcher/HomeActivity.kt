package com.text.messages.sms.messanger.ui.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.viewpager2.widget.ViewPager2
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.ui.base.BaseActivity

class HomeActivity : BaseActivity(), HomeUiHost {

    private var appDrawer: AppDrawerBottomSheetFragment? = null
    private lateinit var pager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        pager = findViewById(R.id.homePager)
        pager.adapter = HomePagerAdapter(this)
        pager.currentItem = HomePagerAdapter.PAGE_WORKSPACE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    collapseAppDrawerIfOpen() -> Unit
                    pager.currentItem != HomePagerAdapter.PAGE_WORKSPACE ->
                        pager.setCurrentItem(HomePagerAdapter.PAGE_WORKSPACE, true)
                    else -> Unit
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        if (!HomeRoleHelper.isHomeRoleHeld(this)) {
            startActivity(
                Intent(this, ProxyActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            )
            finish()
            return
        }

        maybeTriggerAdCycle()
        maybeRunTutorialCheck()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        collapseOverlaysAndReset()
    }

    override fun showAppDrawer() {
        if (appDrawer?.isAdded == true) return
        appDrawer = AppDrawerBottomSheetFragment.newInstance().also {
            it.show(supportFragmentManager, "app_drawer")
        }
    }

    override fun collapseOverlaysAndReset() {
        collapseAppDrawerIfOpen()
        pager.setCurrentItem(HomePagerAdapter.PAGE_WORKSPACE, true)
        supportFragmentManager.fragments
            .firstOrNull { it is HomeWorkspaceFragment }
            ?.let { (it as HomeWorkspaceFragment).scrollToFirstPage() }
    }

    private fun collapseAppDrawerIfOpen(): Boolean {
        val drawer = appDrawer
        return if (drawer != null && drawer.isAdded) {
            drawer.dismissAllowingStateLoss()
            true
        } else {
            false
        }
    }

    private fun maybeTriggerAdCycle() {
        // Hook point for an "ad cycle" check if/when launcher integrates ads.
    }

    private fun maybeRunTutorialCheck() {
        // Hook point for a first-run tutorial check if/when needed for launcher UX.
    }
}
