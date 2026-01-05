package com.quizangomedia.messages.ui.security

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivitySecurityQuestionBinding
import com.quizangomedia.messages.util.ThemeManager

class SecurityQuestionActivity : AppCompatActivity() {

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
        val questions = listOf(
            "What's your favorite color?",
            "What's your pet's name?",
            "What's your lucky number?",
            "What's your favorite sport?",
            "What's your favorite book?",
            "What month was your first child born?",
            "What year was your father born?",
            "What year was your mother born?",
            "What's your mother's maiden name?",
            "What was the make of your first car?"
        )
        
        adapter = SecurityQuestionAdapter(questions) { question ->
            // Question selected
            finish()
        }
        
        binding.recyclerViewQuestions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewQuestions.adapter = adapter
    }
}

