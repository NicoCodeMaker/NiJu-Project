package com.example.niju_project.domain.auth

/**
 * Validación de los formularios de cuenta (antes de llamar al servidor).
 * El servidor (Supabase Auth) vuelve a validar: esto solo da mensajes claros y rápidos.
 * Código puro: se prueba sin Android.
 */
object AuthValidation {

    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 72          // límite de bcrypt
    const val MAX_NAME = 60

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun email(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Escribe tu correo"
            v.length > 254 || !EMAIL.matches(v) -> "Ese correo no es válido"
            else -> null
        }
    }

    /** Contraseña para iniciar sesión: solo que no esté vacía. */
    fun loginPassword(value: String): String? = if (value.isEmpty()) "Escribe tu contraseña" else null

    /** Contraseña nueva: longitud y al menos una letra y un número. */
    fun newPassword(value: String): String? = when {
        value.isEmpty() -> "Escribe una contraseña"
        value.length < MIN_PASSWORD -> "Usa al menos $MIN_PASSWORD caracteres"
        value.length > MAX_PASSWORD -> "Usa como máximo $MAX_PASSWORD caracteres"
        value.none { it.isLetter() } || value.none { it.isDigit() } -> "Combina letras y números"
        value.isBlank() -> "La contraseña no puede ser solo espacios"
        else -> null
    }

    fun confirmation(password: String, confirmation: String): String? =
        if (password != confirmation) "Las contraseñas no coinciden" else null

    fun name(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Escribe tu nombre"
            v.length < 2 -> "El nombre es muy corto"
            v.length > MAX_NAME -> "Máximo $MAX_NAME caracteres"
            else -> null
        }
    }

    /** Fuerza aproximada 0–4 para la barra del registro (no reemplaza la validación). */
    fun strength(value: String): Int {
        var s = 0
        if (value.length >= MIN_PASSWORD) s++
        if (value.length >= 12) s++
        if (value.any { it.isLetter() } && value.any { it.isDigit() }) s++
        if (value.any { !it.isLetterOrDigit() } || (value.any { it.isUpperCase() } && value.any { it.isLowerCase() })) s++
        return s.coerceAtMost(4)
    }
}
