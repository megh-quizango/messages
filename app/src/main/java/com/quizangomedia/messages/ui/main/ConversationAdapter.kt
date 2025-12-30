package com.quizangomedia.messages.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.quizangomedia.messages.R
import com.quizangomedia.messages.data.model.Conversation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textContactName: TextView = itemView.findViewById(R.id.textContactName)
        private val textSnippet: TextView = itemView.findViewById(R.id.textSnippet)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val textUnreadDot: View = itemView.findViewById(R.id.textUnreadDot)
        private val buttonStartChat: MaterialButton = itemView.findViewById(R.id.buttonStartChat)

        fun bind(conversation: Conversation) {
            textContactName.text = conversation.contactName ?: conversation.address
            textSnippet.text = conversation.snippet
            textTime.text = formatTime(conversation.date)
            
            // Show unread dot if there are unread messages
            if (conversation.unreadCount > 0) {
                textUnreadDot.visibility = View.VISIBLE
            } else {
                textUnreadDot.visibility = View.GONE
            }
            
            // Show "Start Chat" button for certain conversations (e.g., promotional messages)
            // This can be customized based on business logic
            val showStartChat = conversation.contactName == null || 
                               conversation.address.contains("VD-", ignoreCase = true) ||
                               conversation.snippet.contains("ALERT", ignoreCase = true)
            
            if (showStartChat && position == itemCount - 1) {
                buttonStartChat.visibility = View.VISIBLE
                buttonStartChat.setOnClickListener {
                    onConversationClick(conversation)
                }
            } else {
                buttonStartChat.visibility = View.GONE
            }
            
            itemView.setOnClickListener {
                onConversationClick(conversation)
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000} Min"
                diff < 86400000 -> {
                    val hours = diff / 3600000
                    if (hours < 24) "${hours}h" else "Yesterday"
                }
                else -> {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    days[dayOfWeek - 1]
                }
            }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.threadId == newItem.threadId
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem == newItem
        }
    }
}

