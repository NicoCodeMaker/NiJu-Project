package com.example.niju_project.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.FlashcardModel
import com.example.niju_project.data.repository.FlashcardRepository
import com.example.niju_project.data.repository.ProgressRepository
import com.example.niju_project.data.repository.UserRepository
import com.example.niju_project.domain.service.SpacedRepetitionService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Estado sellado - el compilador te avisa si olvidas manejar un caso
sealed class PracticeUiState {
    object Loading : PracticeUiState()
    data class Question(
        val flashcard: FlashcardModel,
        val options: List<String>,
        val index: Int,
        val total: Int
    ) : PracticeUiState()
    data class SessionResult(
        val correct: Int,
        val total: Int,
        val xpGained: Int
    ) : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}

class PracticeViewModel(
    private val flashcardRepo : FlashcardRepository = FlashcardRepository(),
    private val progressRepo  : ProgressRepository  = ProgressRepository(),
    private val userRepo      : UserRepository      = UserRepository(),
    private val sm2           : SpacedRepetitionService = SpacedRepetitionService()
) : ViewModel() {

    private val _state = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _state.asStateFlow()

    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var questions = listOf<FlashcardModel>()
    private var currentIdx = 0
    private var correct = 0

    init {
        loadQuestions()
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            _state.value = PracticeUiState.Loading
            val result = flashcardRepo.getAllFlashcards()
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    _state.value = PracticeUiState.Error("No hay tarjetas disponibles")
                } else {
                    questions = list.shuffled().take(10) // Ejemplo: 10 al azar
                    showNextQuestion()
                }
            }.onFailure {
                _state.value = PracticeUiState.Error(it.message ?: "Error desconocido")
            }
        }
    }

    private fun showNextQuestion() {
        if (currentIdx < questions.size) {
            val q = questions[currentIdx]
            // Aquí generarías opciones falsas para un quiz
            val options = listOf(q.back, "Opción B", "Opción C", "Opción D").shuffled()
            _state.value = PracticeUiState.Question(q, options, currentIdx + 1, questions.size)
        } else {
            finishSession()
        }
    }

    fun submitAnswer(answer: String) {
        val q = questions[currentIdx]
        val isCorrect = (answer == q.back)
        
        if (isCorrect) correct++

        // Algoritmo SM-2 (Fase 5)
        viewModelScope.launch {
            if (uid.isNotEmpty()) {
                // 1. Obtener progreso previo
                val existingResult = progressRepo.getProgress(uid, q.id)
                val existing = existingResult.getOrNull()
                
                // 2. Calcular nuevo estado con el motor SM-2
                val updatedProgress = sm2.update(existing, q.id, isCorrect)
                
                // 3. Persistir en Firestore
                progressRepo.saveProgress(uid, updatedProgress)
            }
        }

        currentIdx++
        showNextQuestion()
    }

    private fun finishSession() {
        viewModelScope.launch {
            val xpGained = correct * 10
            if (uid.isNotEmpty()) {
                userRepo.addXp(uid, xpGained)
            }
            _state.value = PracticeUiState.SessionResult(correct, questions.size, xpGained)
        }
    }
}
