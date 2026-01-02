package com.quizangomedia.messages.ui.main

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.ui.swipe.SwipeGesturesActivity

class SwipeHelper(
    private val context: Context,
    private val adapter: ConversationAdapter,
    private val onSwipeAction: (Conversation, SwipeGesturesActivity.SwipeAction) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val prefs: SharedPreferences = context.getSharedPreferences(SwipeGesturesActivity.PREFS_NAME, Context.MODE_PRIVATE)
    private val background = ColorDrawable()
    private val iconPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
    }
    
    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val rightSwipeAction = SwipeGesturesActivity.SwipeAction.values()[
            prefs.getInt(SwipeGesturesActivity.KEY_RIGHT_SWIPE, SwipeGesturesActivity.SwipeAction.MARK_AS_READ.ordinal)
        ]
        val leftSwipeAction = SwipeGesturesActivity.SwipeAction.values()[
            prefs.getInt(SwipeGesturesActivity.KEY_LEFT_SWIPE, SwipeGesturesActivity.SwipeAction.ARCHIVE.ordinal)
        ]
        
        var swipeDirs = 0
        if (rightSwipeAction != SwipeGesturesActivity.SwipeAction.NONE) {
            swipeDirs = swipeDirs or ItemTouchHelper.RIGHT
        }
        if (leftSwipeAction != SwipeGesturesActivity.SwipeAction.NONE) {
            swipeDirs = swipeDirs or ItemTouchHelper.LEFT
        }
        
        return swipeDirs
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        
        val conversation = adapter.getConversationAt(position)
        
        val action = when (direction) {
            ItemTouchHelper.RIGHT -> {
                SwipeGesturesActivity.SwipeAction.values()[
                    prefs.getInt(SwipeGesturesActivity.KEY_RIGHT_SWIPE, SwipeGesturesActivity.SwipeAction.MARK_AS_READ.ordinal)
                ]
            }
            ItemTouchHelper.LEFT -> {
                SwipeGesturesActivity.SwipeAction.values()[
                    prefs.getInt(SwipeGesturesActivity.KEY_LEFT_SWIPE, SwipeGesturesActivity.SwipeAction.ARCHIVE.ordinal)
                ]
            }
            else -> SwipeGesturesActivity.SwipeAction.NONE
        }
        
        if (action != SwipeGesturesActivity.SwipeAction.NONE) {
            // Execute the action first
            onSwipeAction(conversation, action)
        }
        
        // Reset the item position after action is executed
        // Use post to ensure it happens after any list updates
        viewHolder.itemView.post {
            viewHolder.itemView.translationX = 0f
            viewHolder.itemView.alpha = 1f
        }
    }
    
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        // Ensure the view is reset when swipe is cleared
        viewHolder.itemView.translationX = 0f
        viewHolder.itemView.alpha = 1f
        viewHolder.itemView.clearAnimation()
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        // When swipe ends (actionState == ACTION_STATE_IDLE), ensure view is reset
        if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && viewHolder != null) {
            viewHolder.itemView.translationX = 0f
            viewHolder.itemView.alpha = 1f
            viewHolder.itemView.clearAnimation()
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        
        val action = when {
            dX > 0 -> SwipeGesturesActivity.SwipeAction.values()[
                prefs.getInt(SwipeGesturesActivity.KEY_RIGHT_SWIPE, SwipeGesturesActivity.SwipeAction.MARK_AS_READ.ordinal)
            ]
            dX < 0 -> SwipeGesturesActivity.SwipeAction.values()[
                prefs.getInt(SwipeGesturesActivity.KEY_LEFT_SWIPE, SwipeGesturesActivity.SwipeAction.ARCHIVE.ordinal)
            ]
            else -> return
        }
        
        if (action == SwipeGesturesActivity.SwipeAction.NONE) return
        
        // Draw background
        background.color = Color.parseColor("#0C56CF")
        when {
            dX > 0 -> {
                background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
            }
            dX < 0 -> {
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
            }
        }
        background.draw(c)
        
        // Draw icon
        val iconRes = action.iconRes
        if (iconRes != 0) {
            val icon = ContextCompat.getDrawable(context, iconRes)
            icon?.let {
                // Make drawable mutable and apply white color filter to make icons white
                val mutableIcon = it.mutate()
                mutableIcon.setTint(Color.WHITE)
                
                val iconSize = (itemHeight * 0.3).toInt()
                val iconMargin = (itemHeight - iconSize) / 2
                
                val iconLeft: Int
                val iconTop = itemView.top + iconMargin
                val iconRight: Int
                val iconBottom = itemView.top + iconMargin + iconSize
                
                when {
                    dX > 0 -> {
                        iconLeft = itemView.left + iconMargin
                        iconRight = itemView.left + iconMargin + iconSize
                    }
                    dX < 0 -> {
                        iconLeft = itemView.right - iconMargin - iconSize
                        iconRight = itemView.right - iconMargin
                    }
                    else -> return
                }
                
                mutableIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                mutableIcon.draw(c)
            }
        }
        
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}

