package com.text.messages.sms.messanger.ui.settings

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

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
        
        companion object {
            private const val TAG = "SettingsAdapter"
        }

        fun bindSection(item: SettingsItem, onOptionClick: (SettingsOption) -> Unit) {
            textHeader.text = item.title
            
            // Apply theme to section container background
            val themeColorLight = com.text.messages.sms.messanger.util.ThemeManager.getThemeColorLight(itemView.context)
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
                val switchToggle: SwitchMaterial = optionView.findViewById(R.id.switchToggle)
                
                Log.d(TAG, "Binding option: ${option.title}")
                
                textTitle.text = option.title
                
                // Handle icon and spacing
                Log.d(TAG, "Option: ${option.title}, iconRes: ${option.iconRes}")
                val textParams = textTitle.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                val iconParams = imageIcon.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                
                // Convert 16dp to pixels for marginStart
                val spacingDp = 16
                val spacingPx = (spacingDp * itemView.context.resources.displayMetrics.density).toInt()
                
                if (option.iconRes != null) {
                    try {
                        imageIcon.setImageResource(option.iconRes)
                        imageIcon.visibility = View.VISIBLE
                        // Set TextView marginStart to create spacing between icon and text
                        // This is more reliable than relying on ImageView's marginEnd
                        textParams?.marginStart = spacingPx
                        textTitle.layoutParams = textParams
                        // Force layout update
                        optionView.requestLayout()
                        Log.d(TAG, "Icon set successfully for: ${option.title}, resourceId: ${option.iconRes}, text marginStart: ${textParams?.marginStart}px (${spacingDp}dp)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set icon for: ${option.title}, resourceId: ${option.iconRes}, error: ${e.message}", e)
                        imageIcon.visibility = View.GONE
                        textParams?.marginStart = 0
                        textTitle.layoutParams = textParams
                        optionView.requestLayout()
                    }
                } else {
                    Log.w(TAG, "No icon resource for: ${option.title}")
                    imageIcon.visibility = View.GONE
                    textParams?.marginStart = 0
                    textTitle.layoutParams = textParams
                    optionView.requestLayout()
                }
                
                // Log spacing info for debugging
                Log.d(TAG, "Spacing - Icon visibility: ${imageIcon.visibility}, icon marginEnd: ${iconParams?.marginEnd}, text marginStart: ${textParams?.marginStart}")
                
                // Add view to container first
                optionsContainer.addView(optionView)
                
                // 🚨 FIX 2: ALWAYS reset switch state
                val hasToggle = option.switchState != null
                Log.d(TAG, "Option: ${option.title}, hasToggle: $hasToggle, switchState: ${option.switchState}")
                
                if (hasToggle) {
                    Log.d(TAG, "Setting toggle VISIBLE for: ${option.title}")
                    switchToggle.visibility = View.VISIBLE
                    switchToggle.alpha = 1.0f
                    switchToggle.isChecked = option.switchState ?: false
                    Log.d(TAG, "After setting - visibility: ${switchToggle.visibility}, alpha: ${switchToggle.alpha}, checked: ${switchToggle.isChecked}, width: ${switchToggle.width}, height: ${switchToggle.height}")
                    
                    // Remove old listener before setting new one (RecyclerView rule)
                    switchToggle.setOnCheckedChangeListener(null)
                    switchToggle.setOnCheckedChangeListener { _, isChecked ->
                        // Handle toggle state change - save preference
                        when (option.title) {
                            "Color SIM card icons" -> {
                                com.text.messages.sms.messanger.util.AppPreferences.setColorSimCardIcons(
                                    itemView.context,
                                    isChecked
                                )
                                Log.d(TAG, "Saved Color SIM card icons preference: $isChecked")
                            }
                            "Contacts colored icons" -> {
                                // Handle contacts colored icons if needed
                                // For now, just log
                                Log.d(TAG, "Contacts colored icons toggled: $isChecked")
                            }
                        }
                    }
                    // Apply theme after view is added to container
                    Log.d(TAG, "Applying theme to toggle for: ${option.title}")
                    com.text.messages.sms.messanger.util.ThemeManager.applyToggleTheme(switchToggle, optionView.context)
                    Log.d(TAG, "After theme - visibility: ${switchToggle.visibility}, alpha: ${switchToggle.alpha}, width: ${switchToggle.width}, height: ${switchToggle.height}")
                    
                    // Item click toggles the switch
                    optionView.setOnClickListener {
                        switchToggle.toggle()
                    }
                } else {
                    Log.d(TAG, "Setting toggle GONE for: ${option.title}")
                    // 🚨 CRITICAL PART OF FIX 2
                    switchToggle.visibility = View.GONE
                    switchToggle.setOnCheckedChangeListener(null)
                    optionView.setOnClickListener {
                        onOptionClick(option)
                    }
                }
            }
        }
    }

    companion object {
        private const val TYPE_SECTION = 0
    }
}
