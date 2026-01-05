package com.quizangomedia.messages.ui.blocking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.data.model.Conversation
import com.quizangomedia.messages.databinding.ItemBlockedConversationBinding
import com.squareup.picasso.Picasso

class BlockedConversationsAdapter(
    private val onUnblockClick: (Long) -> Unit
) : ListAdapter<Conversation, BlockedConversationsAdapter.ConversationViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemBlockedConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(
        private val binding: ItemBlockedConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            binding.textContactName.text = conversation.contactName ?: conversation.address
            binding.textSnippet.text = conversation.snippet

            if (conversation.photoUri != null) {
                Picasso.get()
                    .load(conversation.photoUri)
                    .into(binding.imageContact)
            } else {
                binding.imageContact.setImageResource(com.quizangomedia.messages.R.drawable.contacts)
            }

            binding.textUnblock.setOnClickListener {
                onUnblockClick(conversation.threadId)
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

