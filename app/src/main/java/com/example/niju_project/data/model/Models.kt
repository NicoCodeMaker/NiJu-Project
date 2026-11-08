package com.example.niju_project.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modelos que reflejan las tablas y funciones de supabase/migrations.
// Los nombres de columna (snake_case) se mapean con @SerialName.

enum class SessionMode(val apiValue: String) {
    LESSON("lesson"), REVIEW("review"), PRACTICE("practice"),
    /** Evaluación de comprensión de un contexto (migración 6). */
    EXAM("exam");

    companion object {
        fun fromApi(value: String?): SessionMode =
            entries.firstOrNull { it.apiValue == value } ?: PRACTICE
    }
}

/**
 * Tipos de ejercicio. La corrección SIEMPRE la hace el servidor (submit_answer);
 * aquí solo se decide cómo se presenta cada uno.
 */
enum class ExerciseType(val apiValue: String, val instruction: String, val input: AnswerInput) {
    EN_TO_ES("en_to_es", "¿Qué significa esta palabra?", AnswerInput.CHOICE),
    ES_TO_EN("es_to_en", "¿Cómo se dice en inglés?", AnswerInput.CHOICE),
    FILL_BLANK("fill_blank", "Completa la oración", AnswerInput.CHOICE),
    IMAGE_MATCH("image_match", "¿Qué palabra corresponde a la imagen?", AnswerInput.CHOICE),
    LISTEN_CHOOSE("listen_choose", "Escucha y elige la palabra", AnswerInput.CHOICE),
    TYPE_ANSWER("type_answer", "Escribe la palabra en inglés", AnswerInput.TEXT),
    WORD_ORDER("word_order", "Ordena las palabras para traducir la oración", AnswerInput.ORDER),
    SPOT_ERROR("spot_error", "Toca la palabra que está mal en la oración", AnswerInput.CHOICE),
    DIALOGUE("dialogue", "Completa la conversación", AnswerInput.CHOICE);

    /** El enunciado está en inglés y se puede escuchar (en «escuchar» suena solo). */
    val promptIsEnglish: Boolean
        get() = this == EN_TO_ES || this == LISTEN_CHOOSE || this == FILL_BLANK ||
            this == SPOT_ERROR || this == DIALOGUE

    companion object {
        fun fromApi(value: String?): ExerciseType =
            entries.firstOrNull { it.apiValue == value } ?: ES_TO_EN
    }
}

/** Cómo responde el estudiante. */
enum class AnswerInput { CHOICE, TEXT, ORDER }

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
    val accuracy: Double? = null,
    @SerialName("level_title") val levelTitle: String = "",
    @SerialName("urgent_reviews") val urgentReviews: Int = 0,
    @SerialName("study_ms_today") val studyMsToday: Long = 0,
    @SerialName("challenges_done") val challengesDone: Int = 0,
    @SerialName("challenges_total") val challengesTotal: Int = 3
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
    @SerialName("next_due_at") val nextDueAt: String? = null,
    /** Oración o diálogo completo, para mostrarlo al corregir (null si no aplica). */
    val solution: String? = null,
    /** XP extra por alcanzar un hito de racha con esta respuesta. */
    @SerialName("streak_bonus") val streakBonus: Int = 0,
    /** Calidad SM-2 (0–5) que el servidor asignó a la respuesta. */
    val quality: Int? = null,
    val bucket: String? = null,
    @SerialName("ease_factor") val easeFactor: Double? = null
)

@Serializable
data class UnlockedAchievement(
    val code: String,
    val title: String,
    @SerialName("xp_reward") val xpReward: Int = 0
)

