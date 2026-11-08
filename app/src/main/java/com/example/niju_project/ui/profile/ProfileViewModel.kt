package com.example.niju_project.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.niju_project.data.model.DailyActivity
import com.example.niju_project.data.model.ProfileOverview
import com.example.niju_project.data.model.SessionHistoryItem
import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.LearningRepository
import com.example.niju_project.ui.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileData(
    val overview: ProfileOverview,
    val week: List<DailyActivity>,
    val history: List<SessionHistoryItem>
)

/** Perfil: resumen, actividad de 7 días e historial (tres consultas en paralelo). */
class ProfileViewModel(
    private val repo: LearningRepository = LearningRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ProfileData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProfileData>> = _state.asStateFlow()

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            val overview = async { repo.getProfileOverview() }
            val week = async { repo.getDailyActivity(7) }
            val history = async { repo.getSessionHistory(10) }
            _state.value = runCatching {
                ProfileData(overview.await().getOrThrow(), week.await().getOrThrow(), history.await().getOrThrow())
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(NijuErrors.messageFor(it), NijuErrors.isSessionExpired(it)) }
            )
        }
    }
}

/** Iniciales para el avatar ("Ana Gómez" → "AG"). Código puro. */
fun initials(name: String, email: String?): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
        words.size == 1 -> words[0].take(2).uppercase()
        !email.isNullOrBlank() -> email.take(1).uppercase()
        else -> "?"
    }
}
