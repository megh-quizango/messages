package com.text.messages.sms.messanger.ui.caller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class QuickResponseAdapter(
    private val onResponseClick: (Int) -> Unit
) : ListAdapter<CallAfterViewModel.QuickResponse, QuickResponseAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_response, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textResponse: TextView = itemView.findViewById(R.id.textResponse)
        private val radioSelector: ImageView = itemView.findViewById(R.id.radioSelector)

        fun bind(response: CallAfterViewModel.QuickResponse, position: Int) {
            textResponse.text = response.text

            // Update radio selector state
            if (response.isSelected) {
                radioSelector.setImageResource(R.drawable.ic_radio_selected)
            } else {
                radioSelector.setImageResource(R.drawable.ic_radio_unselected)
            }

            // Handle click
            itemView.setOnClickListener {
                onResponseClick(position)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CallAfterViewModel.QuickResponse>() {
        override fun areItemsTheSame(
            oldItem: CallAfterViewModel.QuickResponse,
            newItem: CallAfterViewModel.QuickResponse
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: CallAfterViewModel.QuickResponse,
            newItem: CallAfterViewModel.QuickResponse
        ): Boolean {
            return oldItem.text == newItem.text && oldItem.isSelected == newItem.isSelected
        }
    }
}
