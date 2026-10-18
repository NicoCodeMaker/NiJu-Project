package com.example.niju_project.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado visible de la reproducción (para colorear el botón y dar mensajes). */
sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** Suena el audio de [key] (la clave de AudioKey). */
    data class Playing(val key: String, val kind: AudioKind) : PlaybackState

    /** El catálogo no tiene audio para ese texto: no suena nada. */
    data class Unavailable(val text: String) : PlaybackState

    /** Hay audio en el catálogo pero no se pudo reproducir (archivo ausente/dañado, error del sistema). */
    data class Failed(val text: String, val reason: String) : PlaybackState
}

/**
 * Orquesta «Escuchar» / «Repetir» sobre un [AudioEngine].
 *
 * Reglas:
 *  · Cada llamada a [play] reinicia el audio (así «Repetir» es volver a tocar el botón).
 *  · Sin audio en el catálogo -> [PlaybackState.Unavailable] y se registra una sola vez;
 *    jamás se reproduce otro archivo como sustituto.
 *  · Se mantiene un solo estado; al terminar vuelve a Idle.
 *
 * Debe usarse desde el hilo principal (igual que MediaPlayer).
 */
class PronunciationController(
    private val catalog: () -> AudioCatalog,
    private val engine: AudioEngine,
    private val settings: () -> Settings,
    private val report: (String) -> Unit = {}
) {
    data class Settings(val variant: String = AudioCatalog.DEFAULT_VARIANT, val speed: Float = 1f)

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val reported = mutableSetOf<String>()
    private var released = false

    fun hasAudio(text: String, kind: AudioKind): Boolean =
        !released && catalog().has(text, kind, settings().variant)

    /** @return true si se inició la reproducción. */
    fun play(text: String, kind: AudioKind): Boolean {
        if (released) return false
        val cfg = settings()
        val entry = catalog().find(text, kind, cfg.variant)
        if (entry == null) {
            engine.stop()
            _state.value = PlaybackState.Unavailable(text)
            logOnce("Sin audio: kind=${kind.json} variant=${cfg.variant} key=${AudioKey.of(text)} text=\"$text\"")
            return false
        }
        _state.value = PlaybackState.Playing(entry.key, kind)
        engine.play(entry.file, cfg.speed, object : AudioEngine.Listener {
            override fun onCompleted() {
                if (!released && _state.value is PlaybackState.Playing) _state.value = PlaybackState.Idle
            }

            override fun onError(reason: String) {
                if (released) return
                _state.value = PlaybackState.Failed(text, reason)
                logOnce("Fallo al reproducir ${entry.file}: $reason")
            }
        })
        return true
    }

    fun stop() {
        if (released) return
        engine.stop()
        _state.value = PlaybackState.Idle
    }

    /** Llamar en onDestroy: libera el reproductor y corta los callbacks pendientes. */
    fun release() {
        if (released) return
        released = true
        engine.release()
        _state.value = PlaybackState.Idle
    }

    private fun logOnce(message: String) {
        if (reported.add(message)) report(message)
    }
}
