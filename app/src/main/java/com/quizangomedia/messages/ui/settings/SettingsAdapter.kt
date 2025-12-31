package com.quizangomedia.messages.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.R

class SettingsAdapter(
    private val items: List<SettingsItem>,
    private val onOptionClick: (SettingsOption) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return TYPE_SECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings_section, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bindSection(item, onOptionClick)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textHeader: TextView = itemView.findViewById(R.id.textHeader)
        private val optionsContainer: ViewGroup = itemView.findViewById(R.id.optionsContainer)

        fun bindSection(item: SettingsItem, onOptionClick: (SettingsOption) -> Unit) {
            textHeader.text = item.title
            
            // Clear existing views
            optionsContainer.removeAllViews()
            
            // Add each option to the container
            item.options.forEach { option ->
                val optionView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_settings_option, optionsContainer, false)
                
                val textTitle: TextView = optionView.findViewById(R.id.textTitle)
                val imageIcon: ImageView = optionView.findViewById(R.id.imageIcon)
                val switchToggle: Switch = optionView.findViewById(R.id.switchToggle)
                
                textTitle.text = option.title
                
                // Add view to container first so it can be measured
                optionsContainer.addView(optionView)
                
                // Now set up icon and constraints using ConstraintSet for proper updates
                val constraintLayout = optionView as ConstraintLayout
                val constraintSet = ConstraintSet()
                constraintSet.clone(constraintLayout)
                
                // Convert 16dp to pixels for margin
                val margin16dp = (16 * itemView.context.resources.displayMetrics.density).toInt()
                
                if (option.iconRes != null) {
                    // Set icon resource first
                    imageIcon.setImageResource(option.iconRes)
                    
                    // Ensure icon constraints are set
                    constraintSet.connect(R.id.imageIcon, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    constraintSet.connect(R.id.imageIcon, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
                    constraintSet.connect(R.id.imageIcon, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
                    
                    // Constrain text to start after icon with explicit margin for spacing
                    constraintSet.clear(R.id.textTitle, ConstraintSet.START)
                    constraintSet.connect(R.id.textTitle, ConstraintSet.START, R.id.imageIcon, ConstraintSet.END, margin16dp)
                    constraintSet.applyTo(constraintLayout)
                    
                    // Make icon visible AFTER constraints are applied
                    imageIcon.visibility = View.VISIBLE
                    
                    // Force layout recalculation
                    constraintLayout.requestLayout()
                } else {
                    // When icon is gone, constrain text to parent start
                    imageIcon.visibility = View.GONE
                    constraintSet.clear(R.id.textTitle, ConstraintSet.START)
                    constraintSet.connect(R.id.textTitle, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    constraintSet.applyTo(constraintLayout)
                    constraintLayout.requestLayout()
                }
                
                switchToggle.visibility = if (option.switchState != null) View.VISIBLE else View.GONE
                if (option.switchState != null) {
                    switchToggle.isChecked = option.switchState
                    switchToggle.setOnCheckedChangeListener { _, isChecked ->
                        // Handle toggle state change
                        // TODO: Save preference
                    }
                }
                
                optionView.setOnClickListener {
                    if (option.switchState == null) {
                        onOptionClick(option)
                    } else {
                        // Toggle the switch when clicking the row
                        switchToggle.toggle()
                    }
                }
            }
        }
    }

    companion object {
        private const val TYPE_SECTION = 0
    }
}
