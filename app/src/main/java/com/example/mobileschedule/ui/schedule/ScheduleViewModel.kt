package com.example.mobileschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseDetail
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data object NoSemester : ScheduleUiState
    data object ConfigRequired : ScheduleUiState
    data class Ready(
        val week: ActiveWeek,
        val currentPosition: WeekPosition,
        val today: LocalDate,
    ) : ScheduleUiState
    data object Error : ScheduleUiState
}

sealed interface CourseDetailUiState {
    data object Closed : CourseDetailUiState
    data object Loading : CourseDetailUiState
    data object Missing : CourseDetailUiState
    data object Error : CourseDetailUiState
    data class Ready(val detail: CourseDetail, val sourceTermLabel: String?) : CourseDetailUiState
}

private data class BrowsingWeek(val semesterId: Long, val week: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(private val repository: ScheduleRepository) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now())
    private val retryRequests = MutableStateFlow(0)
    private val browsingWeek = MutableStateFlow<BrowsingWeek?>(null)
    private val selectedCourseId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ScheduleUiState> = retryRequests.flatMapLatest {
        combine(repository.observeActiveSemester(), today) { semester, date -> semester to date }
            .flatMapLatest { (result, date) ->
        val semester = (result as? RepoResult.Ok)?.value
        when {
            result is RepoResult.Err -> flowOf(ScheduleUiState.Error)
            semester == null -> flowOf(ScheduleUiState.NoSemester)
            semester.config == null -> flowOf(ScheduleUiState.ConfigRequired)
            else -> {
                val position = repository.weekPosition(semester.id, date)
                when (position) {
                    is RepoResult.Err -> flowOf(ScheduleUiState.Error)
                    is RepoResult.Ok -> {
                        val defaultWeek = when (val value = position.value) {
                            WeekPosition.BeforeSemester -> 1
                            WeekPosition.AfterSemester -> semester.config.totalWeeks
                            is WeekPosition.InSemester -> value.week
                        }
                        browsingWeek.map { selection ->
                            if (selection?.semesterId == semester.id) selection.week.coerceIn(1, semester.config.totalWeeks)
                            else defaultWeek
                        }.flatMapLatest { selectedWeek ->
                            repository.observeActiveWeek(selectedWeek).map { active ->
                                when (active) {
                                    is RepoResult.Err -> ScheduleUiState.Error
                                    is RepoResult.Ok -> active.value?.let { ScheduleUiState.Ready(it, position.value, date) }
                                        ?: ScheduleUiState.NoSemester
                                }
                            }
                        }
                    }
                }
            }
        }
            }.catch { emit(ScheduleUiState.Error) }
            .onStart { emit(ScheduleUiState.Loading) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState.Loading)

    val detailState: StateFlow<CourseDetailUiState> = selectedCourseId.flatMapLatest { id ->
        if (id == null) flowOf(CourseDetailUiState.Closed)
        else repository.observeCourseDetail(id).flatMapLatest { result ->
            when (result) {
                is RepoResult.Err -> flowOf(CourseDetailUiState.Error)
                is RepoResult.Ok -> {
                    val detail = result.value
                    if (detail == null) flowOf(CourseDetailUiState.Missing)
                    else {
                        val scope = (detail.arrangement.origin as? CourseOrigin.SchoolImport)?.scope
                        if (scope == null) flowOf(CourseDetailUiState.Ready(detail, null))
                        else repository.observeImportStatus(detail.arrangement.semesterId).map { status ->
                            val label = (status as? RepoResult.Ok)?.value?.sources
                                ?.firstOrNull { it.scope == scope }?.sourceTermLabel
                                ?.takeIf(String::isNotBlank) ?: scope.sourceTermId
                            CourseDetailUiState.Ready(detail, label)
                        }
                    }
                }
            }
        }.onStart { emit(CourseDetailUiState.Loading) }
    }.catch { emit(CourseDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseDetailUiState.Closed)

    /** Called on resume so crossing midnight updates the real week without changing an explicit browsing week. */
    fun refreshToday(date: LocalDate = LocalDate.now()) { today.value = date }
    fun retry() { retryRequests.value++ }

    fun selectWeek(week: Int) {
        val ready = uiState.value as? ScheduleUiState.Ready ?: return
        val totalWeeks = requireNotNull(ready.week.semester.config).totalWeeks
        if (week !in 1..totalWeeks) return
        browsingWeek.value = BrowsingWeek(ready.week.semester.id, week)
        closeCourseDetail()
    }

    fun previousWeek() {
        val week = (uiState.value as? ScheduleUiState.Ready)?.week?.schedule?.week ?: return
        selectWeek(week - 1)
    }

    fun nextWeek() {
        val ready = uiState.value as? ScheduleUiState.Ready ?: return
        if (ready.week.schedule.week < requireNotNull(ready.week.semester.config).totalWeeks) {
            selectWeek(ready.week.schedule.week + 1)
        }
    }

    fun returnToCurrentWeek() {
        browsingWeek.value = null
        closeCourseDetail()
    }

    fun showCourseDetail(id: Long) { selectedCourseId.value = id }
    fun closeCourseDetail() { selectedCourseId.value = null }
}
