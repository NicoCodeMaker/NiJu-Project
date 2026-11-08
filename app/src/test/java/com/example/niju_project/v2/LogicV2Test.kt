package com.example.niju_project.v2

import com.example.niju_project.audio.AudioCatalog
import com.example.niju_project.audio.AudioEngine
import com.example.niju_project.audio.AudioEntry
import com.example.niju_project.audio.AudioKind
import com.example.niju_project.audio.AudioManifest
import com.example.niju_project.audio.AudioTarget
import com.example.niju_project.audio.ExerciseAudio
import com.example.niju_project.audio.PlaybackState
import com.example.niju_project.audio.PronunciationController
import com.example.niju_project.audio.SpeechFallback
import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.ExerciseType
import com.example.niju_project.data.model.SessionItem
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.repository.AuthLinks
import com.example.niju_project.domain.auth.AuthValidation
import com.example.niju_project.domain.session.SessionProgress
import com.example.niju_project.domain.session.WordOrderState
import com.example.niju_project.ui.Formatters
import com.example.niju_project.ui.profile.initials
import com.example.niju_project.ui.results.ResultTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test fun email() {
        assertNull(AuthValidation.email(" ana@uni.edu.co "))
        assertNotNull(AuthValidation.email(""))
        assertNotNull(AuthValidation.email("ana@"))
        assertNotNull(AuthValidation.email("ana.uni.edu"))
    }

    @Test fun newPassword_requiresLengthLettersAndDigits() {
        assertNull(AuthValidation.newPassword("ingles2026"))
        assertEquals("Usa al menos 8 caracteres", AuthValidation.newPassword("ab12"))
        assertEquals("Combina letras y números", AuthValidation.newPassword("solamenteletras"))
        assertEquals("Combina letras y números", AuthValidation.newPassword("1234567890"))
        assertNotNull(AuthValidation.newPassword("a1".repeat(40)))
    }

    @Test fun confirmationNameAndStrength() {
        assertNull(AuthValidation.confirmation("x1y2z3w4", "x1y2z3w4"))
        assertNotNull(AuthValidation.confirmation("x1y2z3w4", "x1y2z3w5"))
        assertNull(AuthValidation.name("Ana"))
        assertNotNull(AuthValidation.name(" A "))
        assertTrue(AuthValidation.strength("Ingles-2026-Seguro") > AuthValidation.strength("abc"))
        assertEquals(0, AuthValidation.strength(""))
    }
}

class AuthLinksTest {
    @Test fun detectsRecoveryLinks() {
        assertTrue(AuthLinks.isRecovery("niju://login-callback#access_token=x&refresh_token=y&type=recovery"))
        assertTrue(AuthLinks.isRecovery("niju://login-callback?type=recovery&code=abc"))
        assertFalse(AuthLinks.isRecovery("niju://login-callback#access_token=x&type=signup"))
        assertFalse(AuthLinks.isRecovery(null))
    }

    @Test fun explainsLinkErrors() {
        assertEquals("El enlace venció. Pide uno nuevo.",
            AuthLinks.errorDescription("niju://login-callback#error=access_denied&error_code=otp_expired"))
        assertNull(AuthLinks.errorDescription("niju://login-callback#access_token=x"))
    }
}

class WordOrderStateTest {
    @Test fun buildsAnswerInTapOrder_withRepeatedWords() {
        val s = WordOrderState(listOf("the", "is", "The", "on", "lamp", "desk", "the"))
        listOf(2, 4, 1, 3, 0, 5).forEach { assertTrue(s.place(it)) }
        assertFalse("no se coloca dos veces", s.place(2))
        assertFalse(s.isComplete)
        assertEquals(listOf(6), s.availableIndexes)
        s.place(6)
        assertTrue(s.isComplete)
        assertEquals("The lamp is on the desk the", s.answer)
    }

    @Test fun removeAndUndo() {
        val s = WordOrderState(listOf("a", "b", "c"))
        s.place(1); s.place(0); s.place(2)
        assertTrue(s.remove(0))
        assertEquals("b c", s.answer)
        assertTrue(s.undo())
        assertEquals("b", s.answer)
        s.clear()
        assertFalse(s.undo())
        assertEquals(listOf(0, 1, 2), s.availableIndexes)
    }
}

