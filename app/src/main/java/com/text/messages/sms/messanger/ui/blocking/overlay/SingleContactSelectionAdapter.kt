package com.text.messages.sms.messanger.ui.blocking.overlay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.databinding.ItemContactBinding
import com.text.messages.sms.messanger.util.AvatarHelper
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

    override fun onViewRecycled(holder: ContactViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingLoads()
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: SingleContactSelectionActivity.ContactItem) {
            binding.textName.text = contact.name
            binding.textPhone.text = contact.phoneNumber

            resetAvatarState()
            AvatarHelper.loadAvatar(
                binding.imageContact,
                binding.textAvatarLetter,
                contact.photoUri,
                contact.name,
                contact.phoneNumber,
                binding.root.context
            )

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

        fun cancelPendingLoads() {
            Picasso.get().cancelRequest(binding.imageContact)
        }

        private fun resetAvatarState() {
            Picasso.get().cancelRequest(binding.imageContact)
            binding.imageContact.setImageDrawable(null)
            binding.imageContact.visibility = View.VISIBLE

            binding.textAvatarLetter.text = ""
            binding.textAvatarLetter.visibility = View.GONE
            binding.textAvatarLetter.background = null
            binding.textAvatarLetter.alpha = 1.0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                binding.textAvatarLetter.elevation = 0f
                binding.textAvatarLetter.translationZ = 0f
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

