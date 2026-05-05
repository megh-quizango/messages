package com.text.messages.sms.messanger.ui.launcher

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.ui.launcher.apps.LaunchableAppsRepository
import com.text.messages.sms.messanger.ui.launcher.views.HomeScreenGrid
import com.text.messages.sms.messanger.util.AppDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeWorkspaceFragment : Fragment() {

    private var uiHost: HomeUiHost? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        uiHost = activity as? HomeUiHost
    }

    override fun onDetach() {
        uiHost = null
        super.onDetach()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home_workspace, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val grid = view.findViewById<HomeScreenGrid>(R.id.homeGrid)
        grid.onRequestOpenDrawer = { uiHost?.showAppDrawer() }

        // Show cached list immediately, refresh in background.
        grid.setApps(LaunchableAppsRepository.getCachedApps(requireContext()))
        viewLifecycleOwner.lifecycleScope.launch {
            val refreshed = withContext(AppDispatchers.io) {
                LaunchableAppsRepository.refreshApps(requireContext())
            }
            grid.setApps(refreshed)
        }
    }

    fun scrollToFirstPage() {
        view?.findViewById<HomeScreenGrid>(R.id.homeGrid)?.scrollToFirstPage()
    }

    companion object {
        const val TAG = "home_workspace"

        fun newInstance(): HomeWorkspaceFragment = HomeWorkspaceFragment()
    }
}

