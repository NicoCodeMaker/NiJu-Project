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
        "JWT expired" to "Tu sesión expiró. Vuelve a iniciar sesión."
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
