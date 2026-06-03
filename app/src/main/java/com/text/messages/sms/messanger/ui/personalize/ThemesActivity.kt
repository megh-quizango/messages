package com.text.messages.sms.messanger.ui.personalize

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivityThemesBinding
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.ThemeTransitionAdManager

class ThemesActivity : BaseActivity() {

    private lateinit var binding: ActivityThemesBinding
    private var selectedCardId: Int? = null
    private var selectedIconId: Int? = null
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var themeUpdateCallback: ((Context, View) -> Unit)? = null
    
    // Theme colors mapping - will be initialized after binding
    private val themeColors: Map<Int, String> by lazy {
        mapOf(
            binding.cardThemeBlue.id to "#2454B8",
            binding.cardThemeGreen.id to "#007F18",
            binding.cardThemePink.id to "#BD4F74",
            binding.cardThemeYellow.id to "#C77900",
            binding.cardThemeOral.id to "#BB594B",
            binding.cardThemeNavyBlue.id to "#273266",
            binding.cardThemeCeruleanBlue.id to "#0B6F99",
            binding.cardThemeBlackBlue.id to "#000000",
            binding.cardThemePurple.id to "#7B3FB0",
            binding.cardThemeTeal.id to "#00897B",
            binding.cardThemeOrange.id to "#F57C00",
            binding.cardThemeRed.id to "#D34034",
            R.id.cardSeasonSpring to "#43A047",
            R.id.cardSeasonSummer to "#FF9800",
            R.id.cardSeasonAutumn to "#E64A19",
            R.id.cardSeasonWinter to "#03A9F4",
            R.id.cardCountryChina to "#F44336",
            R.id.cardCountryEgypt to "#F9A825",
            R.id.cardCountryEngland to "#1976D2",
            R.id.cardCountryFrance to "#3F51B5",
            R.id.cardCountryIndia to "#FF6F00",
            R.id.cardCountryJapan to "#D81B60",
            R.id.cardCountryRussia to "#1565C0",
            R.id.cardCountryUsa to "#0D47A1"
        )
    }
    
    // Reverse mapping: color -> card ID
    private val colorToCardId: Map<String, Int> by lazy {
        themeColors.entries
            .filterNot { (cardId, _) -> imageThemeCardIds().contains(cardId) }
            .associate { (cardId, color) -> color to cardId }
    }

    private val themeWallpapers: Map<Int, String> by lazy {
        mapOf(
            R.id.cardSeasonSpring to "sea_1",
            R.id.cardSeasonSummer to "sea_2",
            R.id.cardSeasonAutumn to "sea_3",
            R.id.cardSeasonWinter to "sea_4",
            R.id.cardCountryChina to "item_china",
            R.id.cardCountryEgypt to "item_egypt",
            R.id.cardCountryEngland to "item_england",
            R.id.cardCountryFrance to "item_france",
            R.id.cardCountryIndia to "item_india",
            R.id.cardCountryJapan to "item_japan",
            R.id.cardCountryRussia to "item_russia",
            R.id.cardCountryUsa to "item_usa"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityThemesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup navigation bar with white background and black icons
        ThemeManager.setupNavigationBar(this)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupImageThemeTiles()
        setupThemeSelection()
        setupSaveButton()
        
        // Load and restore previously selected theme
        restoreSelectedTheme()
        ThemeTransitionAdManager.preload(applicationContext)
        
        // Register receiver for theme changes
        registerThemeChangeReceiver()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        themeChangeReceiver?.let {
            unregisterReceiver(it)
        }
        // Unregister theme callback
        themeUpdateCallback?.let {
            ThemeManager.unregisterThemeUpdateCallback(it)
        }
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            if (binding.previewContainer.visibility == View.VISIBLE) {
                showPickerMode()
            } else {
                finish()
            }
        }
    }
    
