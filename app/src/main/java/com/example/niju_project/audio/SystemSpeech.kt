package com.example.niju_project.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Voz en inglés del sistema (TextToSpeech de Android) como respaldo de los clips locales.
 *
 *  · Se inicia una vez por pantalla; si el teléfono no tiene voz en inglés, [isAvailable]
 *    queda en false y la app lo indica en lugar de sonar otra cosa.
 *  · Los callbacks se devuelven en el hilo principal, como MediaPlayerEngine.
 *  · No requiere red si la voz está instalada (Configuración del teléfono → Texto a voz).
 */
class SystemSpeech(context: Context) : SpeechFallback {

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ready = false
    private var listener: AudioEngine.Listener? = null
    private var counter = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = setLanguage()
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) Log.w(TAG, "El teléfono no tiene voz en inglés (resultado $result)")
        } else {
            Log.w(TAG, "TextToSpeech no se pudo iniciar ($status)")
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                main.post { if (utteranceId == current()) listener?.onCompleted() }
            }
            @Deprecated("Requerido por la API", ReplaceWith("onError(utteranceId, errorCode)"))
            override fun onError(utteranceId: String?) {
                main.post { if (utteranceId == current()) listener?.onError("TTS error") }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { if (utteranceId == current()) listener?.onError("TTS error $errorCode") }
            }
        })
    }

    private fun current() = "niju-$counter"

    private fun setLanguage(): Int {
        val us = tts.setLanguage(Locale.US)
        return if (us == TextToSpeech.LANG_MISSING_DATA || us == TextToSpeech.LANG_NOT_SUPPORTED)
            tts.setLanguage(Locale.ENGLISH) else us
    }

    override val isAvailable: Boolean get() = ready

    override fun speak(text: String, speed: Float, listener: AudioEngine.Listener) {
        if (!ready) { listener.onError("Sin voz en inglés"); return }
        this.listener = listener
        counter++
        tts.setSpeechRate(speed)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), current())
    }

    override fun stop() {
        listener = null
        if (ready) tts.stop()
    }

    override fun release() {
        listener = null
        ready = false
        runCatching { tts.stop(); tts.shutdown() }
    }

    private companion object { const val TAG = "NijuAudio" }
}
