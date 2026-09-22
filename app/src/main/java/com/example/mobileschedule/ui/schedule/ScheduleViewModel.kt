package com.example.mobileschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.Course
import com.example.mobileschedule.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Ready(val courses: List<Course>) : ScheduleUiState
    data object Error : ScheduleUiState
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(repository: CourseRepository) : ViewModel() {
    val uiState = repository.observeCourses()
        .map<List<Course>, ScheduleUiState> { ScheduleUiState.Ready(it) }
        .catch { emit(ScheduleUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState.Loading)
}
