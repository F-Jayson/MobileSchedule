package com.example.mobileschedule.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.repository.ScheduleRepository
import com.example.mobileschedule.ui.schedule.CourseDetailUiState
import com.example.mobileschedule.ui.schedule.CourseFormState
import com.example.mobileschedule.ui.theme.CourseColorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Error(val message: String) : TodayUiState
    data class Ready(
        val schedule: TodaySchedule,
        val currentTime: LocalTime,
    ) : TodayUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: ScheduleRepository,
) : ViewModel() {

    private val selectedCourseId = MutableStateFlow<Long?>(null)
    private val _formState = MutableStateFlow<CourseFormState>(CourseFormState.Closed)
    val formState: StateFlow<CourseFormState> = _formState.asStateFlow()

    // Periodically tick every 15s to update countdowns and current/upcoming states
    private val tickerFlow = flow {
        while (true) {
            emit(LocalTime.now())
            delay(15_000)
        }
    }

    val uiState: StateFlow<TodayUiState> = tickerFlow
        .flatMapLatest { now ->
            repository.observeTodaySchedule(LocalDate.now(), now).map { result ->
                when (result) {
                    is RepoResult.Ok -> TodayUiState.Ready(result.value, now)
                    is RepoResult.Err -> TodayUiState.Error("未能读取今日课程")
                }
            }
        }
        .catch { emit(TodayUiState.Error("加载今日课程失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

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

    fun showCourseDetail(id: Long) {
        selectedCourseId.value = id
    }

    fun closeCourseDetail() {
        selectedCourseId.value = null
    }

    fun openEditCourse(arrangement: CourseArrangement, config: SemesterConfig) {
        closeCourseDetail()
        _formState.value = CourseFormState.Edit(
            arrangement = arrangement,
            config = config,
        )
    }

    fun openCopyCourse(arrangement: CourseArrangement, config: SemesterConfig) {
        closeCourseDetail()
        _formState.value = CourseFormState.Copy(
            arrangement = arrangement,
            config = config,
        )
    }

    fun closeCourseForm() {
        _formState.value = CourseFormState.Closed
    }

    suspend fun checkConflict(draft: ArrangementDraft, excludingId: Long?): List<CourseArrangement> {
        val ready = (uiState.value as? TodayUiState.Ready)?.schedule as? TodaySchedule.Ready ?: return emptyList()
        val result = repository.checkArrangementConflict(ready.semester.id, draft, excludingId)
        return (result as? RepoResult.Ok)?.value ?: emptyList()
    }

    fun saveCourse(
        draft: ArrangementDraft,
        colorIndex: Int?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            when (val state = _formState.value) {
                is CourseFormState.Create -> {
                    when (val result = repository.createManualCourse(state.semesterId, draft)) {
                        is RepoResult.Ok -> {
                            if (colorIndex != null) CourseColorStore.setColorIndex(draft.name.trim(), colorIndex)
                            closeCourseForm()
                            onSuccess()
                        }
                        is RepoResult.Err -> onError(formatError(result.error))
                    }
                }
                is CourseFormState.Copy -> {
                    when (val result = repository.createManualCourse(state.arrangement.semesterId, draft)) {
                        is RepoResult.Ok -> {
                            if (colorIndex != null) CourseColorStore.setColorIndex(draft.name.trim(), colorIndex)
                            closeCourseForm()
                            onSuccess()
                        }
                        is RepoResult.Err -> onError(formatError(result.error))
                    }
                }
                is CourseFormState.Edit -> {
                    when (val result = repository.updateManualCourse(state.arrangement.id, draft)) {
                        is RepoResult.Ok -> {
                            if (colorIndex != null) CourseColorStore.setColorIndex(draft.name.trim(), colorIndex)
                            closeCourseForm()
                            onSuccess()
                        }
                        is RepoResult.Err -> onError(formatError(result.error))
                    }
                }
                CourseFormState.Closed -> Unit
            }
        }
    }

    fun deleteCourse(arrangementId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            when (repository.deleteManualCourse(arrangementId)) {
                is RepoResult.Ok -> {
                    closeCourseDetail()
                    onSuccess()
                }
                is RepoResult.Err -> Unit
            }
        }
    }

    private fun formatError(error: DataError): String = when (error.code) {
        DataErrorCode.COURSE_NOT_FOUND -> "课程不存在或已被删除"
        DataErrorCode.ARRANGEMENT_INVALID -> "课程信息校验未通过，请检查所填内容"
        else -> "操作失败，请重试"
    }
}