class ExerciseAudioV2Test {
    @Test fun answersForNewTypes() {
        assertEquals(AudioTarget("They are in my backpack.", AudioKind.SENTENCE),
            ExerciseAudio.forAnswer(ExerciseType.DIALOGUE, "A: Where are your books?\nB: They are in my _____.", "backpack"))
        assertEquals(AudioTarget("My books are in my backpack.", AudioKind.SENTENCE),
            ExerciseAudio.forAnswer(ExerciseType.WORD_ORDER, "Mis libros…", "My books are in my backpack."))
        assertEquals(AudioTarget("My books are in my backpack.", AudioKind.SENTENCE),
            ExerciseAudio.forAnswer(ExerciseType.SPOT_ERROR, "My books are in my pencil.", "pencil", "My books are in my backpack."))
        assertEquals(AudioTarget("backpack", AudioKind.WORD),
            ExerciseAudio.forAnswer(ExerciseType.TYPE_ANSWER, "mochila", "backpack"))
    }

    @Test fun questionAudioNeverGivesAwayTheAnswer() {
        assertEquals(AudioTarget("kitchen", AudioKind.WORD), ExerciseAudio.forQuestion(ExerciseType.LISTEN_CHOOSE, "kitchen"))
        assertNull(ExerciseAudio.forQuestion(ExerciseType.ES_TO_EN, "cocina"))
        assertNull(ExerciseAudio.forQuestion(ExerciseType.TYPE_ANSWER, "cocina"))
        assertNull(ExerciseAudio.forQuestion(ExerciseType.FILL_BLANK, "I cook in the _____."))
    }
}

class SpeechFallbackTest {
    private class FakeEngine : AudioEngine {
        val played = mutableListOf<String>(); var stops = 0
        override fun play(assetPath: String, speed: Float, listener: AudioEngine.Listener) { played += assetPath }
        override fun stop() { stops++ }
        override fun release() = Unit
    }
    private class FakeTts(override var isAvailable: Boolean) : SpeechFallback {
        val spoken = mutableListOf<String>(); var stops = 0; var listener: AudioEngine.Listener? = null
        override fun speak(text: String, speed: Float, listener: AudioEngine.Listener) { spoken += text; this.listener = listener }
        override fun stop() { stops++ }
        override fun release() = Unit
    }
    private val catalog = AudioCatalog(AudioManifest(entries = listOf(
        AudioEntry("kitchen", "word", "en_US", "kitchen", file = "audio/en_us/words/kitchen.ogg", voice = "v"))))

    @Test fun localClipFirst_thenSystemVoice() {
        val engine = FakeEngine(); val tts = FakeTts(true)
        val c = PronunciationController({ catalog }, engine, { PronunciationController.Settings() }, tts)
        assertTrue(c.play("kitchen", AudioKind.WORD))
        assertEquals(listOf("audio/en_us/words/kitchen.ogg"), engine.played)
        assertTrue(tts.spoken.isEmpty())
        assertTrue(c.hasAudio("backpack", AudioKind.WORD))
        assertFalse(c.hasLocalClip("backpack", AudioKind.WORD))
        assertTrue(c.play("backpack", AudioKind.WORD))
        assertEquals(listOf("backpack"), tts.spoken)
        assertTrue("solo un audio a la vez", engine.stops >= 1)
        assertTrue(c.state.value is PlaybackState.Playing)
        tts.listener!!.onCompleted()
        assertEquals(PlaybackState.Idle, c.state.value)
    }

    @Test fun withoutVoice_isUnavailable_neverAnotherFile() {
        val engine = FakeEngine(); val tts = FakeTts(false)
        val c = PronunciationController({ catalog }, engine, { PronunciationController.Settings() }, tts)
        assertFalse(c.play("backpack", AudioKind.WORD))
        assertTrue(engine.played.isEmpty() && tts.spoken.isEmpty())
        assertTrue(c.state.value is PlaybackState.Unavailable)
    }
}

