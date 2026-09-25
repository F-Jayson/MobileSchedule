package com.example.mobileschedule.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface ConfigEditorState {
    data object Loading : ConfigEditorState
    data class LoadError(val message: String) : ConfigEditorState
    data class Editing(
        val semesterId: Long?,
        val revision: Long,
        val initial: SemesterFormInput,
        val input: SemesterFormInput,
        val showErrors: Boolean = false,
        val serverErrors: Map<String, String> = emptyMap(),
        val saveError: String? = null,
        val saving: Boolean = false,
        val impactPending: Boolean = false,
        val activateAfterSave: Boolean = false,
    ) : ConfigEditorState {
        val dirty: Boolean get() = input != initial
        val affectsSchedule: Boolean get() = semesterId != null && (
            input.firstWeekDate != initial.firstWeekDate || input.totalWeeks != initial.totalWeeks ||
                input.totalSections != initial.totalSections || input.timesEnabled != initial.timesEnabled ||
                input.sectionTimes != initial.sectionTimes)
    }
    data object Saved : ConfigEditorState
}

@HiltViewModel
class SettingsEditorViewModel @Inject constructor(private val repository: ScheduleRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ConfigEditorState>(ConfigEditorState.Loading)
    val state: StateFlow<ConfigEditorState> = mutableState
    private var loadJob: Job? = null

    fun open(semesterId: Long?) {
        if (loadJob != null || mutableState.value != ConfigEditorState.Loading) return
        if (semesterId == null) {
            mutableState.value = ConfigEditorState.Editing(null, 0, SemesterFormInput(), SemesterFormInput())
            return
        }
        loadJob = viewModelScope.launch {
            combine(repository.observeSemesters(), repository.observeSemesterConfig(semesterId)) { semesters, config ->
                semesters to config
            }.collect { (semestersResult, configResult) ->
                val current = mutableState.value
                if (current !is ConfigEditorState.Loading && current !is ConfigEditorState.LoadError) return@collect
                val semesters = (semestersResult as? RepoResult.Ok)?.value
                val config = (configResult as? RepoResult.Ok)?.value
                val semester = semesters?.firstOrNull { it.id == semesterId }
                if (semester == null || configResult is RepoResult.Err) {
                    mutableState.value = ConfigEditorState.LoadError("无法读取该学期配置，请返回设置页重试")
                } else {
                    val input = SemesterFormInput(
                        semester.displayName, config?.firstWeekMonday,
                        config?.totalWeeks?.toString() ?: "", config?.totalSections?.toString() ?: "",
                        config?.sectionTimes?.isNotEmpty() == true,
                        config?.sectionTimes?.associate { it.section to SectionTimeText(it.start.toString(), it.end.toString()) }
                            ?: emptyMap(),
                    )
                    mutableState.value = ConfigEditorState.Editing(semesterId, config?.revision ?: 0, input, input)
                }
            }
        }
    }

    fun updateInput(transform: (SemesterFormInput) -> SemesterFormInput) {
        val current = mutableState.value as? ConfigEditorState.Editing ?: return
        if (current.saving) return
        mutableState.value = current.copy(input = transform(current.input), serverErrors = emptyMap(),
            saveError = null, impactPending = false)
    }

    fun save() {
        val current = mutableState.value as? ConfigEditorState.Editing ?: return
        if (current.saving) return
        val validation = current.input.validate()
        if (validation.draft == null) {
            mutableState.value = current.copy(showErrors = true, serverErrors = emptyMap(), saveError = null)
            return
        }
        if (current.affectsSchedule && !current.activateAfterSave) {
            mutableState.value = current.copy(impactPending = true, showErrors = true)
            return
        }
        persist(current, validation.draft)
    }

    fun dismissImpact() {
        val current = mutableState.value as? ConfigEditorState.Editing ?: return
        mutableState.value = current.copy(impactPending = false)
    }

    fun confirmImpactAndSave() {
        val current = mutableState.value as? ConfigEditorState.Editing ?: return
        if (!current.impactPending || current.saving) return
        val validation = current.input.validate()
        if (validation.draft == null) {
            mutableState.value = current.copy(impactPending = false, showErrors = true)
            return
        }
        persist(current.copy(impactPending = false), validation.draft)
    }

    private fun persist(current: ConfigEditorState.Editing, draft: SemesterConfigDraft) {
        mutableState.value = current.copy(saving = true, impactPending = false, saveError = null)
        viewModelScope.launch {
            val targetId = current.semesterId
            if (targetId == null) {
                when (val created = repository.createSemester(draft)) {
                    is RepoResult.Err -> showSaveError(current, created)
                    is RepoResult.Ok -> {
                        val createdState = current.copy(semesterId = created.value.id,
                            revision = created.value.config?.revision ?: 1,
                            initial = current.input, saving = true, activateAfterSave = true)
                        mutableState.value = createdState
                        activate(createdState)
                    }
                }
            } else {
                when (val saved = repository.saveSemesterConfig(targetId, current.revision, draft)) {
                    is RepoResult.Err -> showSaveError(current, saved)
                    is RepoResult.Ok -> {
                        if (current.activateAfterSave) activate(current.copy(revision = saved.value.revision))
                        else mutableState.value = ConfigEditorState.Saved
                    }
                }
            }
        }
    }

    private suspend fun activate(current: ConfigEditorState.Editing) {
        when (repository.setActiveSemester(requireNotNull(current.semesterId))) {
            is RepoResult.Ok -> mutableState.value = ConfigEditorState.Saved
            is RepoResult.Err -> mutableState.value = current.copy(saving = false,
                saveError = "学期已创建，但设为当前学期失败。请重试保存；原学期不会被删除。")
        }
    }

    private fun showSaveError(current: ConfigEditorState.Editing, result: RepoResult.Err) {
        val error = result.error
        val message = when (error.code) {
            DataErrorCode.CONFIG_CONFLICT -> error.affectedCount?.let {
                "有 $it 条课程安排超出新的周数或节次范围。原配置与课程均已保留，请调整后再保存。"
            } ?: "配置已在别处更新。原配置与输入均保留，请返回重新打开后核对。"
            DataErrorCode.SEMESTER_NOT_FOUND -> "该学期已不存在，请返回设置页重新选择。"
            DataErrorCode.STORAGE_WRITE_FAILED -> "保存失败，原配置与课程均已保留，请重试。"
            else -> "配置未保存，请检查输入后重试。"
        }
        mutableState.value = current.copy(saving = false, showErrors = true, saveError = message,
            serverErrors = error.issues.associate { it.fieldKey() to it.message(current.input.suggestedMonday) })
    }
}
