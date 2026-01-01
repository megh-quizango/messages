package com.quizangomedia.messages.ui.blocking.overlay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.databinding.ItemContactBinding
import com.squareup.picasso.Picasso

class ContactSelectionAdapter(
    private val onItemClick: (String) -> Unit,
    private val isSelected: (String) -> Boolean
) : ListAdapter<ContactSelectionActivity.ContactItem, ContactSelectionAdapter.ContactViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ContactSelectionActivity.ContactItem) {
            binding.textName.text = contact.name
            binding.textPhone.text = contact.phoneNumber

            if (contact.photoUri != null) {
                Picasso.get()
                    .load(contact.photoUri)
                    .into(binding.imageContact)
            } else {
                binding.imageContact.setImageResource(com.quizangomedia.messages.R.drawable.contact)
            }

            binding.root.setOnClickListener {
                onItemClick(contact.phoneNumber)
            }

            // Show selection indicator
            if (isSelected(contact.phoneNumber)) {
                binding.root.setBackgroundColor(0x1A0C56CF.toInt())
            } else {
                binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ContactSelectionActivity.ContactItem>() {
        override fun areItemsTheSame(
            oldItem: ContactSelectionActivity.ContactItem,
            newItem: ContactSelectionActivity.ContactItem
        ): Boolean {
            return oldItem.phoneNumber == newItem.phoneNumber
        }

        override fun areContentsTheSame(
            oldItem: ContactSelectionActivity.ContactItem,
            newItem: ContactSelectionActivity.ContactItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}

