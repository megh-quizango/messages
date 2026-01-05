package com.quizangomedia.messages.ui.conversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quizangomedia.messages.R

class QuickMessageAdapter(
    private val messages: List<String>,
    private val onMessageClick: (String) -> Unit
) : RecyclerView.Adapter<QuickMessageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(message: String) {
            textView.text = message
            textView.setTextColor(itemView.context.getColor(android.R.color.black))
            itemView.setOnClickListener {
                onMessageClick(message)
            }
        }
    }
}

