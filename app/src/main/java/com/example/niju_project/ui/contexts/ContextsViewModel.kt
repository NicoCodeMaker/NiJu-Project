package com.example.niju_project.ui.contexts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.ContextProgress
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Catálogo de contextos (pantalla Contextos). */
class ContextsViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ContextProgress>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ContextProgress>>> = _state.asStateFlow()

    init { load() }

    /** Contextos con su avance real (lecciones superadas / total). Recarga también al volver a la pantalla. */
    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            repo.getContextProgress()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    _state.value = UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it))
                }
        }
    }
}

/** Avance por contexto (pantalla Ruta). */
class RutaViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ContextProgress>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ContextProgress>>> = _state.asStateFlow()

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            repo.getContextProgress()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    _state.value = UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it))
                }
        }
    }
}
