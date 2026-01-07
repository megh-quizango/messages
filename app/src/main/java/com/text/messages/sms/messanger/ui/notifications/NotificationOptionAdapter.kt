package com.text.messages.sms.messanger.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class NotificationOptionAdapter(
    private val options: List<NotificationOption>,
    private val onOptionClick: (NotificationOption) -> Unit
) : ListAdapter<NotificationOption, NotificationOptionAdapter.ViewHolder>(DiffCallback()) {

    private var onWakeScreenToggleChanged: ((Boolean) -> Unit)? = null

    fun setOnWakeScreenToggleChangedListener(listener: (Boolean) -> Unit) {
        onWakeScreenToggleChanged = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewDivider: View = itemView.findViewById(R.id.viewDivider)
        private val imageIcon: ImageView = itemView.findViewById(R.id.imageIcon)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDetail: TextView = itemView.findViewById(R.id.textDetail)
        private val switchToggle: Switch = itemView.findViewById(R.id.switchToggle)

        fun bind(option: NotificationOption) {
            textTitle.text = option.title

            // Handle divider for Actions heading
            if (option.type == NotificationOptionType.ACTIONS_HEADING) {
                viewDivider.visibility = View.VISIBLE
            } else {
                viewDivider.visibility = View.GONE
            }

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

            // Handle toggle
            if (option.hasToggle) {
                switchToggle.visibility = View.VISIBLE
                switchToggle.isChecked = option.toggleState
                switchToggle.setOnCheckedChangeListener { _, isChecked ->
                    onWakeScreenToggleChanged?.invoke(isChecked)
                }
                // Make the whole item clickable but toggle handles its own clicks
                itemView.setOnClickListener(null)
            } else {
                switchToggle.visibility = View.GONE
                itemView.setOnClickListener {
                    onOptionClick(option)
                }
            }

            // For heading, make it non-clickable
            if (option.type == NotificationOptionType.ACTIONS_HEADING) {
                itemView.isClickable = false
                itemView.isFocusable = false
                itemView.background = null
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NotificationOption>() {
        override fun areItemsTheSame(oldItem: NotificationOption, newItem: NotificationOption): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: NotificationOption, newItem: NotificationOption): Boolean {
            return oldItem == newItem
        }
    }
}

