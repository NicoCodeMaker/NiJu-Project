package com.example.niju_project.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.niju_project.data.remote.NijuErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel para pantallas que solo cargan y muestran un dato. */
class LoaderViewModel<T>(private val loader: suspend () -> Result<T>) : ViewModel() {

    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val uiState: StateFlow<UiState<T>> = _state.asStateFlow()

    /** Carga; si ya hay datos en pantalla, los mantiene mientras recarga. */
    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            loader()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    _state.value = UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it))
                }
        }
    }
}

/** Fábrica de ViewModels a partir de una lambda. */
inline fun <reified VM : ViewModel> factoryOf(crossinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }
