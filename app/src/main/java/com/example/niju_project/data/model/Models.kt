package com.example.niju_project.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modelos que reflejan las tablas y funciones de supabase/migrations.
// Los nombres de columna (snake_case) se mapean con @SerialName.

enum class SessionMode(val apiValue: String) {
    LESSON("lesson"), REVIEW("review"), PRACTICE("practice");

    companion object {
        fun fromApi(value: String?): SessionMode =
            entries.firstOrNull { it.apiValue == value } ?: PRACTICE
    }
}

enum class ExerciseType(val apiValue: String, val instruction: String) {
    EN_TO_ES("en_to_es", "¿Qué significa esta palabra?"),
    ES_TO_EN("es_to_en", "¿Cómo se dice en inglés?"),
    FILL_BLANK("fill_blank", "Completa la oración");

    companion object {
        fun fromApi(value: String?): ExerciseType =
            entries.firstOrNull { it.apiValue == value } ?: ES_TO_EN
    }
}

@Serializable
data class ContextRow(
    val id: Int,
    val slug: String,
    val name: String,
    val description: String = "",
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0
)

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("daily_goal_xp") val dailyGoalXp: Int = 50,
    val timezone: String = "America/Bogota"
)

@Serializable
data class Dashboard(
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("xp_total") val xpTotal: Int = 0,
    val level: Int = 1,
    @SerialName("level_start_xp") val levelStartXp: Int = 0,
    @SerialName("next_level_xp") val nextLevelXp: Int = 100,
    @SerialName("xp_today") val xpToday: Int = 0,
    @SerialName("daily_goal_xp") val dailyGoalXp: Int = 50,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("studied_today") val studiedToday: Boolean = false,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    @SerialName("lessons_completed") val lessonsCompleted: Int = 0,
    @SerialName("due_reviews") val dueReviews: Int = 0,
    @SerialName("words_seen") val wordsSeen: Int = 0,
    @SerialName("words_practiced") val wordsPracticed: Int = 0,
    @SerialName("words_mastered") val wordsMastered: Int = 0,
    val accuracy: Double? = null
) {
    /** Porcentaje 0–100 de la meta diaria. */
    val dailyGoalPercent: Int
        get() = if (dailyGoalXp <= 0) 0 else (xpToday * 100 / dailyGoalXp).coerceIn(0, 100)

    /** Porcentaje 0–100 dentro del nivel actual. */
    val levelPercent: Int
        get() {
            val span = nextLevelXp - levelStartXp
            return if (span <= 0) 0 else ((xpTotal - levelStartXp) * 100 / span).coerceIn(0, 100)
        }
}

@Serializable
data class SessionItem(
    @SerialName("item_id") val itemId: Long,
    val position: Int,
    @SerialName("word_id") val wordId: Int,
    @SerialName("exercise_type") val exerciseTypeRaw: String,
    val prompt: String,
    val hint: String? = null,
    val options: List<String>
) {
    val exerciseType: ExerciseType get() = ExerciseType.fromApi(exerciseTypeRaw)
}

@Serializable
data class SessionStart(
    @SerialName("session_id") val sessionId: String,
    @SerialName("mode") val modeRaw: String,
    @SerialName("lesson_id") val lessonId: Int? = null,
    @SerialName("context_id") val contextId: Int? = null,
    val items: List<SessionItem> = emptyList()
) {
    val mode: SessionMode get() = SessionMode.fromApi(modeRaw)
}

@Serializable
data class AnswerResult(
    @SerialName("is_correct") val isCorrect: Boolean,
    @SerialName("correct_answer") val correctAnswer: String,
    @SerialName("xp_awarded") val xpAwarded: Int = 0,
    @SerialName("was_due") val wasDue: Boolean = true,
    val status: String = "learning",
    @SerialName("interval_days") val intervalDays: Int = 0,
    @SerialName("next_due_at") val nextDueAt: String? = null
)

@Serializable
data class UnlockedAchievement(
    val code: String,
    val title: String,
    @SerialName("xp_reward") val xpReward: Int = 0
)

