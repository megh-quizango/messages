package com.text.messages.sms.messanger.ui.manageapps

import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Color
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ItemBackgroundAppBinding

class BackgroundAppsAdapter(
    private val onStopClick: (String) -> Unit
) : ListAdapter<ManageAppsDetailActivity.BackgroundApp, BackgroundAppsAdapter.BackgroundAppViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackgroundAppViewHolder {
        val binding = ItemBackgroundAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BackgroundAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BackgroundAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BackgroundAppViewHolder(
        private val binding: ItemBackgroundAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: ManageAppsDetailActivity.BackgroundApp) {
            binding.textAppName.text = app.appName
            binding.imageAppIcon.setImageDrawable(app.icon)
            
            // Set background tint to null to allow theme override and remove shadow
            binding.buttonStop.backgroundTintList = null
            binding.buttonStop.elevation = 0f
            binding.buttonStop.stateListAnimator = null
            
            // Match the reference fixed-blue manage-apps action color.
            val manageAppsBlue = Color.parseColor("#0C56CF")
            binding.buttonStop.setTextColor(manageAppsBlue)
            
            // Apply theme to button background (transparent with themed stroke)
            binding.buttonStop.background?.mutate()?.let { drawable ->
                if (drawable is android.graphics.drawable.GradientDrawable) {
                    drawable.setStroke(2, manageAppsBlue)
                    drawable.setColor(android.graphics.Color.TRANSPARENT)
                }
            }
            
            if (app.isStopped) {
                binding.buttonStop.setText(R.string.manage_apps_stopped)
                binding.buttonStop.isEnabled = false
                binding.buttonStop.alpha = 0.6f
            } else {
                binding.buttonStop.setText(R.string.manage_apps_stop)
                binding.buttonStop.isEnabled = true
                binding.buttonStop.alpha = 1.0f
            }
            
            binding.buttonStop.setOnClickListener {
                if (!app.isStopped) {
                    onStopClick(app.packageName)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ManageAppsDetailActivity.BackgroundApp>() {
        override fun areItemsTheSame(
            oldItem: ManageAppsDetailActivity.BackgroundApp,
            newItem: ManageAppsDetailActivity.BackgroundApp
        ): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(
            oldItem: ManageAppsDetailActivity.BackgroundApp,
            newItem: ManageAppsDetailActivity.BackgroundApp
        ): Boolean {
            return oldItem == newItem
        }
    }
}

