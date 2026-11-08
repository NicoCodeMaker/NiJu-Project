package com.example.niju_project.data.remote

import java.io.IOException

/**
 * Traduce los códigos de error del servidor (raise exception en las funciones
 * SQL) y los fallos de red a mensajes comprensibles para el estudiante.
 */
object NijuErrors {

    const val NOT_AUTHENTICATED = "not_authenticated"
    const val NO_DUE_REVIEWS = "no_due_reviews"

    private val messages = linkedMapOf(
        NOT_AUTHENTICATED to "Tu sesión expiró. Vuelve a iniciar sesión.",
        NO_DUE_REVIEWS to "¡Estás al día! No tienes repasos pendientes.",
        "no_words" to "Todavía no hay palabras para practicar aquí.",
        "lesson_not_found" to "Esta lección no está disponible.",
        "lesson_locked" to "Supera la lección anterior para abrir esta.",
        "word_not_found" to "Esta palabra ya no está disponible.",
        "item_already_answered" to "Ya respondiste este ejercicio.",
        "session_finished" to "Esta sesión ya terminó.",
        "session_not_found" to "No encontramos esta sesión.",
        "item_not_found" to "No encontramos este ejercicio.",
        "invalid_answer" to "La respuesta no es válida.",
        "invalid_timezone" to "La zona horaria no es válida.",
        "JWT expired" to "Tu sesión expiró. Vuelve a iniciar sesión.",
        // v2 (migración 6)
        "exam_locked" to "Supera todas las lecciones del contexto para abrir la evaluación.",
        "context_not_found" to "Este contexto no está disponible.",
        "no_favorites" to "Aún no tienes palabras favoritas. Márcalas con la estrella en las tarjetas.",
        // Supabase Auth (códigos y mensajes de la API)
        "invalid_credentials" to "Correo o contraseña incorrectos.",
        "Invalid login credentials" to "Correo o contraseña incorrectos.",
        "email_not_confirmed" to "Confirma tu correo: abre el enlace que te enviamos.",
        "Email not confirmed" to "Confirma tu correo: abre el enlace que te enviamos.",
        "user_already_exists" to "Ya existe una cuenta con ese correo. Inicia sesión.",
        "User already registered" to "Ya existe una cuenta con ese correo. Inicia sesión.",
        "weak_password" to "La contraseña es muy débil: usa al menos 8 caracteres con letras y números.",
        "same_password" to "La contraseña nueva debe ser distinta a la anterior.",
        "over_email_send_rate_limit" to "Enviamos demasiados correos. Espera unos minutos e intenta de nuevo.",
        "email rate limit exceeded" to "Enviamos demasiados correos. Espera unos minutos e intenta de nuevo.",
        "email_address_invalid" to "Ese correo no es válido.",
        "signup_disabled" to "El registro con correo está desactivado en el servidor.",
        "Signups not allowed" to "El registro con correo está desactivado en el servidor.",
        "email_provider_disabled" to "El inicio de sesión con correo está desactivado en el servidor."
    )

    /** Código de error conocido contenido en el texto, o null. */
    fun codeIn(text: String?): String? =
        text?.let { t -> messages.keys.firstOrNull { t.contains(it, ignoreCase = false) } }

    /** Mensaje para un texto de error (sin depender de clases de Supabase). */
    fun messageFor(text: String?): String =
        codeIn(text)?.let { messages.getValue(it) } ?: "Ocurrió un error. Intenta de nuevo."

    fun messageFor(error: Throwable): String {
        if (isNetwork(error)) return "Sin conexión. Revisa tu internet e intenta de nuevo."
        return messageFor(fullText(error))
    }

    fun isSessionExpired(error: Throwable): Boolean =
        codeIn(fullText(error)).let { it == NOT_AUTHENTICATED || it == "JWT expired" }

    fun hasCode(error: Throwable, code: String): Boolean = codeIn(fullText(error)) == code

    private fun fullText(error: Throwable): String =
        generateSequence(error) { it.cause }.take(5).joinToString(" | ") { it.message.orEmpty() }

    private fun isNetwork(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(5).any {
            it is IOException || it.javaClass.simpleName == "HttpRequestException"
        }
}
