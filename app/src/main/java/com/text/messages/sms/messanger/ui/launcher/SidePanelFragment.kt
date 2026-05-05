package com.text.messages.sms.messanger.ui.launcher

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment

class SidePanelFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val isLeft = requireArguments().getBoolean(ARG_IS_LEFT)
        return TextView(requireContext()).apply {
            text = if (isLeft) "Left panel (placeholder)" else "Right panel (placeholder)"
            gravity = Gravity.CENTER
        }
    }

    companion object {
        private const val ARG_IS_LEFT = "is_left"

        fun newInstance(isLeft: Boolean): SidePanelFragment {
            return SidePanelFragment().apply {
                arguments = bundleOf(ARG_IS_LEFT to isLeft)
            }
        }
    }
}

