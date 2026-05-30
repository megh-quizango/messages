package com.text.messages.sms.messanger.ui.caller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class AfterCallActionAdapter(
    private val onActionClick: (AfterCallActionItem) -> Unit
) : ListAdapter<AfterCallActionItem, AfterCallActionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_after_call_action, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageActionIcon: ImageView = itemView.findViewById(R.id.imageActionIcon)
        private val textActionTitle: TextView = itemView.findViewById(R.id.textActionTitle)

        fun bind(item: AfterCallActionItem) {
            imageActionIcon.setImageResource(item.iconRes)
            textActionTitle.text = item.title
            itemView.setOnClickListener { onActionClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AfterCallActionItem>() {
        override fun areItemsTheSame(oldItem: AfterCallActionItem, newItem: AfterCallActionItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AfterCallActionItem, newItem: AfterCallActionItem): Boolean {
            return oldItem == newItem
        }
    }
}

data class AfterCallActionItem(
    val id: String,
    @DrawableRes val iconRes: Int,
    val title: String
)
