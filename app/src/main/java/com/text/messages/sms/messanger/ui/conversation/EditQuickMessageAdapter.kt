package com.text.messages.sms.messanger.ui.conversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class EditQuickMessageAdapter(
    private var messages: List<String>,
    private var isDeleteMode: Boolean,
    private val selectedItems: MutableSet<Int>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<EditQuickMessageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position], position, isDeleteMode, selectedItems.contains(position))
    }

    override fun getItemCount() = messages.size

    fun setDeleteMode(deleteMode: Boolean) {
        isDeleteMode = deleteMode
        notifyDataSetChanged()
    }

    fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position)
        } else {
            selectedItems.add(position)
        }
        notifyItemChanged(position)
    }

    fun moveItem(from: Int, to: Int) {
        val messagesList = messages.toMutableList()
        val item = messagesList.removeAt(from)
        messagesList.add(to, item)
        messages = messagesList
        notifyItemMoved(from, to)
    }

    fun updateMessages(newMessages: List<String>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    fun getMessages(): List<String> = messages

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val imageDragHandle: ImageView = itemView.findViewById(R.id.imageDragHandle)
        private val imageCheckbox: ImageView = itemView.findViewById(R.id.imageCheckbox)

        fun bind(message: String, position: Int, deleteMode: Boolean, isSelected: Boolean) {
            textMessage.text = message

            if (deleteMode) {
                imageDragHandle.visibility = View.GONE
                imageCheckbox.visibility = View.VISIBLE
                imageCheckbox.setImageResource(
                    if (isSelected) R.drawable.ic_checkbox_selected
                    else R.drawable.ic_checkbox_unselected
                )
                // Update constraint to use checkbox instead of drag handle
                val params = textMessage.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.startToEnd = R.id.imageCheckbox
                textMessage.layoutParams = params
            } else {
                imageDragHandle.visibility = View.VISIBLE
                imageCheckbox.visibility = View.GONE
                // Update constraint to use drag handle
                val params = textMessage.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.startToEnd = R.id.imageDragHandle
                textMessage.layoutParams = params
            }

            itemView.setOnClickListener {
                onItemClick(position)
            }
        }
    }
}

