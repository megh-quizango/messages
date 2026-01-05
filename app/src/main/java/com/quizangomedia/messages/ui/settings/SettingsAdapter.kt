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
            
            // Apply theme to section container background
            val themeColorLight = com.quizangomedia.messages.util.ThemeManager.getThemeColorLight(itemView.context)
            itemView.background?.mutate()?.let { drawable ->
                if (drawable is android.graphics.drawable.GradientDrawable) {
                    drawable.setColor(themeColorLight)
                }
            }
            
            // Clear existing views
            optionsContainer.removeAllViews()
            
            // Add each option to the container
            item.options.forEach { option ->
                val optionView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_settings_option, optionsContainer, false)
                
                val textTitle: TextView = optionView.findViewById(R.id.textTitle)
                val imageIcon: ImageView = optionView.findViewById(R.id.imageIcon)
                
                textTitle.text = option.title
                
                // Find Switch BEFORE adding to container - should work with inflated view
                var switchToggle: Switch? = optionView.findViewById(R.id.switchToggle)
                
                // If not found, try finding it recursively
                if (switchToggle == null) {
                    switchToggle = findSwitchRecursively(optionView)
                }
                
                // Add view to container first so it can be measured
                optionsContainer.addView(optionView)
                
                // Try finding Switch again AFTER adding to container
                if (switchToggle == null) {
                    switchToggle = optionView.findViewById(R.id.switchToggle)
                }
                
                // Last resort: find from container
                if (switchToggle == null) {
                    switchToggle = optionsContainer.findViewById(R.id.switchToggle)
                }
                
                // Handle toggle visibility AFTER adding to container
                // This ensures the view is in the hierarchy before we manipulate it
                val hasToggle = option.switchState != null
                
                if (switchToggle != null) {
                    // Ensure Switch has proper layout params and dimensions
                    val switchLayoutParams = switchToggle.layoutParams
                    if (switchLayoutParams != null) {
                        switchLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        switchLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        switchToggle.layoutParams = switchLayoutParams
                    }
                    
                    // Set minimum dimensions to ensure visibility
                    val density = itemView.context.resources.displayMetrics.density
                    switchToggle.switchMinWidth = (36 * density).toInt()
                    switchToggle.minHeight = (20 * density).toInt()
                    
                    if (hasToggle) {
                        // Force visibility and ensure it's not overridden
                        switchToggle.visibility = View.VISIBLE
                        switchToggle.alpha = 1.0f
                        switchToggle.isEnabled = true
                        switchToggle.invalidate()
                        switchToggle.requestLayout()
                    } else {
                        switchToggle.visibility = View.GONE
                    }
                }
                
                // Now set up icon and constraints using ConstraintSet for proper updates
                val constraintLayout = optionView as ConstraintLayout
                val constraintSet = ConstraintSet()
                constraintSet.clone(constraintLayout)
                
                // Convert 16dp to pixels for margin
                val margin16dp = (16 * itemView.context.resources.displayMetrics.density).toInt()
                
                // CRITICAL: Always ensure Switch constraints are preserved
                // This ensures the Switch stays visible and properly positioned
                // Only set constraints if switchToggle was found
                if (switchToggle != null) {
                    constraintSet.connect(R.id.switchToggle, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                    constraintSet.connect(R.id.switchToggle, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
                    constraintSet.connect(R.id.switchToggle, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
                }
                
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
                    if (switchToggle != null) {
                        constraintSet.connect(R.id.textTitle, ConstraintSet.END, R.id.switchToggle, ConstraintSet.START, margin16dp)
                    }
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
                    if (switchToggle != null) {
                        constraintSet.connect(R.id.textTitle, ConstraintSet.END, R.id.switchToggle, ConstraintSet.START, margin16dp)
                    }
                    constraintSet.applyTo(constraintLayout)
                    constraintLayout.requestLayout()
                }
                
                // Handle toggle state and styling (visibility already set above)
                if (hasToggle && switchToggle != null) {
                    // Set state and listener
                    switchToggle.isChecked = option.switchState ?: false
                    switchToggle.setOnCheckedChangeListener { _, isChecked ->
                        // Handle toggle state change
                        // TODO: Save preference
                    }
                    
                    // Apply theme-based styling using utility function
                    com.quizangomedia.messages.util.ThemeManager.applyToggleTheme(switchToggle, optionView.context)
                    
                    // CRITICAL: Force visibility multiple times to ensure it sticks
                    // This is important because ThemeManager or layout operations might override it
                    optionView.post {
                        switchToggle.visibility = View.VISIBLE
                        switchToggle.alpha = 1.0f
                        com.quizangomedia.messages.util.ThemeManager.applyToggleTheme(switchToggle, optionView.context)
                    }
                    optionView.postDelayed({
                        switchToggle.visibility = View.VISIBLE
                        switchToggle.alpha = 1.0f
                    }, 50)
                    optionView.postDelayed({
                        switchToggle.visibility = View.VISIBLE
                        switchToggle.alpha = 1.0f
                    }, 150)
                    optionView.postDelayed({
                        switchToggle.visibility = View.VISIBLE
                        switchToggle.alpha = 1.0f
                    }, 300)
                }
                
                optionView.setOnClickListener {
                    if (option.switchState == null) {
                        onOptionClick(option)
                    } else {
                        // Toggle the switch when clicking the row
                        switchToggle?.toggle()
                    }
                }
            }
        }
    }

    companion object {
        private const val TYPE_SECTION = 0
        
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
