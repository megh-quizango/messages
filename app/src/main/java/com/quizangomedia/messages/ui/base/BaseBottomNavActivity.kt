package com.quizangomedia.messages.ui.base

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.util.ThemeManager

abstract class BaseBottomNavActivity : AppCompatActivity() {
    
    protected lateinit var bottomNavigationView: BottomNavigationView
    protected lateinit var adViewBanner: com.google.android.gms.ads.AdView
    
    private var isSettingSelectedItem = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set navigation bar color to white with black icons
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = getColor(android.R.color.white)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                var flags = window.decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = flags
            }
        }
        
        setupBottomNavPadding()
        setupWindowInsets()
        setupBottomNavigation()
        setupBannerAd()
        
        // Apply theme after views are created
        findViewById<View>(android.R.id.content)?.let {
            ThemeManager.applyTheme(this, it)
        }
    }
    
    private fun setupBottomNavPadding() {
        // This will be called after layout is inflated
        // We'll set it up in onResume or after binding
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    
    protected fun setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        
        // Remove extra padding from internal menu container
        bottomNavigationView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                bottomNavigationView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                val topPadding = bottomNavigationView.paddingTop
                val bottomPadding = bottomNavigationView.paddingBottom
                bottomNavigationView.setPadding(0, topPadding, 0, bottomPadding)
                bottomNavigationView.minimumHeight = 0
                
                val menuView = bottomNavigationView.getChildAt(0) as? android.view.ViewGroup
                menuView?.let {
                    it.setPadding(0, 0, 0, 0)
                    it.minimumHeight = 0
                    
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        child?.let { item ->
                            if (item is android.view.ViewGroup) {
                                item.setPadding(item.paddingLeft, 0, item.paddingRight, 0)
                                item.minimumHeight = 0
                            }
                        }
                    }
                }
            }
        })
        
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView) { view, insets ->
            insets
        }
        
        bottomNavigationView.setOnItemSelectedListener { item ->
            if (isSettingSelectedItem) {
                return@setOnItemSelectedListener true
            }
            
            handleNavigation(item.itemId)
            true
        }
    }
    
    protected fun setSelectedNavigationItem(itemId: Int) {
        isSettingSelectedItem = true
        bottomNavigationView.selectedItemId = itemId
        isSettingSelectedItem = false
    }
    
    protected abstract fun handleNavigation(itemId: Int)
    
    private fun setupBannerAd() {
        adViewBanner = findViewById(R.id.adViewBanner)
        val adRequest = AdRequest.Builder().build()
        adViewBanner.loadAd(adRequest)
    }
    
    override fun onResume() {
        super.onResume()
        adViewBanner.resume()
    }
    
    override fun onPause() {
        super.onPause()
        adViewBanner.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        adViewBanner.destroy()
    }
}

