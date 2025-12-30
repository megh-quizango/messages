package com.quizangomedia.messages.ui.pin

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityPinBinding

class PinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding
    private var pinDigits = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupToolbar()
        setupKeypad()
        updatePinDots()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupKeypad() {
        // Number buttons
        binding.button0.setOnClickListener { addDigit("0") }
        binding.button1.setOnClickListener { addDigit("1") }
        binding.button2.setOnClickListener { addDigit("2") }
        binding.button3.setOnClickListener { addDigit("3") }
        binding.button4.setOnClickListener { addDigit("4") }
        binding.button5.setOnClickListener { addDigit("5") }
        binding.button6.setOnClickListener { addDigit("6") }
        binding.button7.setOnClickListener { addDigit("7") }
        binding.button8.setOnClickListener { addDigit("8") }
        binding.button9.setOnClickListener { addDigit("9") }
        
        // Delete button
        binding.buttonDelete.setOnClickListener {
            if (pinDigits.isNotEmpty()) {
                pinDigits.deleteCharAt(pinDigits.length - 1)
                updatePinDots()
            }
        }
    }
    
    private fun addDigit(digit: String) {
        if (pinDigits.length < 4) {
            pinDigits.append(digit)
            updatePinDots()
            
            if (pinDigits.length == 4) {
                // PIN complete, proceed to security question
                // TODO: Navigate to security question screen
            }
        }
    }
    
    private fun updatePinDots() {
        binding.dot1.alpha = if (pinDigits.length > 0) 1f else 0.3f
        binding.dot2.alpha = if (pinDigits.length > 1) 1f else 0.3f
        binding.dot3.alpha = if (pinDigits.length > 2) 1f else 0.3f
        binding.dot4.alpha = if (pinDigits.length > 3) 1f else 0.3f
    }
}

