package com.quizangomedia.messages.ui.conversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.data.model.Message
import com.quizangomedia.messages.data.model.MessageType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class MessageListItem {
    data class MessageItem(val message: Message) : MessageListItem()
    data class DateHeader(val date: Long) : MessageListItem()
}

class MessageAdapter(
    private val onMessageLongClick: ((Message) -> Unit)? = null,
    private val onMessageClick: ((Message) -> Unit)? = null,
    private val onSelectionChanged: (() -> Unit)? = null
) : ListAdapter<MessageListItem, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val selectedMessages = mutableSetOf<Long>()
    private val starredMessages = mutableSetOf<Long>()
    private var isSelectionMode = false

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_DATE_HEADER = 2
    }
    
    fun setSelectedMessages(selected: Set<Long>) {
        selectedMessages.clear()
        selectedMessages.addAll(selected)
        notifyDataSetChanged()
    }
    
    fun setStarredMessages(starred: Set<Long>) {
        starredMessages.clear()
        starredMessages.addAll(starred)
        notifyDataSetChanged()
    }
    
    fun toggleSelection(messageId: Long) {
        if (selectedMessages.contains(messageId)) {
            selectedMessages.remove(messageId)
        } else {
            selectedMessages.add(messageId)
        }
        // Notify only the changed item
        val position = currentList.indexOfFirst { 
            it is MessageListItem.MessageItem && it.message.id == messageId 
        }
        if (position >= 0) {
            notifyItemChanged(position)
        }
        // Notify selection changed
        onSelectionChanged?.invoke()
    }
    
    fun getSelectedMessages(): Set<Long> = selectedMessages.toSet()
    
    fun clearSelection() {
        selectedMessages.clear()
        notifyDataSetChanged()
    }
    
    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            selectedMessages.clear()
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                MessageViewHolder(view, this)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                MessageViewHolder(view, this)
            }
            VIEW_TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MessageListItem.MessageItem -> {
                (holder as MessageViewHolder).bind(item.message, isSelectionMode)
            }
            is MessageListItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.date)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is MessageListItem.MessageItem -> {
                if (item.message.type == MessageType.SENT) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            }
            is MessageListItem.DateHeader -> VIEW_TYPE_DATE_HEADER
        }
    }

    class MessageViewHolder(
        itemView: View,
        private val adapter: MessageAdapter
    ) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val imageCheck: android.widget.ImageView = itemView.findViewById(R.id.imageCheck)
        private val imageStarBadge: android.widget.ImageView = itemView.findViewById(R.id.imageStarBadge)

        fun bind(message: Message, isSelectionMode: Boolean) {
            textMessage.text = message.body
            textTime.text = formatTime(message.date)
            
            // Show/hide checkmark based on selection
            val isSelected = adapter.selectedMessages.contains(message.id)
            if (isSelectionMode) {
                imageCheck.visibility = View.VISIBLE
                imageCheck.setImageResource(if (isSelected) R.drawable.ic_checkbox_selected else R.drawable.ic_checkbox_unselected)
            } else {
                imageCheck.visibility = View.GONE
            }
            
            // Show/hide star badge based on starred status
            val isStarred = adapter.starredMessages.contains(message.id)
            imageStarBadge.visibility = if (isStarred) View.VISIBLE else View.GONE
            
            // Handle clicks
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    // In selection mode, toggle selection
                    adapter.toggleSelection(message.id)
                    bind(message, isSelectionMode) // Refresh view
                } else {
                    adapter.onMessageClick?.invoke(message)
                }
            }
            
            itemView.setOnLongClickListener {
                adapter.onMessageLongClick?.invoke(message)
                true
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return timeFormat.format(calendar.time)
        }
    }

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDate: TextView = itemView.findViewById(R.id.textDate)

        fun bind(date: Long) {
            textDate.text = formatDate(date)
        }

        private fun formatDate(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }

            return when {
                isSameDay(messageDate, today) -> "Today"
                isSameDay(messageDate, today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
                messageDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                    val dateFormat = SimpleDateFormat("MMMM dd", Locale.getDefault())
                    dateFormat.format(messageDate.time)
                }
                else -> {
                    val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                    dateFormat.format(messageDate.time)
                }
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageListItem>() {
        override fun areItemsTheSame(oldItem: MessageListItem, newItem: MessageListItem): Boolean {
            return when {
                oldItem is MessageListItem.MessageItem && newItem is MessageListItem.MessageItem ->
                    oldItem.message.id == newItem.message.id
                oldItem is MessageListItem.DateHeader && newItem is MessageListItem.DateHeader ->
                    oldItem.date == newItem.date
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MessageListItem, newItem: MessageListItem): Boolean {
            return when {
                oldItem is MessageListItem.MessageItem && newItem is MessageListItem.MessageItem -> {
                    oldItem.message.id == newItem.message.id &&
                    oldItem.message.body == newItem.message.body &&
                    oldItem.message.date == newItem.message.date &&
                    oldItem.message.type == newItem.message.type &&
                    oldItem.message.status == newItem.message.status
                }
                oldItem is MessageListItem.DateHeader && newItem is MessageListItem.DateHeader ->
                    oldItem.date == newItem.date
                else -> false
            }
        }
    }
}
