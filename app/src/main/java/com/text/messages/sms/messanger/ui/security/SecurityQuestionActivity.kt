package com.text.messages.sms.messanger.ui.security

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.text.messages.sms.messanger.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.text.messages.sms.messanger.R
import com.text.messages.sms.messanger.databinding.ActivitySecurityQuestionBinding
import com.text.messages.sms.messanger.util.ThemeManager

class SecurityQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivitySecurityQuestionBinding
    private lateinit var adapter: SecurityQuestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivitySecurityQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply theme
        ThemeManager.applyTheme(this, binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupRecyclerView()
    }
    
    private fun setupRecyclerView() {
        val questions = resources.getStringArray(R.array.security_questions).toList()
        
        adapter = SecurityQuestionAdapter(questions) { _ ->
            // Question selected
            finish()
        }
        
        binding.recyclerViewQuestions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewQuestions.adapter = adapter
    }
}

