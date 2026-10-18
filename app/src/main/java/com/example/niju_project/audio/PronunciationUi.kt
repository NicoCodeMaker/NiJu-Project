package com.example.niju_project.audio

import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.niju_project.R
import kotlinx.coroutines.launch

/**
 * Une un [PronunciationController] con los botones «Escuchar» de una pantalla.
 *
 *  · Crea el reproductor y lo libera solo (onStop detiene, onDestroy libera).
 *  · Cada botón se registra con [bind]: al tocarlo suena su texto; tocarlo de nuevo lo repite.
 *  · Si no hay audio para el texto: el botón se atenúa, su descripción lo dice y,
 *    al tocarlo, se muestra un mensaje. No suena nada.
 *  · Mientras suena, el botón queda `selected` (el icono cambia de color; ver color/listen_tint).
 */
class PronunciationUi(private val activity: AppCompatActivity) {

    private class Bound(val view: View, val target: () -> AudioTarget?)

    private val prefs = AudioPrefs(activity)
    private val bound = mutableListOf<Bound>()

    val controller = PronunciationController(
        catalog = { AudioCatalogHolder.get(activity) },
        engine = MediaPlayerEngine(activity),
        settings = { PronunciationController.Settings(prefs.variant, prefs.speed) },
        report = { Log.w(TAG, it) }
    )

    /** ¿Reproducir automáticamente al mostrar cada palabra? (Configuración → Audio) */
    val autoPlay: Boolean get() = prefs.autoPlay

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = controller.stop()
            override fun onDestroy(owner: LifecycleOwner) = controller.release()
        })
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { render(it) }
            }
        }
    }

    /** Registra un botón fijo a un tipo. [text] se evalúa al tocar y al refrescar (puede cambiar). */
    fun bind(view: View, kind: AudioKind, text: () -> String?) =
        bindTarget(view) { text()?.let { AudioTarget(it, kind) } }

    /** Registra un botón cuyo texto Y tipo cambian (p. ej. palabra o oración según el ejercicio). */
    fun bindTarget(view: View, target: () -> AudioTarget?) {
        val b = Bound(view, target)
        bound += b
        view.setOnClickListener {
            val t = b.target()
            if (t == null || t.text.isBlank()) return@setOnClickListener
            play(t.text, t.kind)
        }
        refresh(b)
    }

    /** Vuelve a evaluar la disponibilidad de todos los botones (llamar al cambiar de palabra). */
    fun refresh() = bound.forEach { refresh(it) }

    /** Reproduce [text]; si no existe audio lo dice con un mensaje. Devuelve si empezó a sonar. */
    fun play(text: String, kind: AudioKind): Boolean {
        val started = controller.play(text, kind)
        if (!started && !controller.hasAudio(text, kind)) {
            Toast.makeText(activity, activity.getString(R.string.audio_unavailable, text), Toast.LENGTH_SHORT).show()
        }
        return started
    }

    private fun refresh(b: Bound) {
        val t = b.target()
        val available = t != null && t.text.isNotBlank() && controller.hasAudio(t.text, t.kind)
        b.view.alpha = if (available) 1f else UNAVAILABLE_ALPHA
        b.view.contentDescription = when {
            t == null || t.text.isBlank() -> activity.getString(R.string.audio_listen)
            !available -> activity.getString(R.string.audio_unavailable, t.text)
            t.kind == AudioKind.SENTENCE -> activity.getString(R.string.audio_listen_sentence)
            else -> activity.getString(R.string.audio_listen_word, t.text)
        }
        b.view.isSelected = false
    }

    private fun render(state: PlaybackState) {
        bound.forEach { b ->
            val t = b.target()
            b.view.isSelected = state is PlaybackState.Playing &&
                t != null && state.kind == t.kind && state.key == AudioKey.of(t.text)
        }
        if (state is PlaybackState.Failed) {
            Toast.makeText(activity, R.string.audio_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "NijuAudio"
        const val UNAVAILABLE_ALPHA = 0.45f
    }
}