    private fun setupThemeSelection() {
        // List of all theme cards with their corresponding icon IDs
        val themeCards = listOf(
            Pair(binding.cardThemeBlue, binding.iconSelectedBlue),
            Pair(binding.cardThemeGreen, binding.iconSelectedGreen),
            Pair(binding.cardThemePink, binding.iconSelectedPink),
            Pair(binding.cardThemeYellow, binding.iconSelectedYellow),
            Pair(binding.cardThemeOral, binding.iconSelectedOral),
            Pair(binding.cardThemeNavyBlue, binding.iconSelectedNavyBlue),
            Pair(binding.cardThemeCeruleanBlue, binding.iconSelectedCeruleanBlue),
            Pair(binding.cardThemeBlackBlue, binding.iconSelectedBlackBlue),
            Pair(binding.cardThemePurple, binding.iconSelectedPurple),
            Pair(binding.cardThemeTeal, binding.iconSelectedTeal),
            Pair(binding.cardThemeOrange, binding.iconSelectedOrange),
            Pair(binding.cardThemeRed, binding.iconSelectedRed)
        )
        
        themeCards.forEach { (card, icon) ->
            card.setOnClickListener {
                clearThemeSelection()
                icon.visibility = View.VISIBLE
                selectedCardId = card.id
                selectedIconId = icon.id
                updateSelectedPreview(card.id)
                showPreviewMode()
            }
        }

        imageThemeCardIds().forEach { cardId ->
            findViewById<View>(cardId)?.setOnClickListener { card ->
                clearThemeSelection()
                setImageThemeSelected(card, true)
                selectedCardId = card.id
                selectedIconId = null
                updateSelectedPreview(card.id)
                showPreviewMode()
            }
        }
    }

    private fun setupImageThemeTiles() {
        setImageThemeTile(R.id.cardSeasonSpring, R.drawable.sea_1, R.string.spring)
        setImageThemeTile(R.id.cardSeasonSummer, R.drawable.sea_2, R.string.summer)
        setImageThemeTile(R.id.cardSeasonAutumn, R.drawable.sea_3, R.string.autumn)
        setImageThemeTile(R.id.cardSeasonWinter, R.drawable.sea_4, R.string.winter)
        setImageThemeTile(R.id.cardCountryChina, R.drawable.item_china, R.string.china)
        setImageThemeTile(R.id.cardCountryEgypt, R.drawable.item_egypt, R.string.egypt)
        setImageThemeTile(R.id.cardCountryEngland, R.drawable.item_england, R.string.england)
        setImageThemeTile(R.id.cardCountryFrance, R.drawable.item_france, R.string.france)
        setImageThemeTile(R.id.cardCountryIndia, R.drawable.item_india, R.string.india)
        setImageThemeTile(R.id.cardCountryJapan, R.drawable.item_japan, R.string.japan)
        setImageThemeTile(R.id.cardCountryRussia, R.drawable.item_russia, R.string.russia)
        setImageThemeTile(R.id.cardCountryUsa, R.drawable.item_usa, R.string.usa)
    }

    private fun setImageThemeTile(cardId: Int, drawableId: Int, labelId: Int) {
        val card = findViewById<View>(cardId) ?: return
        card.findViewById<ImageView>(R.id.themeImage)?.apply {
            setImageResource(drawableId)
            contentDescription = getString(labelId)
        }
        card.findViewById<TextView>(R.id.themeLabel)?.setText(labelId)
    }

    private fun imageThemeCardIds(): List<Int> = listOf(
        R.id.cardSeasonSpring,
        R.id.cardSeasonSummer,
        R.id.cardSeasonAutumn,
        R.id.cardSeasonWinter,
        R.id.cardCountryChina,
        R.id.cardCountryEgypt,
        R.id.cardCountryEngland,
        R.id.cardCountryFrance,
        R.id.cardCountryIndia,
        R.id.cardCountryJapan,
        R.id.cardCountryRussia,
        R.id.cardCountryUsa
    )