class ResultTextsTest {
    private fun s(mode: String, total: Int, correct: Int, lesson: Boolean = false, exam: Boolean = false,
                  perfect: Boolean = false, prev: Int = 1, level: Int = 1) =
        SessionSummary(sessionId = "x", modeRaw = mode, total = total, answered = total, correct = correct,
            score = if (total == 0) 0.0 else 100.0 * correct / total, xpEarned = 0, lessonCompleted = lesson,
            examPassed = exam, perfect = perfect, previousLevel = prev, level = level)

    @Test fun headers() {
        assertEquals("¡Lección superada!", ResultTexts.header(s("lesson", 6, 5, lesson = true)).title)
        assertTrue(ResultTexts.header(s("lesson", 6, 5, lesson = true)).celebrate)
        assertEquals("¡Evaluación aprobada!", ResultTexts.header(s("exam", 10, 9, exam = true)).title)
        assertEquals("Evaluación terminada", ResultTexts.header(s("exam", 10, 7)).title)
        assertEquals("¡Sesión perfecta!", ResultTexts.header(s("practice", 5, 5, perfect = true)).title)
        assertEquals("¡Sigue practicando!", ResultTexts.header(s("review", 4, 1)).title)
        assertTrue("subir de nivel celebra", ResultTexts.header(s("review", 4, 1, prev = 1, level = 2)).celebrate)
    }

    @Test fun repeatRules() {
        assertTrue(ResultTexts.canRepeat(s("lesson", 6, 3)))
        assertFalse(ResultTexts.canRepeat(s("lesson", 6, 6, lesson = true)))
        assertTrue(ResultTexts.canRepeat(s("exam", 10, 7)))
        assertFalse(ResultTexts.canRepeat(s("exam", 10, 9, exam = true)))
        assertFalse(ResultTexts.canRepeat(s("review", 4, 2)))
        assertTrue(ResultTexts.message(s("exam", 10, 7)).contains("80 %"))
    }
}

class FormattersV2Test {
    @Test fun duration() {
        assertEquals("45 s", Formatters.duration(45_000))
        assertEquals("12 min", Formatters.duration(12 * 60_000L + 30_000))
        assertEquals("2 h 5 min", Formatters.duration((2 * 3600 + 5 * 60) * 1000L))
        assertEquals("1 h", Formatters.duration(3_600_000))
        assertEquals("0 s", Formatters.duration(-5))
    }

    @Test fun datesWithoutJavaTime() {
        assertEquals("hoy", Formatters.dueIn("2026-11-07T10:00:00+00:00", "2026-11-08"))
        assertEquals("mañana", Formatters.dueIn("2026-11-09T10:00:00+00:00", "2026-11-08"))
        assertEquals("en 6 días", Formatters.dueIn("2026-11-14", "2026-11-08"))
        assertEquals("en 2 días", Formatters.dueIn("2027-01-01", "2026-12-30"))
        assertEquals("—", Formatters.dueIn(null, "2026-11-08"))
        assertEquals("8 nov", Formatters.shortDate("2026-11-08T17:00:00Z"))
    }

    @Test fun labels() {
        assertEquals("Fácil", Formatters.quality(5))
        assertEquals("—", Formatters.quality(null))
        assertEquals("🔥" to "Bono de racha", Formatters.rewardReason("streak_bonus"))
        assertEquals("1 día", Formatters.days(1))
        assertEquals("AG", initials("Ana Gómez", null))
        assertEquals("LA", initials("laura", null))
        assertEquals("J", initials("", "julian@x.co"))
    }
}

class SessionProgressV2Test {
    @Test fun streakBonusCountsInSessionXp() {
        val item = SessionItem(1, 1, 1, "type_answer", "mochila", null, emptyList())
        val p = SessionProgress(listOf(item))
        p.record(AnswerResult(isCorrect = true, correctAnswer = "backpack", xpAwarded = 10, streakBonus = 15))
        assertEquals(25, p.xpEarned)
    }
}
