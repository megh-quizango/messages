package com.text.messages.sms.messanger.ui.security

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class SecurityQuestionAdapter(
    private val questions: List<String>,
    private val onQuestionSelected: (String) -> Unit
) : RecyclerView.Adapter<SecurityQuestionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_security_question, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(questions[position])
    }

    override fun getItemCount() = questions.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textQuestion: TextView = itemView as TextView

        fun bind(question: String) {
            textQuestion.text = question
            itemView.setOnClickListener {
                onQuestionSelected(question)
            }
        }
    }
}

