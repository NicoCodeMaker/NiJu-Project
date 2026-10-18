package com.example.niju_project.audio

import com.example.niju_project.data.model.ExerciseType
import com.example.niju_project.data.model.NextLesson
import com.example.niju_project.data.remote.NijuJson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun repoFile(path: String): File {
    // Gradle ejecuta las pruebas con el directorio de trabajo = app/
    val f = File(path)
    return if (f.exists()) f else File("app/$path")
}

class AudioKeyTest {
    /** Mismos vectores que tools/audio/generate_audio.py::audio_key. */
    @Test fun vectors() {
        val cases = mapOf(
            "kitchen" to "kitchen",
            "Boarding pass" to "boarding_pass",
            "Can I pay by card?" to "can_i_pay_by_card",
            "  living   room " to "living_room",
            "It's 5 o'clock!" to "it_s_5_o_clock",
            "" to "",
            "???" to ""
        )
        cases.forEach { (text, key) -> assertEquals(text, key, AudioKey.of(text)) }
    }
}

class AudioCatalogTest {
    private val manifest = repoFile("src/main/assets/audio/manifest.json").readText()

    @Test fun realManifestParses_andHasLicenseMetadata() {
        val c = AudioCatalog.parse(manifest)
        assertEquals(120, c.size)
        assertTrue(c.voices.isNotEmpty())
        c.voices.values.forEach {
            assertTrue("voz sin licencia de datos", it.datasetLicense.isNotBlank())
            assertTrue("voz sin fuente", it.modelSource.startsWith("https://"))
        }
    }

    @Test fun everyEntryPointsToAnExistingFile_withMatchingKeyAndHash() {
        val m = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<AudioManifest>(manifest)
        m.entries.forEach { e ->
            val f = repoFile("src/main/assets/${e.file}")
            assertTrue("falta ${e.file}", f.isFile && f.length() > 0)
            assertEquals("clave de ${e.text}", AudioKey.of(e.text), e.key)
            assertTrue("voz desconocida ${e.voice}", e.voice in m.voices)
            e.sha256?.let { h ->
                val d = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes())
                assertEquals("hash de ${e.file}", h, d.joinToString("") { "%02x".format(it) })
            }
        }
    }

    @Test fun lookupIsExact_noFallback() {
        val c = AudioCatalog.parse(manifest)
        assertNotNull(c.find("Kitchen", AudioKind.WORD, "en_US"))
        assertNull("otra variante no existe", c.find("kitchen", AudioKind.WORD, "en_GB"))
        assertNull("otro tipo no existe", c.find("kitchen", AudioKind.SENTENCE, "en_US"))
        assertNull("palabra inexistente", c.find("spaceship", AudioKind.WORD, "en_US"))
        assertNull("texto vacío", c.find("", AudioKind.WORD, "en_US"))
    }

    /** Todo texto en inglés de los seeds debe tener audio: si añades contenido, regenera el audio. */
    @Test fun everySeedWordAndExampleHasAudio() {
        val c = AudioCatalog.parse(manifest)
        val tuple = Regex("""\(\s*'[a-z_]+'\s*,\s*\d+\s*,\s*'((?:[^']|'')+)'\s*,\s*'(?:[^']|'')+'\s*,\s*'(?:[^']|'')*'\s*,\s*'[a-z]+'\s*,\s*'A[12]'\s*,\s*'((?:[^']|'')+)'""")
        val seeds = listOf("supabase/seed.sql", "supabase/seed_home_transport.sql")
        var n = 0
        seeds.forEach { path ->
            val file = File("../$path").takeIf { it.exists() } ?: File(path)
            tuple.findAll(file.readText()).forEach { m ->
                val word = m.groupValues[1].replace("''", "'")
                val sentence = m.groupValues[2].replace("''", "'")
                assertTrue("sin audio de palabra: $word", c.has(word, AudioKind.WORD, "en_US"))
                assertTrue("sin audio de oración: $sentence", c.has(sentence, AudioKind.SENTENCE, "en_US"))
                n++
            }
        }
        assertEquals("se esperaban 60 palabras de seed", 60, n)
    }
}

