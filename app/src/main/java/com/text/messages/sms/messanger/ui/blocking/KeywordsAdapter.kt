package com.text.messages.sms.messanger.ui.blocking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.databinding.ItemKeywordBinding

class KeywordsAdapter(
    private val onUnblockClick: (String) -> Unit
) : ListAdapter<String, KeywordsAdapter.KeywordViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
        val binding = ItemKeywordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return KeywordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class KeywordViewHolder(
        private val binding: ItemKeywordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(keyword: String) {
            binding.textKeyword.text = keyword
            binding.textUnblock.setOnClickListener {
                onUnblockClick(keyword)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}

