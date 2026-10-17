package com.example.niju_project.data.repository

import android.content.Intent
import com.example.niju_project.data.remote.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Azure
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Inicio de sesión con proveedores OAuth de Supabase Auth.
 *
 * Microsoft (Azure) es el proveedor activo. Google queda listo: basta con
 * habilitarlo en Supabase y poner AUTH_GOOGLE_ENABLED=true en local.properties.
 *
 * El flujo abre el navegador, el usuario se identifica con Microsoft y
 * Supabase devuelve la sesión a la app mediante niju://login-callback.
 */
class AuthRepository(private val client: SupabaseClient = SupabaseProvider.client) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    suspend fun awaitInitialization() = client.auth.awaitInitialization()

    fun isLoggedIn(): Boolean = client.auth.currentSessionOrNull() != null

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun currentEmail(): String? = client.auth.currentUserOrNull()?.email

    /** Procesa el enlace niju://login-callback con el que vuelve el navegador. */
    fun handleRedirect(intent: Intent) = client.handleDeeplinks(intent)

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
}
