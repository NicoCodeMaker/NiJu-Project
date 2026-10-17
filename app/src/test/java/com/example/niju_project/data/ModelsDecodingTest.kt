package com.example.niju_project.data

import com.example.niju_project.TestFixtures
import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.ContextProgress
import com.example.niju_project.data.model.ContextRow
import com.example.niju_project.data.model.Dashboard
import com.example.niju_project.data.model.ExerciseType
import com.example.niju_project.data.model.LessonStatus
import com.example.niju_project.data.model.Profile
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionStart
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.remote.NijuJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato app ↔ servidor: los fixtures son respuestas reales de las funciones
 * SQL (supabase/migrations) ejecutadas en PostgreSQL. Si una columna cambia de
 * nombre en el servidor, estas pruebas fallan.
 */
class ModelsDecodingTest {

    private inline fun <reified T> decode(name: String): T =
        NijuJson.decodeFromString(TestFixtures.read(name))

    @Test
    fun startSession_decodesItemsWithoutAnswers() {
        val s = decode<SessionStart>("start")
        assertEquals(SessionMode.LESSON, s.mode)
        assertEquals(6, s.items.size)
        assertTrue(s.items.all { it.options.size == 3 })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), s.items.map { it.position })
        assertEquals(
            listOf(ExerciseType.EN_TO_ES, ExerciseType.ES_TO_EN, ExerciseType.FILL_BLANK),
            s.items.take(3).map { it.exerciseType }
        )
        assertTrue("el hueco de 'completar' viene marcado", s.items[2].prompt.contains("_____"))
        assertFalse("la respuesta correcta no viaja al teléfono", TestFixtures.read("start").contains("correct_answer"))
    }

    @Test
    fun answerResult_correctAndWrong() {
        val ok = decode<AnswerResult>("answer_ok")
        assertTrue(ok.isCorrect)
        assertEquals(10, ok.xpAwarded)
        assertEquals(1, ok.intervalDays)

        val wrong = decode<AnswerResult>("answer_wrong")
        assertFalse(wrong.isCorrect)
        assertEquals(0, wrong.xpAwarded)
        assertTrue(wrong.correctAnswer.isNotBlank())
    }

    @Test
    fun sessionSummary_decodes() {
        val s = decode<SessionSummary>("finish")
        assertEquals(6, s.total)
        assertEquals(2, s.answered)
        assertEquals(1, s.correct)
        assertEquals(16.67, s.score, 0.001)
        assertFalse(s.lessonCompleted)
        assertTrue(s.newAchievements.isEmpty())
    }

    @Test
    fun dashboard_decodesAndComputesPercents() {
        val d = decode<Dashboard>("dashboard")
        assertEquals("Laura Gómez", d.displayName)
        assertEquals(10, d.xpTotal)
        assertEquals(10, d.xpToday)
        assertEquals(20, d.dailyGoalPercent)       // 10 de 50
        assertEquals(10, d.levelPercent)           // 10 de 100 en el nivel 1
        assertEquals(1, d.currentStreak)
        assertEquals(6, d.wordsSeen)
        assertEquals(2, d.wordsPracticed)
        assertEquals(0, d.wordsMastered)
        assertNull(d.avatarUrl)
    }

    @Test
    fun contextProgress_lessons_contexts_profile() {
        val progress = decode<List<ContextProgress>>("context_progress")
        assertEquals(listOf("restaurant", "supermarket", "airport"), progress.map { it.slug })
        assertEquals(2, progress.first().lessonsTotal)
        assertEquals(12, progress.first().wordsTotal)

        val lessons = decode<List<LessonStatus>>("context_lessons").sortedBy { it.sortOrder }
        assertTrue("la primera lección siempre está abierta", lessons[0].unlocked)
        assertFalse("la segunda se abre al superar la primera", lessons[1].unlocked)

        val contexts = decode<List<ContextRow>>("contexts")
        assertEquals(3, contexts.size)

        val profile = decode<Profile>("profile")
        assertEquals(50, profile.dailyGoalXp)
        assertEquals("America/Bogota", profile.timezone)
    }

    @Test
    fun unknownValues_fallBackSafely() {
        assertEquals(SessionMode.PRACTICE, SessionMode.fromApi("otro"))
        assertEquals(ExerciseType.ES_TO_EN, ExerciseType.fromApi(null))
        val d = Dashboard(xpToday = 80, dailyGoalXp = 50)
        assertEquals("la meta no pasa de 100 %", 100, d.dailyGoalPercent)
        assertEquals(0, Dashboard(dailyGoalXp = 0).dailyGoalPercent)
    }
}
