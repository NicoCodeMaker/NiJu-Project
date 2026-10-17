package com.example.niju_project.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.AchievementStatus
import com.example.niju_project.data.model.DailyActivity
import com.example.niju_project.data.model.Dashboard
import com.example.niju_project.data.model.FavoriteWord
import com.example.niju_project.data.model.StrugglingWord
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsData(
    val dashboard: Dashboard,
    val activity: List<DailyActivity>,
    val achievements: List<AchievementStatus>,
    val struggling: List<StrugglingWord>
)

/** Estadísticas y logros: carga las cuatro consultas en paralelo. */
class StatsViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<StatsData>>(UiState.Loading)
    val uiState: StateFlow<UiState<StatsData>> = _state.asStateFlow()

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            val dashboard = async { repo.getDashboard() }
            val activity = async { repo.getDailyActivity(7) }
            val achievements = async { repo.getAchievements() }
            val struggling = async { repo.getStrugglingWords(10) }
            _state.value = runCatching {
                StatsData(
                    dashboard.await().getOrThrow(),
                    activity.await().getOrThrow(),
                    achievements.await().getOrThrow(),
                    struggling.await().getOrThrow()
                )
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it)) }
            )
        }
    }
}

/** Favoritas: carga la lista y permite quitarlas. */
class FavoritesViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<FavoriteWord>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<FavoriteWord>>> = _state.asStateFlow()

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            repo.getFavorites()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it)) }
        }
    }

    fun remove(word: FavoriteWord) {
        val current = (_state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(current.filterNot { it.wordId == word.wordId })
        viewModelScope.launch {
            repo.toggleFavorite(word.wordId)
                .onSuccess { stillFavorite -> if (stillFavorite) load() }
                .onFailure { _state.value = UiState.Success(current) }
        }
    }
}
