package com.text.messages.sms.messanger.ui.blocking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.databinding.ItemBlockedContactBinding
import com.squareup.picasso.Picasso

class BlockedContactsAdapter(
    private val onUnblockClick: (CustomBlockingActivity.BlockedContact) -> Unit
) : ListAdapter<CustomBlockingActivity.BlockedContact, BlockedContactsAdapter.ContactViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemBlockedContactBinding.inflate(
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
        private val binding: ItemBlockedContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: CustomBlockingActivity.BlockedContact) {
            binding.textContactName.text = contact.name
            binding.textContactNumber.text = contact.phoneNumber

            if (contact.photoUri != null) {
                Picasso.get()
                    .load(contact.photoUri)
                    .into(binding.imageContact)
            } else {
                binding.imageContact.setImageResource(android.R.drawable.ic_menu_myplaces)
            }

            binding.textUnblock.setOnClickListener {
                onUnblockClick(contact)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CustomBlockingActivity.BlockedContact>() {
        override fun areItemsTheSame(
            oldItem: CustomBlockingActivity.BlockedContact,
            newItem: CustomBlockingActivity.BlockedContact
        ): Boolean {
            return oldItem.phoneNumber == newItem.phoneNumber
        }

        override fun areContentsTheSame(
            oldItem: CustomBlockingActivity.BlockedContact,
            newItem: CustomBlockingActivity.BlockedContact
        ): Boolean {
            return oldItem == newItem
        }
    }
}

