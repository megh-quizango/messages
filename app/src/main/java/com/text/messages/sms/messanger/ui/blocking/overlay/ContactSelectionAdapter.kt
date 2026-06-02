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

    override fun onViewRecycled(holder: ContactViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingLoads()
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ContactSelectionActivity.ContactItem) {
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
                onItemClick(contact.phoneNumber)
            }

            // Show selection indicator with theme light color
            val themeColorLight = com.text.messages.sms.messanger.util.ThemeManager.getThemeColorLight(binding.root.context)
            if (isSelected(contact.phoneNumber)) {
                // Use theme light color directly as background
                binding.root.setBackgroundColor(themeColorLight)
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

