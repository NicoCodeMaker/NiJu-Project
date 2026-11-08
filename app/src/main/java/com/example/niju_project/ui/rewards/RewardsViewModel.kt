package com.example.niju_project.ui.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.AchievementStatus
import com.example.niju_project.data.model.ClaimResult
import com.example.niju_project.data.model.Rewards
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RewardsData(
    val rewards: Rewards,
    val achievements: List<AchievementStatus>,
    /** Premios cobrados al abrir la pantalla (retos cumplidos sin cerrar la sesión). */
    val justClaimed: ClaimResult?
)

/**
 * Al abrir: cobra los retos ya cumplidos (claim_rewards, sin duplicar) y luego carga
 * recompensas y logros en paralelo.
 */
class RewardsViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<RewardsData>>(UiState.Loading)
    val uiState: StateFlow<UiState<RewardsData>> = _state.asStateFlow()

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            // Un fallo al cobrar no impide ver la pantalla.
            val claimed = repo.claimRewards().getOrNull()
            val rewards = async { repo.getRewards() }
            val achievements = async { repo.getAchievements() }
            _state.value = runCatching {
                RewardsData(rewards.await().getOrThrow(), achievements.await().getOrThrow(), claimed)
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it)) }
            )
        }
    }
}

/** Emoji de una insignia según su métrica. */
fun badgeEmoji(metric: String): String = when (metric) {
    "lessons_completed" -> "📘"
    "streak_days" -> "🔥"
    "words_mastered" -> "🧠"
    "correct_reviews" -> "🔁"
    "words_practiced" -> "📚"
    "perfect_sessions" -> "🌟"
    "exams_passed" -> "🎓"
    "xp_total" -> "⚡"
    "challenges_completed" -> "🎯"
    else -> "🏆"
}
