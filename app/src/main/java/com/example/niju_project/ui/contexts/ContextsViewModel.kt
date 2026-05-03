package com.example.niju_project.ui.contexts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.ContextModel
import com.example.niju_project.data.repository.ContextRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ContextsUiState {
    object Loading : ContextsUiState()
    data class Success(val contexts: List<ContextModel>) : ContextsUiState()
    data class Error(val message: String) : ContextsUiState()
}

class ContextsViewModel(
    private val repository: ContextRepository = ContextRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContextsUiState>(ContextsUiState.Loading)
    val uiState: StateFlow<ContextsUiState> = _uiState.asStateFlow()

    init {
        loadContexts()
    }

    fun loadContexts() {
        viewModelScope.launch {
            _uiState.value = ContextsUiState.Loading
            repository.getAllContexts().onSuccess { list ->
                _uiState.value = ContextsUiState.Success(list)
            }.onFailure {
                _uiState.value = ContextsUiState.Error(it.message ?: "Error desconocido")
            }
        }
    }
}
