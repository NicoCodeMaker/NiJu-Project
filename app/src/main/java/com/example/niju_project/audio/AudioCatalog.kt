package com.example.niju_project.audio

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Qué se pronuncia: una palabra suelta o una oración de ejemplo. */
enum class AudioKind(val json: String) {
    WORD("word"), SENTENCE("sentence");

    companion object {
        fun fromJson(value: String): AudioKind? = entries.firstOrNull { it.json == value }
    }
}

/** Una pronunciación del catálogo (una fila de assets/audio/manifest.json). */
@Serializable
data class AudioEntry(
    val key: String,
    val kind: String,
    val variant: String,
    val text: String,
    val ipa: String? = null,
    /** Ruta dentro de assets/, p. ej. "audio/en_us/words/kitchen.ogg". */
    val file: String,
    /** Id de la voz en `voices` (de ahí salen la fuente y la licencia). */
    val voice: String,
    val sha256: String? = null,
    val durationMs: Int = 0
)

/** Origen y licencia de una voz. Se muestra en la documentación de licencias. */
@Serializable
data class AudioVoice(
    val variant: String,
    val engine: String = "",
    val model: String = "",
    val modelSource: String = "",
    val dataset: String = "",
    val datasetLicense: String = "",
    val modelRepoLicense: String = "",
    val commercialUse: String = "",
    val attributionRequired: Boolean = false,
    val attribution: String = "",
    val reviewedOn: String = ""
)

@Serializable
data class AudioManifest(
    val schema: Int = 1,
    val generatedBy: String = "",
    val generatedOn: String = "",
    val voices: Map<String, AudioVoice> = emptyMap(),
    val entries: List<AudioEntry> = emptyList()
)

/**
 * Catálogo en memoria: busca el audio de un texto sin tocar la red.
 * La búsqueda es exacta (clave + tipo + variante): si no hay audio devuelve null,
 * nunca "uno parecido".
 */
class AudioCatalog(manifest: AudioManifest = AudioManifest()) {

    val voices: Map<String, AudioVoice> = manifest.voices
    val size: Int = manifest.entries.size

    private val index: Map<Triple<String, String, String>, AudioEntry> =
        manifest.entries.associateBy { Triple(it.variant, it.kind, it.key) }

    /** Variantes de inglés con al menos un audio (p. ej. ["en_US"]). */
    val variants: Set<String> = manifest.entries.map { it.variant }.toSet()

    fun find(text: String, kind: AudioKind, variant: String): AudioEntry? {
        val key = AudioKey.of(text)
        if (key.isEmpty()) return null
        return index[Triple(variant, kind.json, key)]
    }

    fun has(text: String, kind: AudioKind, variant: String): Boolean = find(text, kind, variant) != null

    companion object {
        private const val TAG = "NijuAudio"
        const val MANIFEST_PATH = "audio/manifest.json"
        const val DEFAULT_VARIANT = "en_US"

        private val json = Json { ignoreUnknownKeys = true }

        val EMPTY = AudioCatalog()

        /** Interpreta el JSON del manifiesto. Lanza si está mal formado. */
        fun parse(text: String): AudioCatalog = AudioCatalog(json.decodeFromString<AudioManifest>(text))

        /** Lee assets/audio/manifest.json; si falta o está dañado devuelve un catálogo vacío y lo registra. */
        fun loadFromAssets(context: Context): AudioCatalog = try {
            val text = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
            parse(text)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer $MANIFEST_PATH: ${e.message}")
            EMPTY
        }
    }
}

/**
 * Carga única del catálogo. MainApplication la inicia en segundo plano al abrir la app;
 * si una pantalla lo pide antes de terminar, espera (el archivo pesa pocos KB).
 */
object AudioCatalogHolder {
    @Volatile private var loader: Lazy<AudioCatalog>? = null

    fun preload(context: Context) {
        val app = context.applicationContext
        val lazy = synchronized(this) {
            loader ?: lazy { AudioCatalog.loadFromAssets(app) }.also { loader = it }
        }
        Thread { lazy.value }.apply { name = "niju-audio-catalog"; isDaemon = true; start() }
    }

    fun get(context: Context): AudioCatalog {
        val app = context.applicationContext
        val lazy = synchronized(this) {
            loader ?: lazy { AudioCatalog.loadFromAssets(app) }.also { loader = it }
        }
        return lazy.value
    }
}
