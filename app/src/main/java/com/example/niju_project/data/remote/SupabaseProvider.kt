package com.example.niju_project.data.remote

import com.example.niju_project.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/** JSON compartido por el cliente de Supabase y las pruebas. */
val NijuJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Punto único de acceso a Supabase.
 *
 * La URL y la clave pública (anon) salen de local.properties a través de
 * BuildConfig. La clave service_role NUNCA debe estar en la app.
 */
object SupabaseProvider {

    /** Esquema y host del enlace al que vuelve el navegador tras iniciar sesión. */
    const val AUTH_SCHEME = "niju"
    const val AUTH_HOST = "login-callback"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
                BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) {
            "Supabase no está configurado: agrega SUPABASE_URL y SUPABASE_ANON_KEY en local.properties"
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            defaultSerializer = KotlinXSerializer(NijuJson)
            install(Auth) {
                scheme = AUTH_SCHEME
                host = AUTH_HOST
            }
            install(Postgrest)
        }
    }
}
