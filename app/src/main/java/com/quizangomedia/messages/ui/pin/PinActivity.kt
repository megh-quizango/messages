package com.quizangomedia.messages.ui.pin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.quizangomedia.messages.R
import com.quizangomedia.messages.databinding.ActivityPinBinding
import com.quizangomedia.messages.databinding.BottomSheetSecurityQuestionBinding
import com.quizangomedia.messages.databinding.BottomSheetSelectQuestionBinding
import com.quizangomedia.messages.databinding.ItemSecurityQuestionBinding
import com.quizangomedia.messages.ui.private.PrivateConversationsActivity

class PinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var pinDigits = StringBuilder()
    private var isCreatingPin = false

    companion object {
        private const val PREFS_NAME = "MessagesPrefs"
        private const val KEY_PIN = "user_pin"
        private const val KEY_SECURITY_QUESTION = "security_question"
        private const val KEY_SECURITY_ANSWER = "security_answer"
        
        fun hasPin(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_PIN, null) != null
        }
    }

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

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isCreatingPin = !hasPin(this)
        
        setupBackButton()
        setupKeypad()
        setupBannerAd()
        updateUI()
    }

    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupKeypad() {
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
        
        binding.buttonDelete.setOnClickListener {
            if (pinDigits.isNotEmpty()) {
                pinDigits.deleteCharAt(pinDigits.length - 1)
                updatePinDots()
            }
        }
    }

    private fun setupBannerAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewBanner.loadAd(adRequest)
    }

    private fun updateUI() {
        if (isCreatingPin) {
            binding.textPinInstruction.text = "Create your PIN"
        } else {
            binding.textPinInstruction.text = "Enter Your PIN"
        }
        updatePinDots()
    }

    private fun addDigit(digit: String) {
        if (pinDigits.length < 4) {
            pinDigits.append(digit)
            updatePinDots()
            
            if (pinDigits.length == 4) {
                handlePinComplete()
            }
        }
    }

    private fun handlePinComplete() {
        if (isCreatingPin) {
            // Save PIN and show security question bottom sheet
            showSecurityQuestionBottomSheet()
        } else {
            // Validate PIN
            val storedPin = sharedPreferences.getString(KEY_PIN, null)
            if (pinDigits.toString() == storedPin) {
                // PIN matched, navigate to PrivateConversationsActivity
                startActivity(Intent(this, PrivateConversationsActivity::class.java))
                finish()
            } else {
                // PIN incorrect, clear and show error
                pinDigits.clear()
                updatePinDots()
                // TODO: Show error message
            }
        }
    }

    private fun showSecurityQuestionBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetSecurityQuestionBinding.inflate(LayoutInflater.from(this))
        bottomSheet.setContentView(sheetBinding.root)

        sheetBinding.editTextQuestion.setOnClickListener {
            showSelectQuestionBottomSheet(sheetBinding)
        }

        sheetBinding.editTextAnswer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSaveButtonState(sheetBinding)
            }
        })

        sheetBinding.buttonSave.setOnClickListener {
            val question = sheetBinding.editTextQuestion.text.toString()
            val answer = sheetBinding.editTextAnswer.text.toString()
            
            if (question.isNotEmpty() && answer.isNotEmpty()) {
                // Save PIN and security question
                sharedPreferences.edit()
                    .putString(KEY_PIN, pinDigits.toString())
                    .putString(KEY_SECURITY_QUESTION, question)
                    .putString(KEY_SECURITY_ANSWER, answer)
                    .apply()
                
                bottomSheet.dismiss()
                
                // Clear PIN and show enter PIN screen
                pinDigits.clear()
                isCreatingPin = false
                updateUI()
            }
        }

        sheetBinding.buttonCancel.setOnClickListener {
            bottomSheet.dismiss()
            pinDigits.clear()
            updatePinDots()
        }

        updateSaveButtonState(sheetBinding)
        bottomSheet.show()
    }

    private fun showSelectQuestionBottomSheet(parentBinding: BottomSheetSecurityQuestionBinding) {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetSelectQuestionBinding.inflate(LayoutInflater.from(this))
        bottomSheet.setContentView(sheetBinding.root)
        
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

        val adapter = SecurityQuestionListAdapter(questions) { question ->
            parentBinding.editTextQuestion.setText(question)
            bottomSheet.dismiss()
            updateSaveButtonState(parentBinding)
        }

        sheetBinding.recyclerViewQuestions.layoutManager = LinearLayoutManager(this)
        sheetBinding.recyclerViewQuestions.adapter = adapter

        bottomSheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.show()
    }

    private fun updateSaveButtonState(sheetBinding: BottomSheetSecurityQuestionBinding) {
        val question = sheetBinding.editTextQuestion.text.toString()
        val answer = sheetBinding.editTextAnswer.text.toString()
        sheetBinding.buttonSave.isEnabled = question.isNotEmpty() && answer.isNotEmpty()
    }

    private fun updatePinDots() {
        binding.dot1.alpha = if (pinDigits.length > 0) 1f else 0.3f
        binding.dot2.alpha = if (pinDigits.length > 1) 1f else 0.3f
        binding.dot3.alpha = if (pinDigits.length > 2) 1f else 0.3f
        binding.dot4.alpha = if (pinDigits.length > 3) 1f else 0.3f
    }
}

class SecurityQuestionListAdapter(
    private val questions: List<String>,
    private val onQuestionClick: (String) -> Unit
) : RecyclerView.Adapter<SecurityQuestionListAdapter.QuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val binding = ItemSecurityQuestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(questions[position])
    }

    override fun getItemCount(): Int = questions.size

    inner class QuestionViewHolder(
        private val binding: ItemSecurityQuestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(question: String) {
            binding.textQuestion.text = question
            binding.root.setOnClickListener {
                onQuestionClick(question)
            }
        }
    }
}
