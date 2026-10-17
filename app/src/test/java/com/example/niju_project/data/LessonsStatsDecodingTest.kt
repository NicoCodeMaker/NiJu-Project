package com.example.niju_project.data

import com.example.niju_project.TestFixtures
import com.example.niju_project.data.model.AchievementStatus
import com.example.niju_project.data.model.DailyActivity
import com.example.niju_project.data.model.FavoriteWord
import com.example.niju_project.data.model.LessonWord
import com.example.niju_project.data.model.StrugglingWord
import com.example.niju_project.data.model.WordStatus
import com.example.niju_project.data.remote.NijuJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de las funciones de la migración 3 (respuestas reales del servidor). */
class LessonsStatsDecodingTest {

    private inline fun <reified T> decode(name: String): T =
        NijuJson.decodeFromString(TestFixtures.read(name))

    @Test
    fun lessonContent() {
        val words = decode<List<LessonWord>>("lesson_content")
        assertEquals(6, words.size)
        val menu = words.first { it.english == "menu" }
        assertTrue(menu.isFavorite)
        assertEquals(WordStatus.LEARNING, menu.status)
        assertEquals("noun", menu.partOfSpeech)
        assertTrue(words.all { !it.sentenceEn.isNullOrBlank() && !it.sentenceEs.isNullOrBlank() })
        assertEquals((1..6).toList(), words.map { it.position })
    }

    @Test
    fun favoritesAndToggle() {
        val favs = decode<List<FavoriteWord>>("favorites")
        assertEquals(listOf("menu"), favs.map { it.english })
        assertTrue(NijuJson.decodeFromString<Boolean>(TestFixtures.read("toggle")))
    }

    @Test
    fun achievementsWithProgress() {
        val list = decode<List<AchievementStatus>>("achievements")
        assertEquals(6, list.size)
        assertTrue(list.none { it.unlocked })
        val first = list.first { it.code == "first_lesson" }
        assertEquals(0, first.percent)
        assertEquals(50, first.copy(currentValue = 1, threshold = 2).percent)
        assertEquals(100, first.copy(currentValue = 9, threshold = 2).percent)
    }

    @Test
    fun dailyActivityAndStruggling() {
        val days = decode<List<DailyActivity>>("daily_activity")
        assertEquals(7, days.size)
        assertTrue(days.all { it.day.matches(Regex("""\d{4}-\d{2}-\d{2}""")) })
        assertEquals(2, days.last().answers)          // las dos respuestas de hoy
        assertEquals(0, days.last().correct)

        val weak = decode<List<StrugglingWord>>("struggling")
        assertEquals("menu", weak.single().english)
        assertEquals(3, weak.single().lapses)
    }

    @Test
    fun unknownStatusFallsBackToNew() {
        assertEquals(WordStatus.NEW, WordStatus.fromApi("??"))
        assertEquals(WordStatus.MASTERED, WordStatus.fromApi("mastered"))
        assertFalse(AchievementStatus("c", "t", "d", "m", 1).unlocked)
    }
}
