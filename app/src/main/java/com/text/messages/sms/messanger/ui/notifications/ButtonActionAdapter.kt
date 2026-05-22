package com.text.messages.sms.messanger.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class ButtonActionAdapter(
    private val actions: List<ButtonAction>,
    private val selectedAction: ButtonAction,
    private val onActionSelected: (ButtonAction) -> Unit
) : ListAdapter<ButtonAction, ButtonActionAdapter.ViewHolder>(DiffCallback()) {

    private var selectedPosition = actions.indexOf(selectedAction).takeIf { it >= 0 } ?: 0
    private var onActionSelectedListener: ((ButtonAction) -> Unit)? = null

    fun setOnActionSelectedListener(listener: (ButtonAction) -> Unit) {
        onActionSelectedListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_swipe_action, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radioButton)
        private val textActionName: TextView = itemView.findViewById(R.id.textActionName)

        fun bind(action: ButtonAction, isSelected: Boolean) {
            textActionName.text = itemView.context.getString(action.displayNameRes)
            radioButton.isChecked = isSelected

            // Prevent direct radio button clicks
            radioButton.isClickable = false
            radioButton.isFocusable = false
            radioButton.isFocusableInTouchMode = false

            itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = adapterPosition
                
                if (previousPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(previousPosition)
                }
                notifyItemChanged(selectedPosition)
                
                onActionSelected(action)
                onActionSelectedListener?.invoke(action)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ButtonAction>() {
        override fun areItemsTheSame(
            oldItem: ButtonAction,
            newItem: ButtonAction
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: ButtonAction,
            newItem: ButtonAction
        ): Boolean {
            return oldItem == newItem
        }
    }
}

