package com.example.niju_project

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.niju_project.ui.practice.PracticeUiState
import com.example.niju_project.ui.practice.PracticeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PracticeActivity : AppCompatActivity() {

    private val viewModel: PracticeViewModel by viewModels()

    private lateinit var txtQuestion: TextView
    private lateinit var btnOption1: Button
    private lateinit var btnOption2: Button
    private lateinit var btnOption3: Button
    private lateinit var btnNext: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: LinearLayout

    private var selectedAnswer = ""
    private lateinit var optionButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        bindViews()
        setupNavigation()
        observeViewModel()
    }

    private fun bindViews() {
        txtQuestion = findViewById(R.id.txtQuestion)
        btnOption1 = findViewById(R.id.btnOption1)
        btnOption2 = findViewById(R.id.btnOption2)
        btnOption3 = findViewById(R.id.btnOption3)
        btnNext = findViewById(R.id.btnNext)
        progressBar = findViewById(R.id.progressBar)
        layoutContent = findViewById(R.id.layoutContent)

        optionButtons = listOf(btnOption1, btnOption2, btnOption3)

        optionButtons.forEach { button ->
            button.setOnClickListener {
                selectedAnswer = button.text.toString()
                updateButtonColors(button)
            }
        }

        btnNext.setOnClickListener {
            if (selectedAnswer.isEmpty()) {
                Toast.makeText(this, "Selecciona una respuesta", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.submitAnswer(selectedAnswer)
                selectedAnswer = ""
                resetButtonColors()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is PracticeUiState.Loading -> showLoading(true)
                    is PracticeUiState.Question -> {
                        showLoading(false)
                        displayQuestion(state)
                    }
                    is PracticeUiState.SessionResult -> {
                        showLoading(false)
                        showResult(state)
                    }
                    is PracticeUiState.Error -> {
                        showLoading(false)
                        Toast.makeText(this@PracticeActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun displayQuestion(state: PracticeUiState.Question) {
        txtQuestion.text = state.flashcard.spanish
        
        // Asignar opciones a los botones (asumiendo que hay 3 opciones máximo en el layout)
        state.options.forEachIndexed { index, option ->
            if (index < optionButtons.size) {
                optionButtons[index].visibility = View.VISIBLE
                optionButtons[index].text = option
            }
        }
        
        // Ocultar botones sobrantes si los hubiera
        for (i in state.options.size until optionButtons.size) {
            optionButtons[i].visibility = View.GONE
        }
    }

    private fun showResult(result: PracticeUiState.SessionResult) {
        // Podrías inflar un diálogo o cambiar el layout
        val message = "¡Sesión terminada!\nCorrectas: ${result.correct}/${result.total}\nXP Ganada: ${result.xpGained}"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        
        // Volver al Home tras un pequeño retraso o botón
        btnNext.text = "Finalizar"
        btnNext.setOnClickListener { finish() }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        layoutContent.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateButtonColors(selected: Button) {
        optionButtons.forEach { 
            it.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.darker_gray)) 
        }
        selected.backgroundTintList = ColorStateList.valueOf(getColor(R.color.teal_700))
    }

    private fun resetButtonColors() {
        optionButtons.forEach { 
            it.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.darker_gray)) 
        }
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navContexts).setOnClickListener {
            startActivity(Intent(this, ContextsActivity::class.java))
            finish()
        }
        // ... otros botones de nav
    }
}
