package com.example.niju_project.ui

import androidx.lifecycle.SavedStateHandle
import com.example.niju_project.TestFixtures
import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionStart
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.remote.NijuJson
import com.example.niju_project.data.repository.SessionRepository
import com.example.niju_project.ui.practice.PracticeViewModel
import com.example.niju_project.ui.practice.RetryAction
import com.example.niju_project.ui.practice.SessionUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Servidor simulado: responde con los fixtures reales o con el error indicado. */
private class FakeSessionRepository : SessionRepository {
    val session: SessionStart = NijuJson.decodeFromString(TestFixtures.read("start"))
    val summary: SessionSummary = NijuJson.decodeFromString(TestFixtures.read("finish"))

    var startError: Throwable? = null
    var nextSubmitError: Throwable? = null
    var submitGate: CompletableDeferred<Unit>? = null

    val startCalls = mutableListOf<Triple<SessionMode, Int?, Int?>>()
    val submitted = mutableListOf<Pair<Long, Long?>>()
    var finishCalls = 0

    override suspend fun startSession(mode: SessionMode, lessonId: Int?, contextId: Int?, limit: Int) =
        startError?.let { Result.failure<SessionStart>(it) }
            ?: Result.success(session).also { startCalls += Triple(mode, lessonId, contextId) }

    override suspend fun submitAnswer(itemId: Long, answer: String, responseMs: Long?): Result<AnswerResult> {
        submitted += itemId to responseMs
        submitGate?.await()
        nextSubmitError?.let { nextSubmitError = null; return Result.failure(it) }
        val correct = answer == "ok"
        return Result.success(AnswerResult(correct, "respuesta", if (correct) 10 else 0))
    }

    override suspend fun finishSession(sessionId: String): Result<SessionSummary> {
        finishCalls++
        return Result.success(summary)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    private lateinit var repo: FakeSessionRepository
    private var now = 0L

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeSessionRepository()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg extras: Pair<String, Any>) =
        PracticeViewModel(SavedStateHandle(mapOf(*extras)), repo) { now }

    private val PracticeViewModel.state get() = uiState.value

    @Test
    fun fullLessonSession_questionFeedbackNextFinish() {
        val vm = vm(PracticeViewModel.EXTRA_MODE to "lesson", PracticeViewModel.EXTRA_LESSON_ID to 7)
        assertEquals(Triple(SessionMode.LESSON, 7, null), repo.startCalls.single())

        val total = repo.session.items.size
        repeat(total) { i ->
            val q = vm.state as SessionUiState.Question
            assertEquals(i + 1, q.number)
            assertEquals(repo.session.items[i].itemId, q.item.itemId)

            vm.submitAnswer(if (i % 2 == 0) "ok" else "mal")
            val f = vm.state as SessionUiState.Feedback
            assertEquals(i % 2 == 0, f.result.isCorrect)
            assertEquals(i == total - 1, f.isLast)
            vm.next()
        }
        assertTrue(vm.state is SessionUiState.Finished)
        assertEquals(1, repo.finishCalls)
        assertEquals(repo.session.items.map { it.itemId }, repo.submitted.map { it.first })
    }

    @Test
    fun doubleTapSendsOnlyOneAnswer() {
        repo.submitGate = CompletableDeferred()
        val vm = vm()
        vm.submitAnswer("ok")
        vm.submitAnswer("ok")           // segundo toque mientras el primero viaja
        repo.submitGate!!.complete(Unit)
        assertEquals(1, repo.submitted.size)
        assertTrue(vm.state is SessionUiState.Feedback)
    }

    @Test
    fun measuresResponseTime() {
        now = 1_000
        val vm = vm()
        now = 3_500
        vm.submitAnswer("ok")
        assertEquals(2_500L, repo.submitted.single().second)
    }

    @Test
    fun noDueReviewsIsAnEmptyStateNotAnError() {
        repo.startError = RuntimeException("no_due_reviews")
        val vm = vm(PracticeViewModel.EXTRA_MODE to "review")
        val s = vm.state as SessionUiState.Empty
        assertTrue(s.message.contains("al día"))
    }

    @Test
    fun networkErrorOnAnswerLetsTheStudentRetry() {
        val vm = vm()
        repo.nextSubmitError = IOException("sin red")
        vm.submitAnswer("ok")
        val e = vm.state as SessionUiState.Error
        assertEquals(RetryAction.QUESTION, e.retry)

        vm.resumeQuestion()
        val q = vm.state as SessionUiState.Question
        assertEquals(1, q.number)
        vm.submitAnswer("ok")
        assertTrue((vm.state as SessionUiState.Feedback).result.isCorrect)
    }

    @Test
    fun expiredSessionSendsToLogin() {
        repo.startError = RuntimeException("not_authenticated")
        val e = vm().state as SessionUiState.Error
        assertTrue(e.sessionExpired)
        assertEquals(RetryAction.NONE, e.retry)
    }

    @Test
    fun finishEarlyClosesTheSession() {
        val vm = vm()
        vm.submitAnswer("ok")
        vm.finish()
        assertTrue(vm.state is SessionUiState.Finished)
        assertEquals(1, repo.finishCalls)
    }
}
