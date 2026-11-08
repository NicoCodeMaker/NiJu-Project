package com.example.niju_project.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.ReviewOverview
import com.example.niju_project.data.model.ReviewWord
import com.example.niju_project.data.model.WordDetail
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewData(val overview: ReviewOverview, val words: List<ReviewWord>, val filter: String?)

/** Pantalla de Repaso: resumen SM-2 + lista filtrable + detalle de palabra. */
class ReviewViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ReviewData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReviewData>> = _state.asStateFlow()

    private val _detail = MutableStateFlow<UiState<WordDetail>?>(null)
    val detail: StateFlow<UiState<WordDetail>?> = _detail.asStateFlow()

    /** Filtro actual (null = todas). */
    var filter: String? = null
        private set

    fun load(newFilter: String? = filter) {
        filter = newFilter
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            val overview = async { repo.getReviewOverview() }
            val words = async { repo.getReviewWords(newFilter) }
            _state.value = runCatching {
                ReviewData(overview.await().getOrThrow(), words.await().getOrThrow(), newFilter)
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it)) }
            )
        }
    }

    fun openDetail(wordId: Int) {
        _detail.value = UiState.Loading
        viewModelScope.launch {
            _detail.value = repo.getWordDetail(wordId).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it)) }
            )
        }
    }

    fun closeDetail() { _detail.value = null }

    fun toggleFavorite(wordId: Int) {
        val current = (_detail.value as? UiState.Success)?.data ?: return
        _detail.value = UiState.Success(current.copy(isFavorite = !current.isFavorite))
        viewModelScope.launch {
            repo.toggleFavorite(wordId)
                .onSuccess { fav -> _detail.value = UiState.Success(current.copy(isFavorite = fav)) }
                .onFailure { _detail.value = UiState.Success(current) }
        }
    }
}
