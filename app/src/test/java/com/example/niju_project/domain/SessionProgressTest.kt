package com.example.niju_project.domain

import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.SessionItem
import com.example.niju_project.domain.session.SessionProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionProgressTest {

    private fun items(n: Int) = (1..n).map {
        SessionItem(itemId = it.toLong(), position = it, wordId = it, exerciseTypeRaw = "en_to_es",
            prompt = "w$it", options = listOf("a", "b", "c"))
    }

    private fun ok(xp: Int = 10) = AnswerResult(isCorrect = true, correctAnswer = "a", xpAwarded = xp)
    private fun wrong() = AnswerResult(isCorrect = false, correctAnswer = "a")

    @Test
    fun countsCorrectAnswersXpAndPercent() {
        val p = SessionProgress(items(4))
        assertEquals(1, p.number)
        p.record(ok()); assertTrue(p.advance())
        p.record(wrong()); assertTrue(p.advance())
        p.record(ok(2))
        assertEquals(3, p.answered)
        assertEquals(2, p.correct)
        assertEquals(12, p.xpEarned)
        assertEquals(75, p.percent)
        assertEquals(3, p.number)
    }

    @Test
    fun lastItemDoesNotAdvance() {
        val p = SessionProgress(items(1))
        assertTrue(p.isLast)
        p.record(ok())
        assertFalse(p.advance())
        assertEquals(100, p.percent)
    }

    @Test
    fun cannotAnswerTwiceOrSkipUnanswered() {
        val p = SessionProgress(items(2))
        assertThrows(IllegalStateException::class.java) { p.advance() }
        p.record(ok())
        assertThrows(IllegalStateException::class.java) { p.record(ok()) }
    }

    @Test
    fun duplicateSubmissionCountsOnce() {
        val p = SessionProgress(items(2))
        p.markAnsweredWithoutResult()
        p.markAnsweredWithoutResult()
        assertEquals(1, p.answered)
        assertEquals(0, p.correct)
        assertTrue(p.advance())
    }

    @Test
    fun emptySessionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SessionProgress(emptyList()) }
    }
}
