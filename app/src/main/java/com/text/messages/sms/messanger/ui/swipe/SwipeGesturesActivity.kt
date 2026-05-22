package com.text.messages.sms.messanger.ui.swipe

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivitySwipeGesturesBinding
import com.text.messages.sms.messanger.databinding.DialogSwipeActionSelectionBinding
import com.text.messages.sms.messanger.databinding.ItemSwipeActionBinding
import com.text.messages.sms.messanger.util.ThemeManager
import com.text.messages.sms.messanger.util.loadBannerAdWithRemoteConfig
import com.text.messages.sms.messanger.util.AnalyticsHelper

class SwipeGesturesActivity : BaseActivity() {

    private lateinit var binding: ActivitySwipeGesturesBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "swipe_gestures"
        const val KEY_RIGHT_SWIPE = "right_swipe_action"
        const val KEY_LEFT_SWIPE = "left_swipe_action"
    }
    
    enum class SwipeAction(@StringRes val displayNameRes: Int, val iconRes: Int) {
        NONE(R.string.swipe_action_none, 0),
        ARCHIVE(R.string.swipe_action_archive, R.drawable.archive_swipe),
        DELETE(R.string.swipe_action_delete, R.drawable.ic_delete),
        BLOCK(R.string.swipe_action_block, R.drawable.ic_block),
        CALL(R.string.swipe_action_call, R.drawable.ic_call),
        MARK_AS_READ(R.string.swipe_action_mark_as_read, R.drawable.mark_read),
        MARK_AS_UNREAD(R.string.swipe_action_mark_as_unread, R.drawable.ic_mark_unread)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AnalyticsHelper.logScreenView("SwipeGesturesActivity", "SwipeGesturesActivity")
        
        binding = ActivitySwipeGesturesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupBackButton()
        setupSwipeActions()
        setupPersonIcons()
        setupBannerAd()
    }
    
    private fun setupPersonIcons() {
        // Set background color for person icons using theme light color
        val lightColor = ThemeManager.getThemeColorLight(this)
        try {
            binding.imagePersonRight.setCircleBackgroundColor(lightColor)
            binding.imagePersonLeft.setCircleBackgroundColor(lightColor)
        } catch (e: Exception) {
            // Fallback if method doesn't exist
            binding.imagePersonRight.setBackgroundColor(lightColor)
            binding.imagePersonLeft.setBackgroundColor(lightColor)
        }
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupSwipeActions() {
        // Load saved actions
        val rightSwipeOrdinal = prefs.getInt(KEY_RIGHT_SWIPE, SwipeAction.MARK_AS_READ.ordinal)
        val leftSwipeOrdinal = prefs.getInt(KEY_LEFT_SWIPE, SwipeAction.ARCHIVE.ordinal)
        
        val rightSwipeAction = if (rightSwipeOrdinal in SwipeAction.values().indices) {
            SwipeAction.values()[rightSwipeOrdinal]
        } else {
            SwipeAction.MARK_AS_READ
        }
        
        val leftSwipeAction = if (leftSwipeOrdinal in SwipeAction.values().indices) {
            SwipeAction.values()[leftSwipeOrdinal]
        } else {
            SwipeAction.ARCHIVE
        }
        
        updateRightSwipeUI(rightSwipeAction)
        updateLeftSwipeUI(leftSwipeAction)
        
        binding.buttonChangeRight.setOnClickListener {
            // Reload current action before showing dialog
            val currentRightAction = SwipeAction.values()[
                prefs.getInt(KEY_RIGHT_SWIPE, SwipeAction.MARK_AS_READ.ordinal)
            ]
            showSwipeActionDialog(true, currentRightAction)
        }
        
        binding.buttonChangeLeft.setOnClickListener {
            // Reload current action before showing dialog
            val currentLeftAction = SwipeAction.values()[
                prefs.getInt(KEY_LEFT_SWIPE, SwipeAction.ARCHIVE.ordinal)
            ]
            showSwipeActionDialog(false, currentLeftAction)
        }
    }
    
    private fun updateRightSwipeUI(action: SwipeAction) {
        binding.textRightSwipeAction.text = getString(action.displayNameRes)
        if (action.iconRes != 0) {
            binding.imageRightSwipeIcon.setImageResource(action.iconRes)
            binding.imageRightSwipeIcon.visibility = View.VISIBLE
        } else {
            binding.imageRightSwipeIcon.visibility = View.GONE
        }
    }
    
    private fun updateLeftSwipeUI(action: SwipeAction) {
        binding.textLeftSwipeAction.text = getString(action.displayNameRes)
        if (action.iconRes != 0) {
            binding.imageLeftSwipeIcon.setImageResource(action.iconRes)
            binding.imageLeftSwipeIcon.visibility = View.VISIBLE
        } else {
            binding.imageLeftSwipeIcon.visibility = View.GONE
        }
    }
    
    private fun showSwipeActionDialog(isRightSwipe: Boolean, currentAction: SwipeAction) {
        val dialogBinding = DialogSwipeActionSelectionBinding.inflate(LayoutInflater.from(this))
        dialogBinding.textDialogTitle.text = getString(if (isRightSwipe) R.string.swipe_right else R.string.swipe_left)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        // Apply theme to dialog
        ThemeManager.applyThemeToDialog(this, dialogBinding.root)
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Apply theme after dialog is shown
        dialog.setOnShowListener {
            ThemeManager.applyTheme(this, dialogBinding.root)
        }
        
        val adapter = SwipeActionAdapter(SwipeAction.values().toList(), currentAction) { selectedAction ->
            if (isRightSwipe) {
                prefs.edit().putInt(KEY_RIGHT_SWIPE, selectedAction.ordinal).apply()
                updateRightSwipeUI(selectedAction)
            } else {
                prefs.edit().putInt(KEY_LEFT_SWIPE, selectedAction.ordinal).apply()
                updateLeftSwipeUI(selectedAction)
            }
            dialog.dismiss()
        }
        
        dialogBinding.recyclerViewActions.layoutManager = LinearLayoutManager(this)
        dialogBinding.recyclerViewActions.adapter = adapter
        
        adapter.setDialog(dialog)
        dialog.show()
    }
    
    private fun setupBannerAd() {
        binding.adViewBanner.loadBannerAdWithRemoteConfig()
    }
}

