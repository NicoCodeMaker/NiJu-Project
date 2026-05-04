package com.example.niju_project.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.UserModel
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.UserRepository
import com.example.niju_project.utils.TOTPHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    data class Success(val secret: String, val email: String) : RegisterState()
    data class Error(val message: String) : RegisterState()
}

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    val registerState = MutableLiveData<RegisterState>(RegisterState.Idle)

    fun register(name: String, email: String, pass: String, confirm: String) {
        if (name.length < 2) {
            registerState.value = RegisterState.Error("Nombre muy corto")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            registerState.value = RegisterState.Error("Correo inválido")
            return
        }
        if (pass.length < 8 || !pass.any { it.isDigit() } || !pass.any { it.isUpperCase() }) {
            registerState.value = RegisterState.Error("Contraseña débil")
            return
        }
        if (pass != confirm) {
            registerState.value = RegisterState.Error("Las contraseñas no coinciden")
            return
        }

        registerState.value = RegisterState.Loading
        authRepository.signUpWithEmail(email, pass) { result ->
            result.onSuccess { firebaseUser ->
                authRepository.updateProfileAndVerify(firebaseUser, name) { profileResult ->
                    createUserInFirestore(firebaseUser, name)
                }
            }.onFailure {
                registerState.value = RegisterState.Error(it.message ?: "Error al registrar")
            }
        }
    }

    private fun createUserInFirestore(firebaseUser: FirebaseUser, name: String) {
        viewModelScope.launch {
            val secret = TOTPHelper.generateSecretKey()
            val newUser = UserModel(
                uid = firebaseUser.uid,
                name = name,
                email = firebaseUser.email ?: "",
                totpSecret = secret
            )
            
            userRepository.upsertUser(newUser).onSuccess {
                registerState.value = RegisterState.Success(secret, newUser.email)
            }.onFailure {
                registerState.value = RegisterState.Error("Error al guardar perfil")
            }
        }
    }
}
