package com.text.messages.sms.messanger.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R
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
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textPhone: TextView = itemView.findViewById(R.id.textPhone)

        fun bind(contact: ContactItem) {
            textName.text = contact.name
            textPhone.text = contact.phoneNumber
            
            // Set different background colors for contact icons (cycling through colors)
            // Colors: pink (#FF6B9D), teal/green (#4ECDC4), light blue (#95E1D3), light blue (#E6F0FF)
            val colors = listOf("#FF6B9D", "#4ECDC4", "#95E1D3", "#E6F0FF")
            val colorIndex = contact.name.hashCode() % colors.size
            val color = colors[Math.abs(colorIndex)]
            
            // Set background color for the circle image view
            try {
                imageContact.setCircleBackgroundColor(android.graphics.Color.parseColor(color))
            } catch (e: Exception) {
                // Fallback if method doesn't exist
                imageContact.setBackgroundColor(android.graphics.Color.parseColor(color))
            }
            imageContact.setImageResource(R.drawable.contacts)
            
            itemView.setOnClickListener {
                onContactClick(contact)
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
