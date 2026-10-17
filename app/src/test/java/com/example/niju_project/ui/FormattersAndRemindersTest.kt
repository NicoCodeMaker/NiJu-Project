package com.example.niju_project.ui

import com.example.niju_project.data.model.DailyActivity
import com.example.niju_project.data.model.LessonStatus
import com.example.niju_project.reminders.ReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FormattersAndRemindersTest {

    private fun lesson(unlocked: Boolean, completed: Boolean, best: Double, words: Int = 6) =
        LessonStatus(1, "s", "T", wordCount = words, bestScore = best, completed = completed, unlocked = unlocked)

    @Test
    fun lessonStatusLabels() {
        assertTrue(Formatters.lessonStatus(lesson(false, false, 0.0)).startsWith("🔒"))
        assertEquals("✓ Superada · mejor resultado 83 %", Formatters.lessonStatus(lesson(true, true, 83.33)))
        assertTrue(Formatters.lessonStatus(lesson(true, false, 50.0)).contains("necesitas 70 %"))
        assertEquals("1 palabra · nueva", Formatters.lessonStatus(lesson(true, false, 0.0, words = 1)))
    }

    @Test
    fun dayInitialsInSpanish() {
        assertEquals("V", Formatters.dayInitial("2026-10-16"))   // viernes
        assertEquals("S", Formatters.dayInitial("2026-10-17"))
        assertEquals("D", Formatters.dayInitial("2026-10-18"))
        assertEquals("L", Formatters.dayInitial("2026-10-19"))
        assertEquals("X", Formatters.dayInitial("2026-10-21"))
        assertEquals("?", Formatters.dayInitial("no-es-fecha"))
    }

    @Test
    fun barHeightsAreRelativeToMax() {
        val days = listOf(0, 50, 100).map { DailyActivity("2026-10-1$it".take(10), xp = it) }
        assertEquals(listOf(0f, 0.5f, 1f), Formatters.barHeights(days))
        assertEquals(listOf(0f, 0f), Formatters.barHeights(List(2) { DailyActivity("2026-10-10") }))
        assertEquals("—", Formatters.accuracy(null))
        assertEquals("67 %", Formatters.accuracy(66.6))
        assertEquals("sustantivo", Formatters.partOfSpeech("noun"))
        assertNull(Formatters.partOfSpeech(null))
    }

    @Test
    fun reminderPolicy() {
        // Ya estudió hoy: nunca molestar.
        assertNull(ReminderPolicy.decide(true, true, studiedToday = true, dueReviews = 5))
        // Repasos pendientes tienen prioridad.
        assertTrue(ReminderPolicy.decide(true, true, false, 3)!!.text.startsWith("3 palabras"))
        assertTrue(ReminderPolicy.decide(false, true, false, 1)!!.text.startsWith("1 palabra está"))
        // Sin repasos (o sin dato del servidor): recordatorio general si está activo.
        assertEquals("¿Practicamos hoy?", ReminderPolicy.decide(true, true, false, 0)!!.title)
        assertEquals("¿Practicamos hoy?", ReminderPolicy.decide(true, false, false, null)!!.title)
        // Todo apagado.
        assertNull(ReminderPolicy.decide(false, false, false, 9))
        assertNull(ReminderPolicy.decide(false, true, false, 0))
    }

    @Test
    fun reminderDelayTargetsNext7pm() {
        val morning = Calendar.getInstance().apply { set(2026, 9, 16, 10, 0, 0); set(Calendar.MILLISECOND, 0) }
        assertEquals(9 * 3_600_000L, ReminderPolicy.delayUntil(19, morning))
        val night = Calendar.getInstance().apply { set(2026, 9, 16, 20, 0, 0); set(Calendar.MILLISECOND, 0) }
        assertEquals(23 * 3_600_000L, ReminderPolicy.delayUntil(19, night))
    }
}
