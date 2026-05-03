package com.example.niju_project.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.UserModel
import com.example.niju_project.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val user: UserModel) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            userRepository.getCurrentUser(uid).onSuccess { user ->
                if (user != null) {
                    _uiState.value = HomeUiState.Success(user)
                } else {
                    _uiState.value = HomeUiState.Error("Usuario no encontrado")
                }
            }.onFailure {
                _uiState.value = HomeUiState.Error(it.message ?: "Error desconocido")
            }
        }
    }
}
