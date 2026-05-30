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
import com.text.messages.sms.messanger.data.model.Conversation
import com.text.messages.sms.messanger.util.AvatarHelper
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AfterCallConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit
) : ListAdapter<Conversation, AfterCallConversationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_after_call_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageAvatar: CircleImageView = itemView.findViewById(R.id.imageAvatar)
        private val textAvatarLetter: TextView = itemView.findViewById(R.id.textAvatarLetter)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textSnippet: TextView = itemView.findViewById(R.id.textSnippet)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val viewUnreadDot: View = itemView.findViewById(R.id.viewUnreadDot)

        fun bind(conversation: Conversation) {
            textName.text = conversation.contactName?.takeIf { it.isNotBlank() } ?: conversation.address
            textSnippet.text = conversation.snippet.ifBlank { itemView.context.getString(R.string.messages) }
            textTime.text = formatTimestamp(conversation.date)
            viewUnreadDot.visibility = if (conversation.unreadCount > 0) View.VISIBLE else View.INVISIBLE

            AvatarHelper.loadAvatar(
                imageView = imageAvatar,
                textView = textAvatarLetter,
                photoUri = conversation.photoUri,
                contactName = conversation.contactName,
                address = conversation.address,
                context = itemView.context
            )

            itemView.setOnClickListener {
                onConversationClick(conversation)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp <= 0L) return ""

            val now = System.currentTimeMillis()
            val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(now - timestamp)
            if (diffMinutes in 0..59) {
                return itemView.context.getString(R.string.conversation_time_minutes_short, diffMinutes.coerceAtLeast(1))
            }

            val nowCalendar = Calendar.getInstance()
            val dateCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
            return when {
                nowCalendar.get(Calendar.YEAR) == dateCalendar.get(Calendar.YEAR) &&
                    nowCalendar.get(Calendar.DAY_OF_YEAR) == dateCalendar.get(Calendar.DAY_OF_YEAR) -> {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
                }
                else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.threadId == newItem.threadId
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem == newItem
        }
    }
}