@Serializable
data class CompletedChallenge(
    val code: String,
    val title: String,
    val period: String = "daily",
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
    val level: Int = 1,
    @SerialName("exam_passed") val examPassed: Boolean = false,
    val perfect: Boolean = false,
    @SerialName("challenges_completed") val challengesCompleted: List<CompletedChallenge> = emptyList(),
    @SerialName("study_ms") val studyMs: Long = 0,
    @SerialName("previous_level") val previousLevel: Int = 1,
    @SerialName("level_title") val levelTitle: String = "",
    @SerialName("current_streak") val currentStreak: Int = 0
) {
    val mode: SessionMode get() = SessionMode.fromApi(modeRaw)
    val leveledUp: Boolean get() = level > previousLevel
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
    @SerialName("percent_complete") val percentComplete: Double = 0.0,
    val emoji: String? = null,
    @SerialName("exam_unlocked") val examUnlocked: Boolean = false,
    @SerialName("exam_best_score") val examBestScore: Double = 0.0,
    @SerialName("exam_passed") val examPassed: Boolean = false
) {
    fun toContextRow() = ContextRow(contextId, slug, name, description, icon)
    val completed: Boolean get() = lessonsTotal > 0 && lessonsCompleted >= lessonsTotal
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
    @SerialName("status") val statusRaw: String = "new",
    val emoji: String? = null
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
    @SerialName("added_at") val addedAt: String? = null,
    val emoji: String? = null
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

/** Siguiente lección que debe hacer el estudiante (RPC get_next_lesson, migración 4). */
@Serializable
data class NextLesson(
    @SerialName("lesson_id") val lessonId: Int,
    @SerialName("lesson_slug") val lessonSlug: String,
    @SerialName("lesson_title") val lessonTitle: String,
    @SerialName("lesson_description") val lessonDescription: String = "",
    val level: String = "A1",
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("context_id") val contextId: Int,
    @SerialName("context_slug") val contextSlug: String,
    @SerialName("context_name") val contextName: String,
    @SerialName("context_lessons_completed") val contextLessonsCompleted: Int = 0,
    @SerialName("context_lessons_total") val contextLessonsTotal: Int = 0
)


// ── v2: repetición espaciada, recompensas, perfil (migración 6) ────────────────

/** Estado de una palabra en la repetición espaciada (word_bucket del servidor). */
enum class WordBucket(val apiValue: String, val label: String) {
    NEW("new", "Nueva"),
    LEARNING("learning", "Aprendiendo"),
    REINFORCE("reinforce", "Necesita refuerzo"),
    REVIEW("review", "En repaso"),
    MASTERED("mastered", "Dominada");

    companion object {
        fun fromApi(value: String?): WordBucket = entries.firstOrNull { it.apiValue == value } ?: NEW
    }
}

@Serializable
data class ForecastDay(val day: String, val count: Int = 0)

@Serializable
data class ReviewOverview(
    val total: Int = 0,
    @SerialName("due_now") val dueNow: Int = 0,
    val urgent: Int = 0,
    val learning: Int = 0,
    val reinforce: Int = 0,
    val review: Int = 0,
    val mastered: Int = 0,
    @SerialName("average_ease") val averageEase: Double? = null,
    @SerialName("next_due_at") val nextDueAt: String? = null,
    val forecast: List<ForecastDay> = emptyList()
)

@Serializable
data class ReviewWord(
    @SerialName("word_id") val wordId: Int,
    val english: String,
    val spanish: String,
    val emoji: String? = null,
    val pronunciation: String? = null,
    @SerialName("bucket") val bucketRaw: String = "new",
    val urgent: Boolean = false,
    @SerialName("ease_factor") val easeFactor: Double = 2.5,
    @SerialName("interval_days") val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    @SerialName("correct_count") val correctCount: Int = 0,
    @SerialName("incorrect_count") val incorrectCount: Int = 0,
    @SerialName("last_quality") val lastQuality: Int? = null,
    @SerialName("first_seen_at") val firstSeenAt: String? = null,
    @SerialName("last_reviewed_at") val lastReviewedAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null
) {
    val bucket: WordBucket get() = WordBucket.fromApi(bucketRaw)
}

@Serializable
data class WordAttempt(
    @SerialName("created_at") val createdAt: String,
    @SerialName("exercise_type") val exerciseTypeRaw: String = "",
    @SerialName("is_correct") val isCorrect: Boolean = false,
    val quality: Int? = null,
    @SerialName("response_ms") val responseMs: Long? = null
) {
    val exerciseType: ExerciseType get() = ExerciseType.fromApi(exerciseTypeRaw)
}

@Serializable
data class WordDetail(
    @SerialName("word_id") val wordId: Int,
    val english: String,
    val spanish: String,
    val emoji: String? = null,
    val pronunciation: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    val level: String? = null,
    @SerialName("example_en") val exampleEn: String? = null,
    @SerialName("example_es") val exampleEs: String? = null,
    @SerialName("bucket") val bucketRaw: String = "new",
    val urgent: Boolean = false,
    @SerialName("ease_factor") val easeFactor: Double? = null,
    @SerialName("interval_days") val intervalDays: Int? = null,
    val repetitions: Int? = null,
    val lapses: Int? = null,
    @SerialName("correct_count") val correctCount: Int? = null,
    @SerialName("incorrect_count") val incorrectCount: Int? = null,
    @SerialName("last_quality") val lastQuality: Int? = null,
    @SerialName("first_seen_at") val firstSeenAt: String? = null,
    @SerialName("last_reviewed_at") val lastReviewedAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val history: List<WordAttempt> = emptyList()
) {
    val bucket: WordBucket get() = WordBucket.fromApi(bucketRaw)
}

@Serializable
data class Challenge(
    val code: String,
    val period: String = "daily",
    val title: String,
    val description: String = "",
    val progress: Int = 0,
    val target: Int = 1,
    @SerialName("xp_reward") val xpReward: Int = 0,
    val claimed: Boolean = false,
    val completed: Boolean = false
) {
    val isWeekly: Boolean get() = period == "weekly"
    val percent: Int get() = if (target <= 0) 0 else (progress * 100 / target).coerceIn(0, 100)
}

@Serializable
data class StreakMilestone(val days: Int, val xp: Int = 0, val reached: Boolean = false)

@Serializable
data class RecentReward(
    val reason: String,
    val amount: Int = 0,
    val ref: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Rewards(
    @SerialName("xp_total") val xpTotal: Int = 0,
    val level: Int = 1,
    @SerialName("level_title") val levelTitle: String = "",
    @SerialName("next_level_title") val nextLevelTitle: String? = null,
    @SerialName("level_start_xp") val levelStartXp: Int = 0,
    @SerialName("next_level_xp") val nextLevelXp: Int = 100,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    @SerialName("studied_today") val studiedToday: Boolean = false,
    @SerialName("streak_milestones") val streakMilestones: List<StreakMilestone> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
    @SerialName("week_start") val weekStart: String? = null,
    @SerialName("achievements_unlocked") val achievementsUnlocked: Int = 0,
    @SerialName("achievements_total") val achievementsTotal: Int = 0,
    @SerialName("recent_rewards") val recentRewards: List<RecentReward> = emptyList()
) {
    val levelPercent: Int
        get() {
            val span = nextLevelXp - levelStartXp
            return if (span <= 0) 0 else ((xpTotal - levelStartXp) * 100 / span).coerceIn(0, 100)
        }
    val daily: List<Challenge> get() = challenges.filterNot { it.isWeekly }
    val weekly: List<Challenge> get() = challenges.filter { it.isWeekly }
    val nextMilestone: StreakMilestone? get() = streakMilestones.firstOrNull { it.days > currentStreak }
}

@Serializable
data class ClaimResult(
    @SerialName("challenges_completed") val challengesCompleted: List<CompletedChallenge> = emptyList(),
    @SerialName("new_achievements") val newAchievements: List<UnlockedAchievement> = emptyList()
)

@Serializable
data class ProfileOverview(
    @SerialName("display_name") val displayName: String = "",
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("member_since") val memberSince: String? = null,
    @SerialName("daily_goal_xp") val dailyGoalXp: Int = 50,
    val level: Int = 1,
    @SerialName("level_title") val levelTitle: String = "",
    @SerialName("xp_total") val xpTotal: Int = 0,
    @SerialName("level_start_xp") val levelStartXp: Int = 0,
    @SerialName("next_level_xp") val nextLevelXp: Int = 100,
    @SerialName("lessons_completed") val lessonsCompleted: Int = 0,
    @SerialName("lessons_total") val lessonsTotal: Int = 0,
    @SerialName("words_learned") val wordsLearned: Int = 0,
    @SerialName("words_mastered") val wordsMastered: Int = 0,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    @SerialName("study_ms_total") val studyMsTotal: Long = 0,
    @SerialName("study_ms_week") val studyMsWeek: Long = 0,
    @SerialName("sessions_completed") val sessionsCompleted: Int = 0,
    @SerialName("answers_total") val answersTotal: Int = 0,
    val accuracy: Double? = null,
    @SerialName("perfect_sessions") val perfectSessions: Int = 0,
    @SerialName("exams_passed") val examsPassed: Int = 0,
    @SerialName("challenges_completed") val challengesCompleted: Int = 0,
    @SerialName("contexts_completed") val contextsCompleted: Int = 0,
    @SerialName("achievements_unlocked") val achievementsUnlocked: Int = 0,
    @SerialName("achievements_total") val achievementsTotal: Int = 0
) {
    val levelPercent: Int
        get() {
            val span = nextLevelXp - levelStartXp
            return if (span <= 0) 0 else ((xpTotal - levelStartXp) * 100 / span).coerceIn(0, 100)
        }
}

@Serializable
data class SessionHistoryItem(
    @SerialName("session_id") val sessionId: String,
    @SerialName("mode") val modeRaw: String,
    @SerialName("lesson_title") val lessonTitle: String? = null,
    @SerialName("context_name") val contextName: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    val total: Int = 0,
    val correct: Int = 0,
    val score: Double = 0.0,
    @SerialName("xp_earned") val xpEarned: Int = 0,
    @SerialName("study_ms") val studyMs: Long = 0
) {
    val mode: SessionMode get() = SessionMode.fromApi(modeRaw)
}
