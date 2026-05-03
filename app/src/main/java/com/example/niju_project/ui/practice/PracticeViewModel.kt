package com.example.niju_project.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
    private val savedStateHandle: SavedStateHandle,
    private val flashcardRepo: FlashcardRepository = FlashcardRepository(),
    private val progressRepo: ProgressRepository  = ProgressRepository(),
    private val userRepo: UserRepository           = UserRepository(),
    private val sm2: SpacedRepetitionService       = SpacedRepetitionService()
) : ViewModel() {

    private val _state = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _state.asStateFlow()

    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // 🔴 FIX CRÍTICO 1: leer contextId del SavedStateHandle (viene del Intent extra)
    private val contextId: String? = savedStateHandle["context_id"]

    private var allFlashcards = listOf<FlashcardModel>()
    private var questions    = listOf<FlashcardModel>()
    private var currentIdx   = 0
    private var correct      = 0

    init { loadQuestions() }

    private fun loadQuestions() {
        viewModelScope.launch {
            _state.value = PracticeUiState.Loading

            // 🔴 FIX CRÍTICO 1: usar subcolección si hay contextId, global si no
            val result = if (!contextId.isNullOrBlank()) {
                flashcardRepo.getFlashcardsByContext(contextId)
            } else {
                flashcardRepo.getAllFlashcards()
            }

            result.onSuccess { list ->
                if (list.isEmpty()) {
                    _state.value = PracticeUiState.Error("No hay tarjetas disponibles para esta práctica")
                    return@onSuccess
                }
                allFlashcards = list
                questions     = list.shuffled().take(10)
                currentIdx    = 0
                correct       = 0
                showNextQuestion()
            }.onFailure {
                _state.value = PracticeUiState.Error(it.message ?: "Error al cargar tarjetas")
            }
        }
    }

    private fun showNextQuestion() {
        if (currentIdx >= questions.size) { finishSession(); return }

        val q = questions[currentIdx]

        // 🔴 FIX CRÍTICO 2: opciones falsas REALES usando otras flashcards del contexto
        val falseOptions = allFlashcards
            .filter { it.id != q.id && it.english.isNotBlank() }
            .shuffled()
            .take(2)
            .map { it.english }

        // Rellenar si no hay suficientes distractores
        val distractors = falseOptions.toMutableList()
        while (distractors.size < 2) distractors.add("…")

        val options = (distractors + q.english).shuffled()

        _state.value = PracticeUiState.Question(
            flashcard = q,
            options   = options,
            index     = currentIdx + 1,
            total     = questions.size
        )
    }

    fun submitAnswer(answer: String) {
        if (currentIdx >= questions.size) return
        val q         = questions[currentIdx]
        val isCorrect = answer.trim() == q.english.trim()
        if (isCorrect) correct++

        viewModelScope.launch {
            if (uid.isNotEmpty()) {
                val existing = progressRepo.getProgress(uid, q.id).getOrNull()
                val updated  = sm2.update(existing, q.id, isCorrect)
                progressRepo.saveProgress(uid, updated)
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
                userRepo.updateStreak(uid)
            }
            _state.value = PracticeUiState.SessionResult(correct, questions.size, xpGained)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PracticeViewModel(savedStateHandle = createSavedStateHandle())
            }
        }
    }
}
