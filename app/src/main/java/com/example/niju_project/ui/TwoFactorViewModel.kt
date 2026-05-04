package com.example.niju_project.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.repository.UserRepository
import com.example.niju_project.utils.TOTPHelper
import kotlinx.coroutines.launch

sealed class TwoFactorUiState {
    object Idle : TwoFactorUiState()
    object Loading : TwoFactorUiState()
    data class SecretReady(val secret: String) : TwoFactorUiState()
    object Success : TwoFactorUiState()
    data class Error(val message: String) : TwoFactorUiState()
}

class TwoFactorViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    val uiState = MutableLiveData<TwoFactorUiState>(TwoFactorUiState.Idle)

    fun fetchSecret(uid: String) {
        uiState.value = TwoFactorUiState.Loading
        viewModelScope.launch {
            userRepository.getCurrentUser(uid).onSuccess { user ->
                val secret = user?.totpSecret ?: ""

                uiState.value = TwoFactorUiState.SecretReady(secret)

            }.onFailure {
                uiState.value = TwoFactorUiState.Error("Error al obtener datos")
            }
        }
    }

    fun verifyCode(secret: String, code: String, isSetupMode: Boolean, uid: String?) {
        if (code.length != 6 || !code.all { it.isDigit() }) {
            uiState.value = TwoFactorUiState.Error("El código debe tener 6 dígitos")
            return
        }

        uiState.value = TwoFactorUiState.Loading

        if (secret.isEmpty()) {
            uiState.value = TwoFactorUiState.Error("Error interno: secreto inválido")
            return
        }

        val isValid = TOTPHelper.validateCode(secret, code)
        
        if (isValid) {
            if (isSetupMode && uid != null) {
                completeSetup(uid)
            } else {
                uiState.value = TwoFactorUiState.Success
            }
        } else {
            uiState.value = TwoFactorUiState.Error("Código incorrecto")
        }
    }

    private fun completeSetup(uid: String) {
        viewModelScope.launch {
            userRepository.updateTwoFactorEnabled(uid, true).onSuccess {
                fetchSecret(uid) // 🔥 recarga datos reales
                uiState.value = TwoFactorUiState.Success
            }.onFailure {
                uiState.value = TwoFactorUiState.Error("Error al activar 2FA")
            }
        }
    }

    fun saveSecret(uid: String, secret: String) {
        uiState.value = TwoFactorUiState.Loading

        viewModelScope.launch {
            runCatching<Unit> {
                userRepository.saveTotpSecret(uid, secret)
            }.onSuccess {
                uiState.value = TwoFactorUiState.SecretReady(secret)
            }.onFailure {
                uiState.value = TwoFactorUiState.Error("Error al guardar secreto")
            }
        }
    }

}
