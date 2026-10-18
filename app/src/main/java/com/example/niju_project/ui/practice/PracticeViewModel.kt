package com.example.niju_project.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.SessionItem
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.data.repository.SessionRepository
import com.example.niju_project.domain.session.SessionProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Qué hacer después de un error. */
enum class RetryAction { START, QUESTION, FINISH, NONE }

sealed interface SessionUiState {
    data object Loading : SessionUiState

    data class Question(
        val item: SessionItem,
        val number: Int,
        val total: Int,
        val percent: Int
    ) : SessionUiState

    data class Feedback(
        val item: SessionItem,
        val result: AnswerResult,
        val number: Int,
        val total: Int,
        val percent: Int,
        val isLast: Boolean,
        /** Lo que eligió el estudiante (para mostrar «Tu respuesta» al fallar). */
        val answer: String = ""
    ) : SessionUiState

    data class Finished(val summary: SessionSummary) : SessionUiState

    /** No hay nada que estudiar (p. ej. sin repasos pendientes). No es un fallo. */
    data class Empty(val message: String) : SessionUiState

    data class Error(
        val message: String,
        val retry: RetryAction,
        val sessionExpired: Boolean = false
    ) : SessionUiState
}

/**
 * Orquesta una sesión: start_session → submit_answer por ejercicio → finish_session.
 * La corrección, el XP y la programación de repasos los decide el servidor.
 * Parámetros (extras del Intent): mode, lesson_id, context_id.
 */
class PracticeViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: SessionRepository = LearningRepository(),
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val mode = SessionMode.fromApi(savedStateHandle.get<String>(EXTRA_MODE))
    private val lessonId: Int? = savedStateHandle.get<Int>(EXTRA_LESSON_ID)?.takeIf { it > 0 }
    private val contextId: Int? = savedStateHandle.get<Int>(EXTRA_CONTEXT_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _state.asStateFlow()

    private var sessionId: String? = null
    private var progress: SessionProgress? = null
    private var questionShownAt = 0L
    private var busy = false

    init { start() }

    /** Inicia (o reintenta iniciar) la sesión. */
    fun start() = runBusy {
        repo.startSession(mode, lessonId, contextId)
            .onSuccess { session ->
                sessionId = session.sessionId
                if (session.items.isEmpty()) {
                    _state.value = SessionUiState.Empty(NijuErrors.messageFor("no_words"))
                } else {
                    progress = SessionProgress(session.items)
                    showQuestion()
                }
            }
            .onFailure { e ->
                _state.value =
                    if (NijuErrors.hasCode(e, NijuErrors.NO_DUE_REVIEWS) || NijuErrors.hasCode(e, "no_words"))
                        SessionUiState.Empty(NijuErrors.messageFor(e))
                    else error(e, RetryAction.START)
            }
    }

    fun submitAnswer(answer: String) {
        val p = progress ?: return
        if (p.isCurrentAnswered || answer.isBlank()) return
        val item = p.current
        val elapsed = (clock() - questionShownAt).takeIf { questionShownAt > 0 && it >= 0 }
        runBusy {
            repo.submitAnswer(item.itemId, answer, elapsed)
                .onSuccess { result ->
                    p.record(result)
                    _state.value = SessionUiState.Feedback(
                        item, result, p.number, p.total, p.percent, p.isLast, answer
                    )
                }
                .onFailure { e ->
                    if (NijuErrors.hasCode(e, "item_already_answered")) {
                        // Un envío anterior sí llegó al servidor: se cuenta y se continúa.
                        p.markAnsweredWithoutResult()
                        _state.value = error(e, RetryAction.QUESTION)
                    } else {
                        _state.value = error(e, RetryAction.QUESTION)
                    }
                }
        }
    }

    /** Vuelve a mostrar el ejercicio actual (tras un error al responder). */
    fun resumeQuestion() {
        val p = progress ?: return
        if (p.isCurrentAnswered) next() else showQuestion()
    }

    /** Avanza al siguiente ejercicio o cierra la sesión si era el último. */
    fun next() {
        val p = progress ?: return
        if (busy) return
        if (p.advance()) showQuestion() else finish()
    }

    /** Termina la sesión ahora. Los ejercicios sin responder cuentan como no acertados. */
    fun finish() {
        val id = sessionId ?: return
        runBusy {
            repo.finishSession(id)
                .onSuccess { _state.value = SessionUiState.Finished(it) }
                .onFailure { e -> _state.value = error(e, RetryAction.FINISH) }
        }
    }

    private fun showQuestion() {
        val p = progress ?: return
        questionShownAt = clock()
        _state.value = SessionUiState.Question(p.current, p.number, p.total, p.percent)
    }

    private fun error(e: Throwable, retry: RetryAction) = SessionUiState.Error(
        message = NijuErrors.messageFor(e),
        retry = if (NijuErrors.isSessionExpired(e)) RetryAction.NONE else retry,
        sessionExpired = NijuErrors.isSessionExpired(e)
    )

    /** Evita envíos dobles: ignora la acción si ya hay una en curso. */
    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        _state.value = SessionUiState.Loading
        viewModelScope.launch {
            try { block() } finally { busy = false }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_LESSON_ID = "lesson_id"
        const val EXTRA_CONTEXT_ID = "context_id"
        const val EXTRA_CONTEXT_SLUG = "context_slug"
        const val EXTRA_TITLE = "title"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PracticeViewModel(createSavedStateHandle()) }
        }
    }
}
