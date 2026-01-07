package com.text.messages.sms.messanger.ui.blocking.overlay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.databinding.ItemContactBinding
import com.squareup.picasso.Picasso

class SingleContactSelectionAdapter(
    private val onItemClick: (SingleContactSelectionActivity.ContactItem) -> Unit
) : ListAdapter<SingleContactSelectionActivity.ContactItem, SingleContactSelectionAdapter.ContactViewHolder>(DiffCallback()) {

    private var selectedContact: SingleContactSelectionActivity.ContactItem? = null

    fun setSelectedContact(contact: SingleContactSelectionActivity.ContactItem) {
        val previousSelected = selectedContact
        selectedContact = contact
        previousSelected?.let { notifyItemChanged(currentList.indexOf(it)) }
        notifyItemChanged(currentList.indexOf(contact))
    }

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

        fun bind(contact: SingleContactSelectionActivity.ContactItem) {
            binding.textName.text = contact.name
            binding.textPhone.text = contact.phoneNumber

            if (contact.photoUri != null) {
                Picasso.get()
                    .load(contact.photoUri)
                    .into(binding.imageContact)
            } else {
                binding.imageContact.setImageResource(com.text.messages.sms.messanger.R.drawable.contacts)
            }

            binding.root.setOnClickListener {
                onItemClick(contact)
            }

            // Show selection indicator
            if (selectedContact?.phoneNumber == contact.phoneNumber) {
                binding.root.setBackgroundColor(0x1A0C56CF.toInt())
            } else {
                binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SingleContactSelectionActivity.ContactItem>() {
        override fun areItemsTheSame(
            oldItem: SingleContactSelectionActivity.ContactItem,
            newItem: SingleContactSelectionActivity.ContactItem
        ): Boolean {
            return oldItem.phoneNumber == newItem.phoneNumber
        }

        override fun areContentsTheSame(
            oldItem: SingleContactSelectionActivity.ContactItem,
            newItem: SingleContactSelectionActivity.ContactItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}

