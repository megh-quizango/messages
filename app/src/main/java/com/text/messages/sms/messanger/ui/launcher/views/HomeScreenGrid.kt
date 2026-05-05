package com.text.messages.sms.messanger.ui.launcher.views

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import com.text.messages.sms.messanger.ui.launcher.model.GridCoordinate
import com.text.messages.sms.messanger.ui.launcher.model.LaunchableApp
import java.util.LinkedHashMap
import kotlin.math.max

class HomeScreenGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val drawingArea = DrawingArea(context)

    private val coordinateMap: LinkedHashMap<GridCoordinate, LaunchableApp> = LinkedHashMap()
    private val hitRects: LinkedHashMap<LaunchableApp, RectF> = LinkedHashMap()

    var onRequestOpenDrawer: (() -> Unit)? = null
    var columns: Int = 4
        set(value) {
            field = max(1, value)
            requestLayout()
            invalidate()
        }
    var rows: Int = 5
        set(value) {
            field = max(1, value)
            requestLayout()
            invalidate()
        }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val hit = hitRects.entries.firstOrNull { it.value.contains(e.x, e.y) }?.key
            if (hit != null) {
                drawingArea.launchApp(hit)
                return true
            }
            return super.onSingleTapUp(e)
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // Very lightweight "swipe up opens drawer" affordance.
            if (velocityY < -2000f) {
                onRequestOpenDrawer?.invoke()
                return true
            }
            return false
        }
    })

    init {
        isClickable = true
        isFocusable = true
        addView(drawingArea, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        drawingArea.bindDataSource(
            coordinateMap = coordinateMap,
            hitRects = hitRects,
            columnsProvider = { columns },
            rowsProvider = { rows }
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }

    fun setApps(apps: List<LaunchableApp>) {
        coordinateMap.clear()
        hitRects.clear()

        var index = 0
        for (app in apps) {
            val x = index % columns
            val y = (index / columns) % rows
            coordinateMap[GridCoordinate(x, y)] = app
            index++
            if (index >= columns * rows) break // first page only for now
        }

        drawingArea.invalidate()
    }

    fun scrollToFirstPage() {
        // Placeholder for future multi-page support.
    }
}

