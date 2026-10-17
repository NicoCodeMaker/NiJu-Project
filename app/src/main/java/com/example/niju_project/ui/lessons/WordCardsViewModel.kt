package com.example.niju_project.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.LessonWord
import com.example.niju_project.data.remote.NijuErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CardsUiState {
    data object Loading : CardsUiState
    data class Card(
        val word: LessonWord,
        val number: Int,
        val total: Int
    ) : CardsUiState {
        val isFirst get() = number == 1
        val isLast get() = number == total
    }
    data class Error(val message: String, val sessionExpired: Boolean = false) : CardsUiState
}

/**
 * Tarjetas de presentación de una lección: el estudiante ve cada palabra con
 * su pronunciación, significado y ejemplo antes de hacer los ejercicios.
 */
class WordCardsViewModel(
    private val loadContent: suspend () -> Result<List<LessonWord>>,
    private val toggleFavorite: suspend (wordId: Int) -> Result<Boolean>
) : ViewModel() {

    private val _state = MutableStateFlow<CardsUiState>(CardsUiState.Loading)
    val uiState: StateFlow<CardsUiState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    /** Mensajes breves (p. ej. un error al marcar favorita). */
    val messages: StateFlow<String?> = _messages.asStateFlow()

    private var words: List<LessonWord> = emptyList()
    private var index = 0

    init { load() }

    fun load() {
        _state.value = CardsUiState.Loading
        viewModelScope.launch {
            loadContent()
                .onSuccess { list ->
                    words = list.sortedBy { it.position }
                    index = 0
                    if (words.isEmpty()) _state.value = CardsUiState.Error(NijuErrors.messageFor("no_words"))
                    else emit()
                }
                .onFailure {
                    _state.value = CardsUiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it))
                }
        }
    }

    fun next() { if (index < words.lastIndex) { index++; emit() } }

    fun previous() { if (index > 0) { index--; emit() } }

    fun toggleFavoriteCurrent() {
        val word = words.getOrNull(index) ?: return
        // Cambio optimista: se ve al instante y se revierte si el servidor falla.
        replace(word.copy(isFavorite = !word.isFavorite))
        viewModelScope.launch {
            toggleFavorite(word.wordId)
                .onSuccess { fav -> replace(currentById(word.wordId)?.copy(isFavorite = fav)) }
                .onFailure {
                    replace(currentById(word.wordId)?.copy(isFavorite = word.isFavorite))
                    _messages.value = NijuErrors.messageFor(it)
                }
        }
    }

    fun messageShown() { _messages.value = null }

    private fun currentById(id: Int) = words.firstOrNull { it.wordId == id }

    private fun replace(updated: LessonWord?) {
        if (updated == null) return
        words = words.map { if (it.wordId == updated.wordId) updated else it }
        emit()
    }

    private fun emit() {
        val w = words.getOrNull(index) ?: return
        _state.value = CardsUiState.Card(w, index + 1, words.size)
    }
}
