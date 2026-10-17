package com.example.niju_project.ui

/** Estado genérico de una pantalla que carga datos. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val sessionExpired: Boolean = false) : UiState<Nothing>
}