class ExerciseAudioTest {
    @Test fun enToEs_playsPromptWord() =
        assertEquals(AudioTarget("kitchen", AudioKind.WORD), ExerciseAudio.forAnswer(ExerciseType.EN_TO_ES, "kitchen", "cocina"))

    @Test fun esToEn_playsCorrectAnswer() =
        assertEquals(AudioTarget("kitchen", AudioKind.WORD), ExerciseAudio.forAnswer(ExerciseType.ES_TO_EN, "cocina", "kitchen"))

    @Test fun fillBlank_playsFullSentence() =
        assertEquals(
            AudioTarget("I cook dinner in the kitchen.", AudioKind.SENTENCE),
            ExerciseAudio.forAnswer(ExerciseType.FILL_BLANK, "I cook dinner in the _____.", "kitchen")
        )
}

private class FakeEngine : AudioEngine {
    var played = mutableListOf<String>()
    var stops = 0
    var released = false
    var listener: AudioEngine.Listener? = null
    override fun play(assetPath: String, speed: Float, listener: AudioEngine.Listener) { played += assetPath; this.listener = listener }
    override fun stop() { stops++ }
    override fun release() { released = true }
}

class PronunciationControllerTest {
    private val catalog = AudioCatalog(
        AudioManifest(entries = listOf(
            AudioEntry("kitchen", "word", "en_US", "kitchen", file = "audio/en_us/words/kitchen.ogg", voice = "v")
        ))
    )
    private val logs = mutableListOf<String>()
    private val engine = FakeEngine()
    private fun controller() = PronunciationController({ catalog }, engine, { PronunciationController.Settings() }) { logs += it }

    @Test fun playsExistingAudio_thenIdleOnCompletion() {
        val c = controller()
        assertTrue(c.play("Kitchen", AudioKind.WORD))
        assertEquals(listOf("audio/en_us/words/kitchen.ogg"), engine.played)
        assertTrue(c.state.value is PlaybackState.Playing)
        engine.listener!!.onCompleted()
        assertEquals(PlaybackState.Idle, c.state.value)
    }

    @Test fun missingAudio_isUnavailable_playsNothing_logsOnce() {
        val c = controller()
        assertFalse(c.play("spaceship", AudioKind.WORD))
        assertFalse(c.play("spaceship", AudioKind.WORD))
        assertTrue(engine.played.isEmpty())
        assertTrue(c.state.value is PlaybackState.Unavailable)
        assertEquals(1, logs.size)
    }

    @Test fun engineError_becomesFailed() {
        val c = controller()
        c.play("kitchen", AudioKind.WORD)
        engine.listener!!.onError("archivo dañado")
        assertEquals(PlaybackState.Failed("kitchen", "archivo dañado"), c.state.value)
    }

    @Test fun repeat_restartsAudio() {
        val c = controller()
        c.play("kitchen", AudioKind.WORD); c.play("kitchen", AudioKind.WORD)
        assertEquals(2, engine.played.size)
    }

    @Test fun afterRelease_nothingPlays_andLateCallbacksAreIgnored() {
        val c = controller()
        c.play("kitchen", AudioKind.WORD)
        val l = engine.listener!!
        c.release()
        assertTrue(engine.released)
        l.onError("tarde")
        assertEquals(PlaybackState.Idle, c.state.value)
        assertFalse(c.play("kitchen", AudioKind.WORD))
        assertEquals(1, engine.played.size)
    }
}

class NextLessonDecodingTest {
    @Test fun decodesRpcRow() {
        val json = """[{"lesson_id":7,"lesson_slug":"home_rooms","lesson_title":"Las habitaciones",
            "lesson_description":"x","level":"A1","word_count":6,"context_id":4,"context_slug":"home",
            "context_name":"Hogar","context_lessons_completed":0,"context_lessons_total":2}]"""
        val n = NijuJson.decodeFromString<List<NextLesson>>(json).first()
        assertEquals("home", n.contextSlug)
        assertEquals(6, n.wordCount)
        assertEquals(2, n.contextLessonsTotal)
    }

    @Test fun emptyArrayMeansAllLessonsDone() =
        assertTrue(NijuJson.decodeFromString<List<NextLesson>>("[]").isEmpty())
}
