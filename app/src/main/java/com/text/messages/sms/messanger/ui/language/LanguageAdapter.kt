package com.text.messages.sms.messanger.ui.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R

class LanguageAdapter(
    private var languages: List<LanguageItem>,
    private val onLanguageSelected: (String) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private var selectedPosition = languages.indexOfFirst { it.isSelected }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_language, parent, false)
        return LanguageViewHolder(view)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val language = languages[position]
        holder.bind(language, position == selectedPosition)
        
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onLanguageSelected(language.code)
        }
    }

    override fun getItemCount() = languages.size

    fun updateSelection(languageCode: String) {
        val newPosition = languages.indexOfFirst { it.code == languageCode }
        if (newPosition != -1 && newPosition != selectedPosition) {
            val previousPosition = selectedPosition
            selectedPosition = newPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
        }
    }

    class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textLanguage: TextView = itemView.findViewById(R.id.textLanguage)
        private val imageCheck: ImageView = itemView.findViewById(R.id.imageCheck)
        private val imageEmptyCheck: ImageView = itemView.findViewById(R.id.imageEmptyCheck)

        fun bind(language: LanguageItem, isSelected: Boolean) {
            textLanguage.text = language.displayName
            if (isSelected) {
                imageCheck.visibility = View.VISIBLE
                imageEmptyCheck.visibility = View.GONE
            } else {
                imageCheck.visibility = View.GONE
                imageEmptyCheck.visibility = View.VISIBLE
            }
        }
    }
}

