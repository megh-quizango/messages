package com.text.messages.sms.messanger.ui.launcher

import android.content.Intent
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.ui.launcher.model.LaunchableApp

class SimpleAppListAdapter : RecyclerView.Adapter<SimpleAppListAdapter.VH>() {

    private val items = ArrayList<LaunchableApp>()

    fun submit(newItems: List<LaunchableApp>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            setPadding(32, 24, 32, 24)
        }
        return VH(tv)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.textView.text = app.label
        holder.textView.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = app.componentName()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { holder.textView.context.startActivity(intent) }
        }
    }

    class VH(val textView: TextView) : RecyclerView.ViewHolder(textView)
}

