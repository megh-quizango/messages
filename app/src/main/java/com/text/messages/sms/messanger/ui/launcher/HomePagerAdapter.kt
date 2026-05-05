package com.text.messages.sms.messanger.ui.launcher

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class HomePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            PAGE_LEFT -> SidePanelFragment.newInstance(isLeft = true)
            PAGE_WORKSPACE -> HomeWorkspaceFragment.newInstance()
            PAGE_RIGHT -> SidePanelFragment.newInstance(isLeft = false)
            else -> HomeWorkspaceFragment.newInstance()
        }
    }

    companion object {
        const val PAGE_LEFT = 0
        const val PAGE_WORKSPACE = 1
        const val PAGE_RIGHT = 2
    }
}

