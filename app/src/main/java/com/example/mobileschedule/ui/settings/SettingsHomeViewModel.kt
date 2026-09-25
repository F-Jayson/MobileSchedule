package com.example.mobileschedule.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsHomeUiState {
    data object Loading : SettingsHomeUiState
    data object Error : SettingsHomeUiState
    data class Ready(val semesters: List<Semester>, val activeId: Long?) : SettingsHomeUiState
}

@HiltViewModel
class SettingsHomeViewModel @Inject constructor(private val repository: ScheduleRepository) : ViewModel() {
    val state: StateFlow<SettingsHomeUiState> = combine(
        repository.observeSemesters(), repository.observeActiveSemester(),
    ) { semesters, active ->
        if (semesters is RepoResult.Err || active is RepoResult.Err) SettingsHomeUiState.Error
        else SettingsHomeUiState.Ready(
            (semesters as RepoResult.Ok).value,
            (active as RepoResult.Ok).value?.id,
        )
    }.catch { emit(SettingsHomeUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsHomeUiState.Loading)

    private val mutableActionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = mutableActionError

    fun activate(semesterId: Long) {
        mutableActionError.value = null
        viewModelScope.launch {
            if (repository.setActiveSemester(semesterId) is RepoResult.Err) {
                mutableActionError.value = "无法切换当前学期，请重新打开设置页后重试。"
            }
        }
    }
}
