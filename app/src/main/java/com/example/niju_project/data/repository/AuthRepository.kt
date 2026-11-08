package com.example.niju_project.data.repository

import android.content.Intent
import com.example.niju_project.data.remote.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Azure
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Resultado del registro con correo. */
enum class SignUpOutcome {
    /** La cuenta quedó activa y con sesión (confirmación de correo desactivada en Supabase). */
    SIGNED_IN,
    /** Supabase envió un correo de confirmación; la sesión empieza al abrir el enlace. */
    CONFIRMATION_SENT
}

/**
 * Autenticación con Supabase Auth.
 *
 *  · Correo y contraseña: registro, inicio de sesión y recuperación por enlace.
 *    Supabase guarda solo el hash de la contraseña (bcrypt); la app nunca la almacena.
 *  · Microsoft (Azure) por OAuth. Google queda listo con AUTH_GOOGLE_ENABLED=true.
 *
 * Todos los enlaces de retorno (OAuth, confirmación de correo y recuperación) vuelven
 * a niju://login-callback, que recibe LoginActivity.
 */
class AuthRepository(private val client: SupabaseClient = SupabaseProvider.client) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    suspend fun awaitInitialization() = client.auth.awaitInitialization()

    fun isLoggedIn(): Boolean = client.auth.currentSessionOrNull() != null

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun currentEmail(): String? = client.auth.currentUserOrNull()?.email

    /**
     * Procesa el enlace niju://login-callback con el que vuelve el navegador o el correo.
     * [onRecovery] se llama si el enlace es de recuperación de contraseña: la sesión es
     * temporal y la app debe pedir la contraseña nueva.
     */
    fun handleRedirect(intent: Intent, onRecovery: () -> Unit = {}) {
        val isRecovery = AuthLinks.isRecovery(intent.data?.toString())
        client.handleDeeplinks(intent, onSessionSuccess = { session ->
            if (isRecovery || session.type == AuthLinks.TYPE_RECOVERY) onRecovery()
        })
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUpWithEmail(name: String, email: String, password: String): Result<SignUpOutcome> =
        runCatching {
            client.auth.signUpWith(Email, redirectUrl = REDIRECT_URL) {
                this.email = email.trim()
                this.password = password
                // El trigger handle_new_user copia "full_name" al perfil.
                data = buildJsonObject { put("full_name", name.trim()) }
            }
            if (client.auth.currentSessionOrNull() != null) SignUpOutcome.SIGNED_IN
            else SignUpOutcome.CONFIRMATION_SENT
        }

    /** Envía el enlace de recuperación. Por privacidad la app responde igual exista o no la cuenta. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email.trim(), redirectUrl = REDIRECT_URL)
    }

    /** Cambia la contraseña del usuario con sesión (p. ej. tras abrir el enlace de recuperación). */
    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        client.auth.updateUser { password = newPassword }
        Unit
    }

    suspend fun signInWithMicrosoft(): Result<Unit> = runCatching {
        client.auth.signInWith(Azure) {
            // Supabase necesita el alcance "email" para Microsoft.
            scopes.add("email")
        }
    }

    suspend fun signInWithGoogle(): Result<Unit> = runCatching {
        client.auth.signInWith(Google)
    }

    suspend fun signOut(): Result<Unit> = runCatching { client.auth.signOut() }

    companion object {
        val REDIRECT_URL = "${SupabaseProvider.AUTH_SCHEME}://${SupabaseProvider.AUTH_HOST}"
    }
}

/** Interpretación de los enlaces de Supabase Auth. Código puro: se prueba sin Android. */
object AuthLinks {
    const val TYPE_RECOVERY = "recovery"

    /** ¿El enlace (con #fragmento o ?query) es de recuperación de contraseña? */
    fun isRecovery(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val params = url.substringAfter('#', "").split('&') + url.substringAfter('?', "").substringBefore('#').split('&')
        return params.any { it == "type=$TYPE_RECOVERY" }
    }

    /** Mensaje de error del enlace (p. ej. enlace vencido), o null. */
    fun errorDescription(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val params = (url.substringAfter('#', "") + "&" + url.substringAfter('?', "").substringBefore('#'))
            .split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        val code = params["error_code"] ?: params["error"] ?: return null
        return when (code) {
            "otp_expired" -> "El enlace venció. Pide uno nuevo."
            "access_denied" -> "El enlace no es válido o ya se usó."
            else -> "No se pudo validar el enlace."
        }
    }
}
