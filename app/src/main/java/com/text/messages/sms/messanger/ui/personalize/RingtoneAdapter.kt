package com.text.messages.sms.messanger.ui.personalize

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class RingtoneAdapter(
    private val onClick: (RingtoneOption) -> Unit
) : ListAdapter<RingtoneOption, RingtoneAdapter.ViewHolder>(DiffCallback()) {

    private var selectedKey: String = "default"

    fun setSelectedKey(key: String) {
        val previousKey = selectedKey
        selectedKey = key
        currentList.indexOfFirst { it.key == previousKey }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
        currentList.indexOfFirst { it.key == key }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ringtone_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).key == selectedKey)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageRingtone: ImageView = itemView.findViewById(R.id.imageRingtone)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val selectionContainer: View = itemView.findViewById(R.id.selectionContainer)

        fun bind(option: RingtoneOption, selected: Boolean) {
            imageRingtone.setImageResource(option.imageRes)
            textName.text = option.title
            selectionContainer.visibility = if (selected) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(option) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RingtoneOption>() {
        override fun areItemsTheSame(oldItem: RingtoneOption, newItem: RingtoneOption): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: RingtoneOption, newItem: RingtoneOption): Boolean =
            oldItem == newItem
    }
}
