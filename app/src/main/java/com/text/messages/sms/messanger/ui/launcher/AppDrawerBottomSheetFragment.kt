package com.text.messages.sms.messanger.ui.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.ui.launcher.apps.LaunchableAppsRepository
import com.text.messages.sms.messanger.util.AppDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerBottomSheetFragment : BottomSheetDialogFragment() {

    private val adapter = SimpleAppListAdapter()

    override fun getTheme(): Int = R.style.Theme_Messages_Dialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_app_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.appDrawerList)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        adapter.submit(LaunchableAppsRepository.getCachedApps(requireContext()))

        viewLifecycleOwner.lifecycleScope.launch {
            val refreshed = withContext(AppDispatchers.io) {
                LaunchableAppsRepository.refreshApps(requireContext())
            }
            adapter.submit(refreshed)
        }
    }

    companion object {
        fun newInstance(): AppDrawerBottomSheetFragment = AppDrawerBottomSheetFragment()
    }
}
