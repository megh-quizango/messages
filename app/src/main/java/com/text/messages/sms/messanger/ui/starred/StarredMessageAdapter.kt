package com.text.messages.sms.messanger.ui.starred

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StarredMessageAdapter(
    private val onItemClick: ((StarredMessageData) -> Unit)? = null
) : ListAdapter<StarredMessageData, StarredMessageAdapter.StarredMessageViewHolder>(StarredMessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StarredMessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_starred_message, parent, false)
        return StarredMessageViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: StarredMessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StarredMessageViewHolder(
        itemView: View,
        private val onItemClick: ((StarredMessageData) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageContact: CircleImageView = itemView.findViewById(R.id.imageContact)
        private val textContactName: TextView = itemView.findViewById(R.id.textContactName)
        private val textDate: TextView = itemView.findViewById(R.id.textDate)
        private val textMessagePreview: TextView = itemView.findViewById(R.id.textMessagePreview)

        fun bind(starredMessage: StarredMessageData) {
            textContactName.text = starredMessage.contactName ?: starredMessage.address
            textDate.text = formatDateTime(starredMessage.date)
            textMessagePreview.text = starredMessage.body
            
            // Set contact icon background color based on first letter
            val firstChar = (starredMessage.contactName ?: starredMessage.address).firstOrNull()?.uppercaseChar() ?: '?'
            val color = getColorForChar(firstChar)
            // Set the person icon with white color
            imageContact.setImageResource(R.drawable.ic_person)
            imageContact.setColorFilter(android.graphics.Color.WHITE)
            // Set background color - CircleImageView supports this via setCircleBackgroundColor
            imageContact.setCircleBackgroundColor(color)
            
            // Set click listener
            itemView.setOnClickListener {
                onItemClick?.invoke(starredMessage)
            }
        }
        
        private fun formatDateTime(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            val dateFormat = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
            return dateFormat.format(calendar.time)
        }
        
        private fun getColorForChar(char: Char): Int {
            val colors = intArrayOf(
                0xFFE91E63.toInt(), // Pink
                0xFF9C27B0.toInt(), // Purple
                0xFF673AB7.toInt(), // Deep Purple
                0xFF3F51B5.toInt(), // Indigo
                0xFF2196F3.toInt(), // Blue
                0xFF03A9F4.toInt(), // Light Blue
                0xFF00BCD4.toInt(), // Cyan
                0xFF009688.toInt(), // Teal
                0xFF4CAF50.toInt(), // Green
                0xFF8BC34A.toInt(), // Light Green
                0xFFCDDC39.toInt(), // Lime
                0xFFFFEB3B.toInt(), // Yellow
                0xFFFFC107.toInt(), // Amber
                0xFFFF9800.toInt(), // Orange
                0xFFFF5722.toInt()  // Deep Orange
            )
            return colors[char.code % colors.size]
        }
    }

    class StarredMessageDiffCallback : DiffUtil.ItemCallback<StarredMessageData>() {
        override fun areItemsTheSame(oldItem: StarredMessageData, newItem: StarredMessageData): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(oldItem: StarredMessageData, newItem: StarredMessageData): Boolean {
            return oldItem == newItem
        }
    }
}

