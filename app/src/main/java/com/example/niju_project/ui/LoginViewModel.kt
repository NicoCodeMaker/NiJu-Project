package com.example.niju_project.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.UserModel
import com.example.niju_project.data.repository.AuthRepository
import com.example.niju_project.data.repository.UserRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val user: UserModel) : LoginState()
    data class Require2FA(val secret: String, val email: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    val loginState = MutableLiveData<LoginState>(LoginState.Idle)

    fun checkCurrentSession() {
        val user = authRepository.getCurrentUser()
        if (user != null) {
            checkUserDocument(user)
        }
    }

    fun loginWithEmail(email: String, pass: String) {
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            loginState.value = LoginState.Error("Correo inválido")
            return
        }
        if (pass.isEmpty() || pass.length < 6) {
            loginState.value = LoginState.Error("Contraseña inválida (mín. 6 caracteres)")
            return
        }

        loginState.value = LoginState.Loading
        authRepository.signInWithEmail(email, pass) { result ->
            result.onSuccess { firebaseUser ->
                checkUserDocument(firebaseUser)
            }.onFailure {
                loginState.value = LoginState.Error(it.message ?: "Error de autenticación")
            }
        }
    }

    fun loginWithCredential(credential: AuthCredential) {
        loginState.value = LoginState.Loading
        authRepository.signInWithCredential(credential) { result ->
            result.onSuccess { firebaseUser ->
                checkUserDocument(firebaseUser)
            }.onFailure {
                loginState.value = LoginState.Error(it.message ?: "Error con Google")
            }
        }
    }

    private fun checkUserDocument(firebaseUser: FirebaseUser) {
        viewModelScope.launch {
            userRepository.checkOrCreateUser(firebaseUser).onSuccess { user ->
                if (user.twoFactorEnabled && user.totpSecret.isNotEmpty()) {
                    loginState.value = LoginState.Require2FA(user.totpSecret, user.email)
                } else {
                    loginState.value = LoginState.Success(user)
                }
            }.onFailure {
                authRepository.signOut()
                loginState.value = LoginState.Error("Error al validar datos de usuario")
            }
        }
    }

    fun sendPasswordReset(email: String) {
        authRepository.sendPasswordReset(email) { result ->
            result.onFailure {
                loginState.value = LoginState.Error(it.message ?: "Error al enviar correo")
            }
        }
    }
}
