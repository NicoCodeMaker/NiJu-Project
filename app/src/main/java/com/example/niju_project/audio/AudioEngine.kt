package com.example.niju_project.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * Lo mínimo que el controlador necesita de un reproductor.
 * Existe para poder probar la lógica en JVM con un reproductor falso.
 */
interface AudioEngine {
    interface Listener {
        fun onCompleted()
        fun onError(reason: String)
    }

    /** Reproduce un archivo de assets. Cancela lo que sonara antes. */
    fun play(assetPath: String, speed: Float, listener: Listener)

    fun stop()

    /** Libera todo. No se debe usar el motor después. */
    fun release()
}

/**
 * Motor basado en MediaPlayer (frases de 1–3 s guardadas en assets; no necesita red).
 *
 *  · Un solo MediaPlayer a la vez: al pedir otro audio se libera el anterior.
 *  · Usa USAGE_MEDIA / CONTENT_TYPE_SPEECH: respeta el volumen multimedia del usuario;
 *    la app nunca cambia el volumen global del dispositivo.
 *  · Los callbacks llegan en el hilo que creó el motor (el principal).
 */
class MediaPlayerEngine(context: Context) : AudioEngine {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    override fun play(assetPath: String, speed: Float, listener: AudioEngine.Listener) {
        stop()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            appContext.assets.openFd(assetPath).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setOnPreparedListener { p ->
                if (player !== p) return@setOnPreparedListener   // ya se pidió otro audio
                try {
                    p.start()
                    // La velocidad se aplica DESPUÉS de start(): en algunos Android, fijar
                    // PlaybackParams antes de iniciar ya arranca la reproducción.
                    if (speed != 1f) p.playbackParams = p.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    fail(p, "No se pudo iniciar: ${e.message}", listener)
                }
            }
            mp.setOnCompletionListener { p ->
                if (player === p) { releasePlayer(); listener.onCompleted() }
            }
            mp.setOnErrorListener { p, what, extra ->
                if (player === p) fail(p, "Error del reproductor ($what/$extra)", listener)
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            // FileNotFoundException si el archivo del manifiesto no está en assets, etc.
            fail(mp, e.message ?: e.javaClass.simpleName, listener)
        }
    }

    private fun fail(p: MediaPlayer, reason: String, listener: AudioEngine.Listener) {
        Log.w(TAG, reason)
        if (player === p) releasePlayer() else runCatching { p.release() }
        listener.onError(reason)
    }

    override fun stop() = releasePlayer()

    override fun release() = releasePlayer()

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        runCatching { p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.setOnErrorListener(null) }
        runCatching { if (p.isPlaying) p.stop() }
        runCatching { p.release() }
    }

    private companion object { const val TAG = "NijuAudio" }
}
