package com.text.messages.sms.messanger.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.util.AvatarHelper
import de.hdodenhof.circleimageview.CircleImageView

sealed class ContactListItem {
    data class SectionHeader(val letter: String) : ContactListItem()
    data class Contact(val contact: ContactItem) : ContactListItem()
}

class ContactAdapter(
    private val onContactClick: (ContactItem) -> Unit
) : ListAdapter<ContactListItem, RecyclerView.ViewHolder>(ContactDiffCallback()) {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_CONTACT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ContactListItem.SectionHeader -> TYPE_SECTION_HEADER
            is ContactListItem.Contact -> TYPE_CONTACT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_contact_section_header, parent, false)
                SectionHeaderViewHolder(view)
            }
            TYPE_CONTACT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_contact, parent, false)
                ContactViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ContactListItem.SectionHeader -> {
                (holder as SectionHeaderViewHolder).bind(item.letter)
            }
            is ContactListItem.Contact -> {
                (holder as ContactViewHolder).bind(item.contact)
            }
        }
    }

    inner class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textLetter: TextView = if (itemView is TextView) {
            itemView
        } else {
            itemView.findViewById(R.id.textLetter)
        }

        fun bind(letter: String) {
            textLetter.text = letter
        }
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageContact: CircleImageView = itemView.findViewById(R.id.imageContact)
        private val textAvatarLetter: TextView = itemView.findViewById(R.id.textAvatarLetter)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textPhone: TextView = itemView.findViewById(R.id.textPhone)

        fun bind(contact: ContactItem) {
            textName.text = contact.name
            textPhone.text = contact.phoneNumber
            
            // Reset avatar state first to prevent showing stale data
            resetAvatarState()
            
            // Load avatar with contact image, first letter, or icon (same as main activity)
            AvatarHelper.loadAvatar(
                imageContact,
                textAvatarLetter,
                contact.photoUri,
                contact.name,
                contact.phoneNumber,
                itemView.context
            )
            
            itemView.setOnClickListener {
                onContactClick(contact)
            }
        }
        
        private fun resetAvatarState() {
            // Cancel any pending Picasso requests
            com.squareup.picasso.Picasso.get().cancelRequest(imageContact)
            
            // Reset image view to a clean state
            imageContact.setImageDrawable(null)
            imageContact.visibility = View.VISIBLE
            
            // Reset text view to a clean state
            textAvatarLetter.text = ""
            textAvatarLetter.visibility = View.GONE
            textAvatarLetter.background = null
            textAvatarLetter.alpha = 1.0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                textAvatarLetter.elevation = 0f
                textAvatarLetter.translationZ = 0f
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<ContactListItem>() {
        override fun areItemsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            return when {
                oldItem is ContactListItem.SectionHeader && newItem is ContactListItem.SectionHeader ->
                    oldItem.letter == newItem.letter
                oldItem is ContactListItem.Contact && newItem is ContactListItem.Contact ->
                    oldItem.contact.phoneNumber == newItem.contact.phoneNumber
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            return oldItem == newItem
        }
    }
}
