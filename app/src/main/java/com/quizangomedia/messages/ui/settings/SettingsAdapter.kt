package com.quizangomedia.messages.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.R

class SettingsAdapter(
    private val items: List<SettingsItem>,
    private val onOptionClick: (SettingsOption) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 || items.subList(0, position).sumOf { it.options.size + 1 } == position) {
            TYPE_HEADER
        } else {
            TYPE_OPTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == TYPE_HEADER) {
            R.layout.item_settings_header
        } else {
            R.layout.item_settings_option
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        var currentPosition = position
        var itemIndex = 0
        
        for (item in items) {
            if (currentPosition == 0) {
                holder.bindHeader(item.title)
                return
            }
            currentPosition--
            
            if (currentPosition < item.options.size) {
                holder.bindOption(item.options[currentPosition], onOptionClick)
                return
            }
            currentPosition -= item.options.size
        }
    }

    override fun getItemCount(): Int {
        return items.size + items.sumOf { it.options.size }
    }

    class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val textHeader: TextView? = itemView.findViewById(R.id.textHeader)
        private val textTitle: TextView? = itemView.findViewById(R.id.textTitle)
        private val imageIcon: ImageView? = itemView.findViewById(R.id.imageIcon)
        private val switchToggle: Switch? = itemView.findViewById(R.id.switchToggle)

        fun bindHeader(title: String) {
            textHeader?.text = title
        }

        fun bindOption(option: SettingsOption, onOptionClick: (SettingsOption) -> Unit) {
            textTitle?.text = option.title
            imageIcon?.setImageResource(option.iconRes ?: 0)
            imageIcon?.visibility = if (option.iconRes != null) View.VISIBLE else View.GONE
            
            switchToggle?.visibility = if (option.switchState != null) View.VISIBLE else View.GONE
            switchToggle?.isChecked = option.switchState ?: false
            
            itemView.setOnClickListener {
                onOptionClick(option)
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_OPTION = 1
    }
}

