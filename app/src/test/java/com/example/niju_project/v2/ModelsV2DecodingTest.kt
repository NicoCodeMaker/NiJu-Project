package com.example.niju_project.v2

import com.example.niju_project.TestFixtures
import com.example.niju_project.data.model.AnswerInput
import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.ClaimResult
import com.example.niju_project.data.model.ContextProgress
import com.example.niju_project.data.model.Dashboard
import com.example.niju_project.data.model.ExerciseType
import com.example.niju_project.data.model.LessonWord
import com.example.niju_project.data.model.ProfileOverview
import com.example.niju_project.data.model.ReviewOverview
import com.example.niju_project.data.model.ReviewWord
import com.example.niju_project.data.model.Rewards
import com.example.niju_project.data.model.SessionHistoryItem
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionStart
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.model.WordBucket
import com.example.niju_project.data.model.WordDetail
import com.example.niju_project.data.remote.NijuJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato v2 app ↔ servidor. Los fixtures v2_*.json se generaron ejecutando las funciones
 * de la migración 6 en PostgreSQL 16 con un estudiante que hizo una lección (5 de 6 bien).
 */
class ModelsV2DecodingTest {

    private inline fun <reified T> decode(name: String): T = NijuJson.decodeFromString(TestFixtures.read(name))

    @Test fun startSession_v2_hasSixTypesAndNoAnswers() {
        val raw = TestFixtures.read("v2_start")
        assertFalse("la respuesta no debe viajar a la app", raw.contains("answer_key") || raw.contains("solution"))
        val s: SessionStart = decode("v2_start")
        assertEquals(6, s.items.size)
        assertEquals(
            listOf(ExerciseType.EN_TO_ES, ExerciseType.IMAGE_MATCH, ExerciseType.LISTEN_CHOOSE,
                ExerciseType.FILL_BLANK, ExerciseType.TYPE_ANSWER, ExerciseType.WORD_ORDER),
            s.items.map { it.exerciseType }
        )
        assertTrue(s.items.first { it.exerciseType == ExerciseType.TYPE_ANSWER }.options.isEmpty())
        assertTrue(s.items.first { it.exerciseType == ExerciseType.WORD_ORDER }.options.size >= 3)
        // Posición 2 de "En el salón" = student: la imagen es su emoji y la opción correcta está entre las opciones.
        val image = s.items.first { it.exerciseType == ExerciseType.IMAGE_MATCH }
        assertEquals("🧑‍🎓", image.prompt)
        assertTrue("student" in image.options)
    }

    @Test fun answers_v2_carryQualityAndBucket() {
        val ok: AnswerResult = decode("v2_answer_ok")
        assertTrue(ok.isCorrect)
        assertEquals(5, ok.quality)
        assertEquals("learning", ok.bucket)
        assertEquals(2.6, ok.easeFactor!!, 0.001)
        val wrong: AnswerResult = decode("v2_answer_wrong")
        assertFalse(wrong.isCorrect)
        assertEquals(2, wrong.quality)
        assertNotNull("completar trae la oración completa", wrong.solution)
    }

    @Test fun finish_v2_levelUpChallengesAndTime() {
        val f: SessionSummary = decode("v2_finish")
        assertEquals(SessionMode.LESSON, f.mode)
        assertTrue(f.lessonCompleted)
        assertTrue(f.leveledUp)
        assertEquals(1, f.previousLevel)
        assertEquals(2, f.level)
        assertEquals("Principiante", f.levelTitle)
        assertEquals(15000L, f.studyMs)
        assertEquals("d_new_words_5", f.challengesCompleted.single().code)
        assertEquals("first_lesson", f.newAchievements.single().code)
    }

    @Test fun dashboard_v2() {
        val d: Dashboard = decode("v2_dashboard")
        assertEquals("Principiante", d.levelTitle)
        assertEquals(3, d.challengesTotal)
        assertTrue(d.studyMsToday > 0)
    }

    @Test fun rewards_v2() {
        val r: Rewards = decode("v2_rewards")
        assertEquals(2, r.level)
        assertEquals(6, r.streakMilestones.size)
        assertEquals(3, r.nextMilestone!!.days)
        assertEquals(3, r.daily.size)
        assertEquals(3, r.weekly.size)
        assertTrue(r.daily.any { it.claimed && it.completed })
        assertEquals(20, r.achievementsTotal)
        assertTrue(r.recentRewards.isNotEmpty())
        assertTrue(r.levelPercent in 0..100)
        val c: ClaimResult = decode("v2_claim")
        assertTrue("ya cobrado: no se duplica", c.challengesCompleted.isEmpty() && c.newAchievements.isEmpty())
    }

    @Test fun review_v2() {
        val o: ReviewOverview = decode("v2_review_overview")
        assertEquals(6, o.total)
        assertEquals(7, o.forecast.size)
        val words: List<ReviewWord> = decode("v2_review_words")
        assertEquals(6, words.size)
        assertTrue(words.all { it.bucket == WordBucket.LEARNING })
        val d: WordDetail = decode("v2_word_detail")
        assertEquals("teacher", d.english)
        assertEquals(1, d.history.size)
        assertEquals(ExerciseType.EN_TO_ES, d.history.single().exerciseType)
    }

    @Test fun profile_history_contexts_v2() {
        val p: ProfileOverview = decode("v2_profile")
        assertEquals("Laura Pérez", p.displayName)
        assertEquals("laura@niju.test", p.email)
        assertEquals(24, p.lessonsTotal)
        assertEquals(1, p.lessonsCompleted)
        assertEquals(6, p.wordsLearned)
        assertEquals(15000L, p.studyMsTotal)
        val h: List<SessionHistoryItem> = decode("v2_history")
        assertEquals(SessionMode.LESSON, h.single().mode)
        assertEquals("En el salón", h.single().lessonTitle)
        val ctx: List<ContextProgress> = decode("v2_context_progress")
        assertEquals(12, ctx.size)
        assertEquals("🏫", ctx.first { it.slug == "school" }.emoji)
        assertFalse(ctx.first { it.slug == "school" }.examUnlocked)
        val cards: List<LessonWord> = decode("v2_lesson_content")
        assertEquals("🎒", cards.first { it.english == "backpack" }.emoji)
    }

    @Test fun everyServerTypeIsKnown() {
        listOf("en_to_es", "es_to_en", "fill_blank", "image_match", "listen_choose",
            "type_answer", "word_order", "spot_error", "dialogue").forEach {
            assertEquals(it, ExerciseType.fromApi(it).apiValue)
        }
        assertEquals(AnswerInput.TEXT, ExerciseType.TYPE_ANSWER.input)
        assertEquals(AnswerInput.ORDER, ExerciseType.WORD_ORDER.input)
        assertEquals(SessionMode.EXAM, SessionMode.fromApi("exam"))
    }
}
