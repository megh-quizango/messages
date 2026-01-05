package com.quizangomedia.messages.ui.advance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
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
        private val switchToggle: Switch? = itemView.findViewById(R.id.switchToggle)

        fun bind(option: AdvanceOption) {
            textTitle.text = option.title

            // Handle icon
            if (option.iconRes != null) {
                imageIcon.setImageResource(option.iconRes)
                imageIcon.visibility = View.VISIBLE
            } else {
                imageIcon.visibility = View.GONE
            }

            // Handle detail
            if (option.detail != null) {
                textDetail.text = option.detail
                textDetail.visibility = View.VISIBLE
            } else {
                textDetail.visibility = View.GONE
            }

            // Verify Switch is found - if not, try to find it
            var toggle = switchToggle
            if (toggle == null) {
                toggle = findSwitchRecursively(itemView)
            }

            // Handle toggle visibility and styling
            if (option.hasToggle && toggle != null) {
                // Ensure Switch has proper layout params and dimensions
                val switchLayoutParams = toggle.layoutParams
                if (switchLayoutParams != null) {
                    switchLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    switchLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    toggle.layoutParams = switchLayoutParams
                }
                
                // Set minimum dimensions to ensure visibility
                val density = itemView.context.resources.displayMetrics.density
                toggle.switchMinWidth = (36 * density).toInt()
                toggle.minHeight = (20 * density).toInt()
                
                // Set visibility immediately
                toggle.visibility = View.VISIBLE
                toggle.alpha = 1.0f
                toggle.isEnabled = true
                toggle.invalidate()
                toggle.requestLayout()
                
                // Set state and listener
                toggle.isChecked = option.toggleState
                toggle.setOnCheckedChangeListener { _, isChecked ->
                    onToggleChangedListener?.invoke(option.type, isChecked)
                }
                
                // Apply theme-based styling using utility function
                com.quizangomedia.messages.util.ThemeManager.applyToggleTheme(toggle, itemView.context)
                
                // CRITICAL: Force visibility multiple times to ensure it sticks
                // This is important because ThemeManager or layout operations might override it
                itemView.post {
                    toggle.visibility = View.VISIBLE
                    toggle.alpha = 1.0f
                    com.quizangomedia.messages.util.ThemeManager.applyToggleTheme(toggle, itemView.context)
                }
                itemView.postDelayed({
                    toggle.visibility = View.VISIBLE
                    toggle.alpha = 1.0f
                }, 50)
                itemView.postDelayed({
                    toggle.visibility = View.VISIBLE
                    toggle.alpha = 1.0f
                }, 150)
                itemView.postDelayed({
                    toggle.visibility = View.VISIBLE
                    toggle.alpha = 1.0f
                }, 300)
                
                // Make the whole item clickable but toggle handles its own clicks
                itemView.setOnClickListener(null)
            } else {
                // Hide toggle if it exists
                toggle?.visibility = View.GONE
                // Make item clickable
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
    
    companion object {
        // Helper function to recursively find Switch view
        private fun findSwitchRecursively(view: View): Switch? {
            if (view is Switch) {
                return view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    val found = findSwitchRecursively(child)
                    if (found != null) {
                        return found
                    }
                }
            }
            return null
        }
    }
}

