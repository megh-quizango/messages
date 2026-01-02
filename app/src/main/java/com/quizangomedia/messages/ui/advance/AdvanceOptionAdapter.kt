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
        private val switchToggle: Switch = itemView.findViewById(R.id.switchToggle)

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

            // Handle toggle
            if (option.hasToggle) {
                switchToggle.visibility = View.VISIBLE
                switchToggle.isChecked = option.toggleState
                switchToggle.setOnCheckedChangeListener { _, isChecked ->
                    onToggleChangedListener?.invoke(option.type, isChecked)
                }
                // Make the whole item clickable but toggle handles its own clicks
                itemView.setOnClickListener(null)
            } else {
                switchToggle.visibility = View.GONE
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

