package com.text.messages.sms.messanger.ui.launcher.views

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.text.messages.sms.messanger.ui.launcher.model.GridCoordinate
import com.text.messages.sms.messanger.ui.launcher.model.LaunchableApp
import java.util.LinkedHashMap
import kotlin.math.min

class DrawingArea(context: Context) : View(context) {

    private var coordinateMap: LinkedHashMap<GridCoordinate, LaunchableApp> = LinkedHashMap()
    private var hitRects: LinkedHashMap<LaunchableApp, RectF> = LinkedHashMap()
    private var columnsProvider: () -> Int = { 4 }
    private var rowsProvider: () -> Int = { 5 }

    private val iconRect = Rect()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
    }

    fun bindDataSource(
        coordinateMap: LinkedHashMap<GridCoordinate, LaunchableApp>,
        hitRects: LinkedHashMap<LaunchableApp, RectF>,
        columnsProvider: () -> Int,
        rowsProvider: () -> Int
    ) {
        this.coordinateMap = coordinateMap
        this.hitRects = hitRects
        this.columnsProvider = columnsProvider
        this.rowsProvider = rowsProvider
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val columns = columnsProvider()
        val rows = rowsProvider()
        if (columns <= 0 || rows <= 0) return

        val cellW = width.toFloat() / columns
        val cellH = height.toFloat() / rows
        val iconSize = min(cellW, cellH) * 0.55f

        hitRects.clear()

        val pm = context.packageManager
        for ((coord, app) in coordinateMap) {
            val cx = (coord.x + 0.5f) * cellW
            val cy = (coord.y + 0.45f) * cellH

            val left = (cx - iconSize / 2f)
            val top = (cy - iconSize / 2f)
            val right = (cx + iconSize / 2f)
            val bottom = (cy + iconSize / 2f)
            val rectF = RectF(left, top, right, bottom)
            hitRects[app] = RectF(
                left,
                top,
                right,
                bottom + textPaint.textSize * 1.4f
            )

            val d = runCatching {
                pm.getActivityIcon(app.componentName())
            }.getOrElse {
                runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
            } ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)

            if (d != null) {
                iconRect.set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                d.bounds = iconRect
                d.draw(canvas)
            }

            canvas.drawText(
                app.label,
                cx,
                bottom + textPaint.textSize * 1.1f,
                textPaint
            )
        }
    }

    fun launchApp(app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.componentName()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

