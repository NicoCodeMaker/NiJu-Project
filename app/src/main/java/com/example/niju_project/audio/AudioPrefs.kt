package com.example.niju_project.audio

import android.content.Context
import android.content.SharedPreferences

/** Preferencias de audio (Configuración → Audio). Solo locales; no se sincronizan. */
class AudioPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reproducir la palabra sola al mostrar cada tarjeta / pregunta. Apagado por defecto. */
    var autoPlay: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    /** Reproducir a velocidad reducida (útil al empezar). */
    var slow: Boolean
        get() = prefs.getBoolean(KEY_SLOW, false)
        set(value) = prefs.edit().putBoolean(KEY_SLOW, value).apply()

    /** Variante de inglés preferida (hoy solo existe en_US). */
    var variant: String
        get() = prefs.getString(KEY_VARIANT, AudioCatalog.DEFAULT_VARIANT) ?: AudioCatalog.DEFAULT_VARIANT
        set(value) = prefs.edit().putString(KEY_VARIANT, value).apply()

    /** Velocidad que recibe el reproductor. */
    val speed: Float get() = if (slow) SLOW_SPEED else 1f

    companion object {
        const val PREFS = "niju_audio_prefs"
        private const val KEY_AUTOPLAY = "audio_autoplay"
        private const val KEY_SLOW = "audio_slow"
        private const val KEY_VARIANT = "audio_variant"
        const val SLOW_SPEED = 0.75f
    }
}
