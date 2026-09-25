package com.example.mobileschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data object NoSemester : ScheduleUiState
    data object ConfigRequired : ScheduleUiState
    data class Ready(val week: ActiveWeek) : ScheduleUiState
    data object Error : ScheduleUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(repository: ScheduleRepository) : ViewModel() {
    val uiState = repository.observeActiveSemester().flatMapLatest { result ->
        val semester = (result as? RepoResult.Ok)?.value
        when {
            result is RepoResult.Err -> kotlinx.coroutines.flow.flowOf(ScheduleUiState.Error)
            semester == null -> kotlinx.coroutines.flow.flowOf(ScheduleUiState.NoSemester)
            semester.config == null -> kotlinx.coroutines.flow.flowOf(ScheduleUiState.ConfigRequired)
            else -> {
                val position = repository.weekPosition(semester.id, LocalDate.now())
                if (position is RepoResult.Err) kotlinx.coroutines.flow.flowOf(ScheduleUiState.Error)
                else {
                    val week = when (val value = (position as RepoResult.Ok).value) {
                        WeekPosition.BeforeSemester -> 1
                        WeekPosition.AfterSemester -> semester.config.totalWeeks
                        is WeekPosition.InSemester -> value.week
                    }
                    repository.observeActiveWeek(week).map { active ->
                        when (active) {
                            is RepoResult.Err -> ScheduleUiState.Error
                            is RepoResult.Ok -> active.value?.let(ScheduleUiState::Ready) ?: ScheduleUiState.NoSemester
                        }
                    }
                }
            }
        }
    }.catch { emit(ScheduleUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState.Loading)
}
