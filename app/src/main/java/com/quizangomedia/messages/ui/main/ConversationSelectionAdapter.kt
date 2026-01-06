package com.quizangomedia.messages.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.R
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.util.AvatarHelper
import de.hdodenhof.circleimageview.CircleImageView
import java.util.Calendar

class ConversationSelectionAdapter(
    private val selectedThreadIds: MutableSet<Long>,
    private val onConversationToggle: (Conversation, Boolean) -> Unit
) : ListAdapter<Conversation, ConversationSelectionAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_selection, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageContact: CircleImageView = itemView.findViewById(R.id.imageContact)
        private val textAvatarLetter: TextView = itemView.findViewById(R.id.textAvatarLetter)
        private val textContactName: TextView = itemView.findViewById(R.id.textContactName)
        private val textSnippet: TextView = itemView.findViewById(R.id.textSnippet)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)

        fun bind(conversation: Conversation) {
            textContactName.text = conversation.contactName ?: conversation.address
            textSnippet.text = conversation.snippet
            textTime.text = formatTime(conversation.date)
            
            checkBox.isChecked = selectedThreadIds.contains(conversation.threadId)
            
            // Load avatar
            AvatarHelper.loadAvatar(
                imageContact,
                textAvatarLetter,
                conversation.photoUri,
                conversation.contactName,
                conversation.address,
                itemView.context
            )
            
            itemView.setOnClickListener {
                val newChecked = !checkBox.isChecked
                checkBox.isChecked = newChecked
                onConversationToggle(conversation, newChecked)
            }
            
            checkBox.setOnClickListener {
                onConversationToggle(conversation, checkBox.isChecked)
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
            return oldItem.address == newItem.address &&
                   oldItem.snippet == newItem.snippet &&
                   oldItem.date == newItem.date &&
                   oldItem.unreadCount == newItem.unreadCount &&
                   oldItem.contactName == newItem.contactName
        }
    }
}