class SwipeActionAdapter(
    private val actions: List<SwipeGesturesActivity.SwipeAction>,
    private val selectedAction: SwipeGesturesActivity.SwipeAction,
    private val onActionSelected: (SwipeGesturesActivity.SwipeAction) -> Unit
) : RecyclerView.Adapter<SwipeActionAdapter.SwipeActionViewHolder>() {

    private var dialog: AlertDialog? = null
    private var selectedPosition: Int = run {
        val index = actions.indexOfFirst { it == selectedAction }
        if (index >= 0) index else 0
    }

    fun setDialog(dialog: AlertDialog) {
        this.dialog = dialog
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwipeActionViewHolder {
        val binding = ItemSwipeActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SwipeActionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SwipeActionViewHolder, position: Int) {
        holder.bind(actions[position], position == selectedPosition)
    }

    override fun getItemCount() = actions.size

    inner class SwipeActionViewHolder(
        private val binding: ItemSwipeActionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(action: SwipeGesturesActivity.SwipeAction, isSelected: Boolean) {
            binding.textActionName.text = binding.root.context.getString(action.displayNameRes)
            binding.radioButton.isChecked = isSelected
            // Prevent radio button from being clickable directly - only the row should be clickable
            binding.radioButton.isClickable = false
            binding.radioButton.isFocusable = false
            binding.radioButton.isFocusableInTouchMode = false

            // Remove any existing click listeners to avoid duplicates
            binding.root.setOnClickListener(null)
            binding.root.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && position != selectedPosition) {
                    val previousPosition = selectedPosition
                    selectedPosition = position
                    
                    // Update both items to reflect the new selection
                    if (previousPosition >= 0 && previousPosition < itemCount) {
                        notifyItemChanged(previousPosition)
                    }
                    notifyItemChanged(selectedPosition)
                    
                    // Call the callback to save selection and close dialog
                    onActionSelected(action)
                } else if (position == selectedPosition) {
                    // If clicking the already selected item, just close the dialog
                    onActionSelected(action)
                }
            }
        }
    }
}

