package com.quizangomedia.messages.ui.advance

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.R

class AdvanceOptionAdapter(
    private val options: List<AdvanceOption>,
    private val onOptionClick: (AdvanceOption) -> Unit
) : ListAdapter<AdvanceOption, AdvanceOptionAdapter.ViewHolder>(DiffCallback()) {

    private var onToggleChangedListener: ((AdvanceOptionType, Boolean) -> Unit)? = null
    
    companion object {
        private const val TAG = "AdvanceOptionAdapter"
    }

    fun setOnToggleChangedListener(listener: (AdvanceOptionType, Boolean) -> Unit) {
        onToggleChangedListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_advance_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageIcon: ImageView = itemView.findViewById(R.id.imageIcon)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDetail: TextView = itemView.findViewById(R.id.textDetail)
        private val switchToggle: SwitchMaterial = itemView.findViewById(R.id.switchToggle)

        fun bind(option: AdvanceOption) {
            Log.d(TAG, "Binding option: ${option.title}, hasToggle: ${option.hasToggle}, toggleState: ${option.toggleState}")
            textTitle.text = option.title

            // Handle icon
            if (option.iconRes != null) {
                imageIcon.setImageResource(option.iconRes)
                imageIcon.visibility = View.VISIBLE
            } else {
                imageIcon.visibility = View.INVISIBLE
            }

            // Handle detail
            if (option.detail != null) {
                textDetail.text = option.detail
                textDetail.visibility = View.VISIBLE
            } else {
                textDetail.visibility = View.GONE
            }

            // 🚨 FIX 2: ALWAYS reset switch state
            if (option.hasToggle) {
                Log.d(TAG, "Setting toggle VISIBLE for: ${option.title}")
                switchToggle.visibility = View.VISIBLE
                switchToggle.alpha = 1.0f
                switchToggle.isChecked = option.toggleState
                Log.d(TAG, "After setting - visibility: ${switchToggle.visibility}, alpha: ${switchToggle.alpha}, checked: ${switchToggle.isChecked}, width: ${switchToggle.width}, height: ${switchToggle.height}")
                
                // Remove old listener before setting new one (RecyclerView rule)
                switchToggle.setOnCheckedChangeListener(null)
                switchToggle.setOnCheckedChangeListener { _, isChecked ->
                    onToggleChangedListener?.invoke(option.type, isChecked)
                }
                // Apply theme once
                Log.d(TAG, "Applying theme to toggle for: ${option.title}")
                com.quizangomedia.messages.util.ThemeManager.applyToggleTheme(switchToggle, itemView.context)
                Log.d(TAG, "After theme - visibility: ${switchToggle.visibility}, alpha: ${switchToggle.alpha}, width: ${switchToggle.width}, height: ${switchToggle.height}")
                
                // Item itself should NOT be clickable
                itemView.setOnClickListener(null)
            } else {
                Log.d(TAG, "Setting toggle GONE for: ${option.title}")
                // 🚨 CRITICAL PART OF FIX 2
                switchToggle.visibility = View.GONE
                switchToggle.setOnCheckedChangeListener(null)
                itemView.setOnClickListener {
                    onOptionClick(option)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AdvanceOption>() {
        override fun areItemsTheSame(oldItem: AdvanceOption, newItem: AdvanceOption): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: AdvanceOption, newItem: AdvanceOption): Boolean {
            return oldItem == newItem
        }
    }
}