    private fun clearThemeSelection() {
        listOf(
            binding.iconSelectedBlue,
            binding.iconSelectedGreen,
            binding.iconSelectedPink,
            binding.iconSelectedYellow,
            binding.iconSelectedOral,
            binding.iconSelectedNavyBlue,
            binding.iconSelectedCeruleanBlue,
            binding.iconSelectedBlackBlue,
            binding.iconSelectedPurple,
            binding.iconSelectedTeal,
            binding.iconSelectedOrange,
            binding.iconSelectedRed
        ).forEach { it.visibility = View.GONE }

        imageThemeCardIds().forEach { cardId ->
            findViewById<View>(cardId)?.let { setImageThemeSelected(it, false) }
        }
    }

    private fun setImageThemeSelected(card: View, selected: Boolean) {
        card.findViewById<View>(R.id.themeSelectedScrim)?.visibility = if (selected) View.VISIBLE else View.GONE
        card.findViewById<View>(R.id.themeSelectedIcon)?.visibility = if (selected) View.VISIBLE else View.GONE
    }

    private fun setupSaveButton() {
        binding.buttonSave.backgroundTintList = null
        binding.buttonSave.setOnClickListener {
            applySelectedTheme()
            PersonalizationSaveAdNavigator.showAdThenFinish(this)
        }
    }

    private fun applySelectedTheme() {
        val cardId = selectedCardId ?: return
        val themeColor = themeColors[cardId] ?: AppPreferences.getThemeColor(this)
        AppPreferences.setThemeColor(this, themeColor)
        AppPreferences.setThemeColorLight(this, AppPreferences.getLighterColor(themeColor))
        AppPreferences.setThemeWallpaper(this, themeWallpapers[cardId])

        ThemeManager.applyThemeImmediate(this, binding.root)
        binding.root.invalidate()
        binding.root.requestLayout()

        val currentActivity = com.text.messages.sms.messanger.util.AppForegroundActivityTracker.currentActivity
        if (currentActivity != null) {
            try {
                val activityRoot = currentActivity.window?.decorView?.findViewById<View>(android.R.id.content)
                activityRoot?.let {
                    ThemeManager.applyThemeImmediate(currentActivity, it)
                    it.invalidate()
                    it.requestLayout()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        ThemeManager.notifyThemeChanged(this, binding.root)

        val intent = android.content.Intent("com.text.messages.sms.messanger.THEME_CHANGED")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        sendOrderedBroadcast(intent, null)
        binding.root.postDelayed({ sendBroadcast(intent) }, 10)
        binding.root.postDelayed({ sendBroadcast(intent) }, 50)
    }
    
    private fun registerThemeChangeReceiver() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Re-apply theme IMMEDIATELY when it changes
                ThemeManager.applyThemeImmediate(this@ThemesActivity, binding.root)
                // Force immediate back button update
                ThemeManager.applyThemeImmediate(this@ThemesActivity, binding.buttonBack)
                binding.buttonBack.invalidate()
                binding.buttonBack.requestLayout()
                binding.root.invalidate()
                binding.root.requestLayout()
            }
        }
        
        // Also register for direct callback updates
        themeUpdateCallback = { ctx: Context, _: View ->
            if (ctx == this@ThemesActivity) {
                ThemeManager.applyThemeImmediate(this@ThemesActivity, binding.root)
                ThemeManager.applyThemeImmediate(this@ThemesActivity, binding.buttonBack)
                binding.buttonBack.invalidate()
                binding.buttonBack.requestLayout()
            }
        }
        themeUpdateCallback?.let { ThemeManager.registerThemeUpdateCallback(it) }
        registerReceiver(themeChangeReceiver, IntentFilter("com.text.messages.sms.messanger.THEME_CHANGED"), receiverFlags)
    }
    
