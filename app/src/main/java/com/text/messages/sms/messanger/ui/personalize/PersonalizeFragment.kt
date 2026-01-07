package com.text.messages.sms.messanger.ui.personalize

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.content.BroadcastReceiver
import com.text.messages.sms.messanger.databinding.FragmentPersonalizeBinding
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
        setupFontSection()
        setupBubbleSection()
        setupRingtoneSection()
        setupThemeMoreButton()
        setupFontMoreButton()
        setupBubbleMoreButton()
        setupRingtoneMoreButton()
        
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
    
    private fun setupThemeSection() {
        binding.layoutThemeSection.setOnClickListener {
            val intent = Intent(requireContext(), ThemesActivity::class.java)
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
}

