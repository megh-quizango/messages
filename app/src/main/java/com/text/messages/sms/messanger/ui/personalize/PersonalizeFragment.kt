package com.text.messages.sms.messanger.ui.personalize

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.content.BroadcastReceiver
import com.text.messages.sms.messanger.databinding.FragmentPersonalizeBinding
import com.text.messages.sms.messanger.ui.caller.CallerThemeActivity
import com.text.messages.sms.messanger.util.AppPreferences
import com.text.messages.sms.messanger.util.ThemeChangeHelper
import com.text.messages.sms.messanger.util.ThemeManager

class PersonalizeFragment : Fragment() {

    private var _binding: FragmentPersonalizeBinding? = null
    private val binding get() = _binding!!
    private var themeChangeReceiver: BroadcastReceiver? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Apply theme
        ThemeManager.applyTheme(requireContext(), binding.root)
        
        setupThemeSection()
        setupAfterCallThemeSection()
        setupFontSection()
        setupBubbleSection()
        setupRingtoneSection()
        setupThemeMoreButton()
        setupAfterCallThemeMoreButton()
        setupFontMoreButton()
        setupBubbleMoreButton()
        setupRingtoneMoreButton()
        renderAfterCallThemePreview()
        
        // Register theme change receiver
        themeChangeReceiver = ThemeChangeHelper.registerThemeChangeReceiver(this, binding.root)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        themeChangeReceiver?.let {
            requireContext().unregisterReceiver(it)
        }
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            renderAfterCallThemePreview()
        }
    }
    
    private fun setupThemeSection() {
        binding.layoutThemeSection.setOnClickListener {
            val intent = Intent(requireContext(), ThemesActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupAfterCallThemeSection() {
        binding.layoutAfterCallThemeSection.setOnClickListener {
            val intent = Intent(requireContext(), CallerThemeActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupFontSection() {
        binding.layoutFontSection.setOnClickListener {
            val intent = Intent(requireContext(), FontSizeActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupBubbleSection() {
        binding.layoutBubbleSection.setOnClickListener {
            val intent = Intent(requireContext(), BubbleActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupRingtoneSection() {
        binding.layoutRingtoneSection.setOnClickListener {
            val intent = Intent(requireContext(), RingtoneActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupThemeMoreButton() {
        binding.textThemeMore.setOnClickListener {
            val intent = Intent(requireContext(), ThemesActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupAfterCallThemeMoreButton() {
        binding.textAfterCallThemeMore.setOnClickListener {
            val intent = Intent(requireContext(), CallerThemeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun renderAfterCallThemePreview() {
        val container = binding.layoutAfterCallThemePreview
        container.removeAllViews()

        AppPreferences.callerThemes.forEach { theme ->
            val item = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(3.dp, 0, 3.dp, 0)
            }

            val swatch = TextView(requireContext()).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor(theme.topColor), Color.parseColor(theme.bottomColor))
                ).apply {
                    cornerRadius = 10.dp.toFloat()
                }
                text = if (theme.index == AppPreferences.getCallerThemeIndex(requireContext())) "✓" else ""
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 18f
            }

            item.addView(
                swatch,
                LinearLayout.LayoutParams(0, 58.dp).apply {
                    width = 34.dp
                }
            )

            container.addView(
                item,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }
    }
    
    private fun setupFontMoreButton() {
        binding.textFontMore.setOnClickListener {
            val intent = Intent(requireContext(), FontSizeActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupBubbleMoreButton() {
        binding.textBubbleMore.setOnClickListener {
            val intent = Intent(requireContext(), BubbleActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupRingtoneMoreButton() {
        binding.textRingtoneMore.setOnClickListener {
            val intent = Intent(requireContext(), RingtoneActivity::class.java)
            startActivity(intent)
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}