    private fun restoreSelectedTheme() {
        // Get the currently saved theme color
        val savedThemeColor = AppPreferences.getThemeColor(this)
        val savedWallpaper = AppPreferences.getThemeWallpaper(this)
        
        // Find the card ID that corresponds to this color
        val cardId = savedWallpaper
            ?.let { wallpaper -> themeWallpapers.entries.firstOrNull { it.value == wallpaper }?.key }
            ?: colorToCardId[savedThemeColor]
        
        if (cardId != null) {
            // Find the corresponding icon
            val iconId = when (cardId) {
                binding.cardThemeBlue.id -> binding.iconSelectedBlue.id
                binding.cardThemeGreen.id -> binding.iconSelectedGreen.id
                binding.cardThemePink.id -> binding.iconSelectedPink.id
                binding.cardThemeYellow.id -> binding.iconSelectedYellow.id
                binding.cardThemeOral.id -> binding.iconSelectedOral.id
                binding.cardThemeNavyBlue.id -> binding.iconSelectedNavyBlue.id
                binding.cardThemeCeruleanBlue.id -> binding.iconSelectedCeruleanBlue.id
                binding.cardThemeBlackBlue.id -> binding.iconSelectedBlackBlue.id
                binding.cardThemePurple.id -> binding.iconSelectedPurple.id
                binding.cardThemeTeal.id -> binding.iconSelectedTeal.id
                binding.cardThemeOrange.id -> binding.iconSelectedOrange.id
                binding.cardThemeRed.id -> binding.iconSelectedRed.id
                else -> null
            }
            
            if (iconId != null) {
                clearThemeSelection()
                findViewById<View>(iconId)?.visibility = View.VISIBLE
                selectedCardId = cardId
                selectedIconId = iconId
                updateSelectedPreview(cardId)
            } else if (imageThemeCardIds().contains(cardId)) {
                clearThemeSelection()
                findViewById<View>(cardId)?.let { setImageThemeSelected(it, true) }
                selectedCardId = cardId
                selectedIconId = null
                updateSelectedPreview(cardId)
            }
        } else {
            // If no saved theme or color doesn't match, default to Green
            clearThemeSelection()
            binding.iconSelectedGreen.visibility = View.VISIBLE
            selectedCardId = binding.cardThemeGreen.id
            selectedIconId = binding.iconSelectedGreen.id
            updateSelectedPreview(binding.cardThemeGreen.id)
        }

        showPickerMode()
    }

    private fun updateSelectedPreview(cardId: Int) {
        val color = themeColors[cardId] ?: AppPreferences.getThemeColor(this)
        val colorInt = Color.parseColor(color)
        val lightColor = AppPreferences.getLighterColor(color)
        val lightColorInt = Color.parseColor(lightColor)

        binding.previewTitle.setTextColor(colorInt)
        binding.previewSearch.backgroundTintList = ColorStateList.valueOf(lightColorInt)
        binding.previewBottomNav.setBackgroundColor(colorInt)
        binding.previewStartChat.backgroundTintList = ColorStateList.valueOf(colorInt)
        binding.buttonSave.backgroundTintList = ColorStateList.valueOf(colorInt)
        tintPreviewUnreadDots(colorInt)

        val wallpaper = themeWallpapers[cardId]
        if (wallpaper != null) {
            val drawableId = resources.getIdentifier(wallpaper, "drawable", packageName)
            if (drawableId != 0) {
                binding.previewPhoneCard.setCardBackgroundColor(Color.WHITE)
                binding.imageSelectedThemePreview.alpha = 0.56f
                binding.imageSelectedThemePreview.setImageResource(drawableId)
                binding.imageSelectedThemePreview.scaleType = ImageView.ScaleType.CENTER_CROP
                return
            }
        }

        binding.previewPhoneCard.setCardBackgroundColor(lightColorInt)
        binding.imageSelectedThemePreview.alpha = 1f
        binding.imageSelectedThemePreview.setImageDrawable(null)
        binding.imageSelectedThemePreview.setBackgroundColor(lightColorInt)
    }

    private fun tintPreviewUnreadDots(colorInt: Int) {
        for (i in 0 until binding.previewRows.childCount) {
            val row = binding.previewRows.getChildAt(i)
            row.findViewById<TextView>(R.id.previewUnread)?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
            }
        }
    }

    private fun showPreviewMode() {
        binding.scrollView.visibility = View.GONE
        binding.previewContainer.visibility = View.VISIBLE
        binding.buttonSave.visibility = View.VISIBLE
    }

    private fun showPickerMode() {
        binding.scrollView.visibility = View.VISIBLE
        binding.previewContainer.visibility = View.GONE
        binding.buttonSave.visibility = View.GONE
    }
}

