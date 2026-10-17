package com.example.niju_project.data.repository

import com.example.niju_project.data.model.AchievementStatus
import com.example.niju_project.data.model.AnswerResult
import com.example.niju_project.data.model.ContextProgress
import com.example.niju_project.data.model.ContextRow
import com.example.niju_project.data.model.DailyActivity
import com.example.niju_project.data.model.Dashboard
import com.example.niju_project.data.model.FavoriteWord
import com.example.niju_project.data.model.LessonWord
import com.example.niju_project.data.model.LessonStatus
import com.example.niju_project.data.model.SessionMode
import com.example.niju_project.data.model.SessionStart
import com.example.niju_project.data.model.SessionSummary
import com.example.niju_project.data.model.StrugglingWord
import com.example.niju_project.data.remote.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Operaciones de una sesión de estudio (permite simular el servidor en pruebas). */
interface SessionRepository {
    suspend fun startSession(
        mode: SessionMode,
        lessonId: Int? = null,
        contextId: Int? = null,
        limit: Int = LearningRepository.DEFAULT_SESSION_SIZE
    ): Result<SessionStart>

    suspend fun submitAnswer(itemId: Long, answer: String, responseMs: Long?): Result<AnswerResult>

    suspend fun finishSession(sessionId: String): Result<SessionSummary>
}

/**
 * Acceso al contenido y a las funciones de aprendizaje del servidor.
 * La app nunca escribe progreso directamente: todo pasa por estas RPC.
 */
class LearningRepository(
    private val client: SupabaseClient = SupabaseProvider.client
) : SessionRepository {

    suspend fun getContexts(): Result<List<ContextRow>> = runCatching {
        client.from("contexts").select {
            order("sort_order", Order.ASCENDING)
        }.decodeList<ContextRow>()
    }

    suspend fun getDashboard(): Result<Dashboard> = runCatching {
        client.postgrest.rpc("get_dashboard").decodeAs<Dashboard>()
    }

    suspend fun getContextProgress(): Result<List<ContextProgress>> = runCatching {
        client.postgrest.rpc("get_context_progress").decodeList<ContextProgress>()
    }

    suspend fun getContextLessons(contextId: Int): Result<List<LessonStatus>> = runCatching {
        client.postgrest.rpc(
            "get_context_lessons",
            buildJsonObject { put("p_context_id", contextId) }
        ).decodeList<LessonStatus>()
    }

    override suspend fun startSession(
        mode: SessionMode,
        lessonId: Int?,
        contextId: Int?,
        limit: Int
    ): Result<SessionStart> = runCatching {
        client.postgrest.rpc("start_session", buildJsonObject {
            put("p_mode", mode.apiValue)
            lessonId?.let { put("p_lesson_id", it) }
            contextId?.let { put("p_context_id", it) }
            put("p_limit", limit)
        }).decodeAs<SessionStart>()
    }

    override suspend fun submitAnswer(itemId: Long, answer: String, responseMs: Long?): Result<AnswerResult> =
        runCatching {
            client.postgrest.rpc("submit_answer", buildJsonObject {
                put("p_item_id", itemId)
                put("p_answer", answer)
                responseMs?.let { put("p_response_ms", it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
            }).decodeAs<AnswerResult>()
        }

    override suspend fun finishSession(sessionId: String): Result<SessionSummary> = runCatching {
        client.postgrest.rpc(
            "finish_session",
            buildJsonObject { put("p_session_id", sessionId) }
        ).decodeAs<SessionSummary>()
    }

    /** Tarjetas de presentación de una lección (error "lesson_locked" si está bloqueada). */
    suspend fun getLessonContent(lessonId: Int): Result<List<LessonWord>> = runCatching {
        client.postgrest.rpc(
            "get_lesson_content",
            buildJsonObject { put("p_lesson_id", lessonId) }
        ).decodeList<LessonWord>()
    }

    /** Marca o desmarca una favorita. Devuelve true si quedó marcada. */
    suspend fun toggleFavorite(wordId: Int): Result<Boolean> = runCatching {
        client.postgrest.rpc(
            "toggle_favorite",
            buildJsonObject { put("p_word_id", wordId) }
        ).decodeAs<Boolean>()
    }

    suspend fun getFavorites(): Result<List<FavoriteWord>> = runCatching {
        client.postgrest.rpc("get_favorites").decodeList<FavoriteWord>()
    }

    suspend fun getAchievements(): Result<List<AchievementStatus>> = runCatching {
        client.postgrest.rpc("get_achievements").decodeList<AchievementStatus>()
    }

    suspend fun getDailyActivity(days: Int = 7): Result<List<DailyActivity>> = runCatching {
        client.postgrest.rpc(
            "get_daily_activity",
            buildJsonObject { put("p_days", days) }
        ).decodeList<DailyActivity>()
    }

    suspend fun getStrugglingWords(limit: Int = 10): Result<List<StrugglingWord>> = runCatching {
        client.postgrest.rpc(
            "get_struggling_words",
            buildJsonObject { put("p_limit", limit) }
        ).decodeList<StrugglingWord>()
    }

    /** Reinicia el progreso de todas las palabras (conserva historial y XP). */
    suspend fun resetAllProgress(): Result<Int> = runCatching {
        client.postgrest.rpc("reset_progress").decodeAs<Int>()
    }

    companion object {
        const val DEFAULT_SESSION_SIZE = 10
    }
}
