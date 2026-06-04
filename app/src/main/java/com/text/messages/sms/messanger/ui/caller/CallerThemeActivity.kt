package com.text.messages.sms.messanger.ui.caller

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.ui.base.BaseActivity
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.ThemeManager

class CallerThemeActivity : BaseActivity() {

    private lateinit var gridCallerThemes: GridLayout
    private lateinit var buttonApply: TextView
    private lateinit var previewHeader: ConstraintLayout
    private lateinit var previewAvatar: ImageView
    private lateinit var previewExplore: LinearLayout
    private lateinit var previewSettings: LinearLayout

    private var selectedIndex: Int = 0
    private val selectedMarks = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caller_theme)

        ThemeManager.setupNavigationBar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootCallerTheme)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        gridCallerThemes = findViewById(R.id.gridCallerThemes)
        buttonApply = findViewById(R.id.buttonCallerThemeApply)
        previewHeader = findViewById(R.id.previewCallerHeader)
        previewAvatar = findViewById(R.id.previewCallerAvatar)
        previewExplore = findViewById(R.id.previewCallerExplore)
        previewSettings = findViewById(R.id.previewCallerSettings)

        selectedIndex = AppPreferences.getCallerThemeIndex(this)
        findViewById<View>(R.id.buttonCallerThemeBack).setOnClickListener { finish() }
        buttonApply.setOnClickListener { applySelectedTheme() }

        buildThemeGrid()
        selectTheme(selectedIndex)
    }

    private fun buildThemeGrid() {
        gridCallerThemes.removeAllViews()
        selectedMarks.clear()

        AppPreferences.callerThemes.forEach { theme ->
            val cell = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                foreground = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                    .use { it.getDrawable(0) }
                setPadding(6.dp, 6.dp, 6.dp, 6.dp)
            }

            val swatch = View(this).apply {
                background = gradientDrawable(theme, 12.dp.toFloat())
            }
            cell.addView(
                swatch,
                FrameLayout.LayoutParams(48.dp, 48.dp, Gravity.CENTER)
            )

            val selectedMark = TextView(this).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = "✓"
                setTextColor(Color.WHITE)
                textSize = 18f
                visibility = View.GONE
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#66000000"))
                }
            }
            selectedMarks.add(selectedMark)
            cell.addView(
                selectedMark,
                FrameLayout.LayoutParams(24.dp, 24.dp, Gravity.CENTER)
            )

            cell.setOnClickListener { selectTheme(theme.index) }

            gridCallerThemes.addView(
                cell,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = 64.dp
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dp, 2.dp, 4.dp, 8.dp)
                }
            )
        }
    }

    private fun selectTheme(index: Int) {
        selectedIndex = index.coerceIn(AppPreferences.callerThemes.indices)
        selectedMarks.forEachIndexed { markIndex, mark ->
            mark.visibility = if (markIndex == selectedIndex) View.VISIBLE else View.GONE
        }
        updatePreview(AppPreferences.callerThemes[selectedIndex])
    }

    private fun updatePreview(theme: AppPreferences.CallerTheme) {
        val topColor = Color.parseColor(theme.topColor)
        val bottomColor = Color.parseColor(theme.bottomColor)
        val buttonColor = Color.parseColor(theme.buttonColor)

        previewHeader.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        ).apply {
            cornerRadii = floatArrayOf(8.dp.toFloat(), 8.dp.toFloat(), 8.dp.toFloat(), 8.dp.toFloat(), 0f, 0f, 0f, 0f)
        }

        val buttonBackground = GradientDrawable().apply {
            setColor(buttonColor)
            cornerRadius = 8.dp.toFloat()
        }
        previewExplore.background = buttonBackground.constantState?.newDrawable()?.mutate()
        previewSettings.background = buttonBackground.constantState?.newDrawable()?.mutate()
        buttonApply.background = GradientDrawable().apply {
            setColor(buttonColor)
            cornerRadius = 18.dp.toFloat()
        }
        previewAvatar.imageTintList = ColorStateList.valueOf(buttonColor)
    }

    private fun applySelectedTheme() {
        AppPreferences.setCallerThemeIndex(this, selectedIndex)
        Toast.makeText(this, R.string.callercad_theme_applied_successfully, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun gradientDrawable(theme: AppPreferences.CallerTheme, radius: Float): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(theme.topColor), Color.parseColor(theme.bottomColor))
        ).apply {
            cornerRadius = radius
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private inline fun <T> android.content.res.TypedArray.use(block: (android.content.res.TypedArray) -> T): T {
        return try {
            block(this)
        } finally {
            recycle()
        }
    }
}
