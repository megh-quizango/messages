package com.text.messages.sms.messanger.ui.caller

import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CallHistoryAdapter : ListAdapter<CallHistoryItem, CallHistoryAdapter.CallHistoryViewHolder>(CallHistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_history, parent, false)
        return CallHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CallHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCallType: ImageView = itemView.findViewById(R.id.imageCallType)
        private val textCallType: TextView = itemView.findViewById(R.id.textCallType)
        private val textCallDate: TextView = itemView.findViewById(R.id.textCallDate)
        private val textCallDuration: TextView = itemView.findViewById(R.id.textCallDuration)

        fun bind(item: CallHistoryItem) {
            // Set call type text and icon
            when (item.type) {
                CallLog.Calls.INCOMING_TYPE -> {
                    textCallType.text = "Incoming"
                    imageCallType.setImageResource(R.drawable.ic_call)
                    imageCallType.rotation = 0f
                }
                CallLog.Calls.OUTGOING_TYPE -> {
                    textCallType.text = "Outgoing"
                    imageCallType.setImageResource(R.drawable.ic_call)
                    imageCallType.rotation = 180f
                }
                CallLog.Calls.MISSED_TYPE -> {
                    textCallType.text = "Missed"
                    imageCallType.setImageResource(R.drawable.ic_call)
                    imageCallType.rotation = 0f
                    textCallType.setTextColor(itemView.context.getColor(R.color.holo_pink_dark))
                }
                else -> {
                    textCallType.text = "Unknown"
                }
            }

            // Set date/time
            textCallDate.text = formatCallDate(item.date)

            // Set duration
            if (item.duration > 0) {
                textCallDuration.text = formatDuration(item.duration)
                textCallDuration.visibility = View.VISIBLE
            } else {
                textCallDuration.visibility = View.GONE
            }
        }

        private fun formatCallDate(timestamp: Long): String {
            val date = Date(timestamp)
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

            calendar.time = date

            return when {
                isSameDay(calendar, today) -> {
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    "Today, ${timeFormat.format(date)}"
                }
                isSameDay(calendar, yesterday) -> {
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    "Yesterday, ${timeFormat.format(date)}"
                }
                calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    dateFormat.format(date)
                }
                else -> {
                    val dateFormat = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
                    dateFormat.format(date)
                }
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun formatDuration(seconds: Long): String {
            return when {
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> {
                    val minutes = seconds / 60
                    val secs = seconds % 60
                    if (secs > 0) "${minutes}m ${secs}s" else "${minutes}m"
                }
                else -> {
                    val hours = seconds / 3600
                    val minutes = (seconds % 3600) / 60
                    if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
                }
            }
        }
    }

    class CallHistoryDiffCallback : DiffUtil.ItemCallback<CallHistoryItem>() {
        override fun areItemsTheSame(oldItem: CallHistoryItem, newItem: CallHistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallHistoryItem, newItem: CallHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}

