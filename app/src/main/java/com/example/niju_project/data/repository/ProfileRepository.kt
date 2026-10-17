package com.example.niju_project.data.repository

import com.example.niju_project.data.model.Profile
import com.example.niju_project.data.remote.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

class ProfileRepository(private val client: SupabaseClient = SupabaseProvider.client) {

    private fun uid(): String =
        client.auth.currentUserOrNull()?.id ?: error("not_authenticated")

    /** Las políticas RLS solo devuelven el perfil del usuario actual. */
    suspend fun getProfile(): Result<Profile> = runCatching {
        client.from("profiles").select { filter { eq("id", uid()) } }.decodeSingle<Profile>()
    }

    suspend fun updateProfile(displayName: String, dailyGoalXp: Int): Result<Unit> = runCatching {
        client.from("profiles").update({
            set("display_name", displayName)
            set("daily_goal_xp", dailyGoalXp)
        }) { filter { eq("id", uid()) } }
        Unit
    }

    /** Guarda la zona horaria del teléfono para que la racha use días locales. */
    suspend fun syncTimezone(timezoneId: String): Result<Unit> = runCatching {
        client.from("profiles").update({ set("timezone", timezoneId) }) {
            filter { eq("id", uid()) }
        }
        Unit
    }

    /** Elimina la cuenta y todos sus datos en el servidor y cierra la sesión local. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        client.postgrest.rpc("delete_my_account")
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
        Unit
    }

    companion object {
        const val MIN_DAILY_GOAL = 10
        const val MAX_DAILY_GOAL = 500

        /** Valida el nombre y la meta. Devuelve un mensaje de error o null si es válido. */
        fun validate(displayName: String, dailyGoalXp: Int?): String? = when {
            displayName.isBlank() -> "El nombre no puede estar vacío"
            displayName.trim().length < 2 -> "El nombre es muy corto"
            displayName.trim().length > 60 -> "El nombre no puede superar 60 caracteres"
            dailyGoalXp == null -> "Escribe tu meta diaria"
            dailyGoalXp !in MIN_DAILY_GOAL..MAX_DAILY_GOAL ->
                "La meta debe estar entre $MIN_DAILY_GOAL y $MAX_DAILY_GOAL XP"
            else -> null
        }
    }
}
