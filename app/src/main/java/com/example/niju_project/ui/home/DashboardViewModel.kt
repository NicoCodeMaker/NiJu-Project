package com.example.niju_project.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.Dashboard
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Datos de Inicio y Perfil (función get_dashboard del servidor). */
class DashboardViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Dashboard>>(UiState.Loading)
    val uiState: StateFlow<UiState<Dashboard>> = _state.asStateFlow()

    fun load() {
        // Mantiene los datos anteriores en pantalla mientras recarga.
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            repo.getDashboard()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    _state.value = UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it))
                }
        }
    }
}