@Serializable
data class SessionSummary(
    @SerialName("session_id") val sessionId: String,
    @SerialName("mode") val modeRaw: String,
    val total: Int,
    val answered: Int,
    val correct: Int,
    val score: Double,
    @SerialName("xp_earned") val xpEarned: Int,
    @SerialName("lesson_completed") val lessonCompleted: Boolean = false,
    @SerialName("new_achievements") val newAchievements: List<UnlockedAchievement> = emptyList(),
    @SerialName("xp_total") val xpTotal: Int = 0,
    val level: Int = 1
) {
    val mode: SessionMode get() = SessionMode.fromApi(modeRaw)
}

@Serializable
data class ContextProgress(
    @SerialName("context_id") val contextId: Int,
    val slug: String,
    val name: String,
    val description: String = "",
    val icon: String? = null,
    @SerialName("lessons_total") val lessonsTotal: Int = 0,
    @SerialName("lessons_completed") val lessonsCompleted: Int = 0,
    @SerialName("words_total") val wordsTotal: Int = 0,
    @SerialName("words_practiced") val wordsPracticed: Int = 0,
    @SerialName("words_mastered") val wordsMastered: Int = 0,
    @SerialName("percent_complete") val percentComplete: Double = 0.0
) {
    fun toContextRow() = ContextRow(contextId, slug, name, description, icon)
}

@Serializable
data class LessonStatus(
    @SerialName("lesson_id") val lessonId: Int,
    val slug: String,
    val title: String,
    val description: String = "",
    val level: String = "A1",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("best_score") val bestScore: Double = 0.0,
    val completed: Boolean = false,
    val unlocked: Boolean = false
)

/** Estado de aprendizaje de una palabra (mastery_status del servidor). */
enum class WordStatus(val apiValue: String, val label: String) {
    NEW("new", "Nueva"),
    LEARNING("learning", "Aprendiendo"),
    REVIEW("review", "En repaso"),
    MASTERED("mastered", "Dominada");

    companion object {
        fun fromApi(value: String?): WordStatus = entries.firstOrNull { it.apiValue == value } ?: NEW
    }
}

@Serializable
data class LessonWord(
    @SerialName("word_id") val wordId: Int,
    val position: Int,
    val english: String,
    val spanish: String,
    val pronunciation: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    @SerialName("sentence_en") val sentenceEn: String? = null,
    @SerialName("sentence_es") val sentenceEs: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("status") val statusRaw: String = "new"
) {
    val status: WordStatus get() = WordStatus.fromApi(statusRaw)
}

@Serializable
data class FavoriteWord(
    @SerialName("word_id") val wordId: Int,
    val english: String,
    val spanish: String,
    val pronunciation: String? = null,
    @SerialName("sentence_en") val sentenceEn: String? = null,
    @SerialName("sentence_es") val sentenceEs: String? = null,
    @SerialName("status") val statusRaw: String = "new",
    @SerialName("added_at") val addedAt: String? = null
) {
    val status: WordStatus get() = WordStatus.fromApi(statusRaw)
}

@Serializable
data class AchievementStatus(
    val code: String,
    val title: String,
    val description: String,
    val metric: String,
    val threshold: Int,
    @SerialName("xp_reward") val xpReward: Int = 0,
    @SerialName("current_value") val currentValue: Int = 0,
    @SerialName("unlocked_at") val unlockedAt: String? = null
) {
    val unlocked: Boolean get() = unlockedAt != null
    val percent: Int get() = if (threshold <= 0) 0 else (currentValue * 100 / threshold).coerceIn(0, 100)
}

@Serializable
data class DailyActivity(
    val day: String,          // yyyy-MM-dd en la zona horaria del usuario
    val answers: Int = 0,
    val correct: Int = 0,
    val xp: Int = 0
)

@Serializable
data class StrugglingWord(
    @SerialName("word_id") val wordId: Int,
    val english: String,
    val spanish: String,
    val lapses: Int = 0,
    @SerialName("correct_count") val correctCount: Int = 0,
    @SerialName("incorrect_count") val incorrectCount: Int = 0,
    @SerialName("due_at") val dueAt: String? = null
)
