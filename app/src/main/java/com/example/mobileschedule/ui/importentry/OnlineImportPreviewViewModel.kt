package com.example.mobileschedule.ui.importentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.importer.ParsedZhengfangSchedule
import com.example.mobileschedule.data.importer.ZhengfangImportPreviewSummary
import com.example.mobileschedule.data.importer.toPreviewSummary
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.ImportPreview
import com.example.mobileschedule.data.model.ImportConfirmation
import com.example.mobileschedule.data.model.ImportReceipt
import com.example.mobileschedule.data.model.ImportRequest
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface OnlineImportPreviewState {
    data object Idle : OnlineImportPreviewState
    data object Loading : OnlineImportPreviewState
    data class Ready(val summary: ZhengfangImportPreviewSummary) : OnlineImportPreviewState
    data class Error(val message: String) : OnlineImportPreviewState
    data class Saving(val summary: ZhengfangImportPreviewSummary) : OnlineImportPreviewState
    data class Saved(val summary: ZhengfangImportPreviewSummary, val receipt: ImportReceipt,
        val activationError: String? = null) : OnlineImportPreviewState
    data class SaveFailed(val summary: ZhengfangImportPreviewSummary, val message: String,
        val retryable: Boolean) : OnlineImportPreviewState
}

/** One live preview at a time. Late results are discarded and cannot replace a newer selection. */
internal class OnlineImportPreviewSession(
    private val scope: CoroutineScope,
    private val prepare: suspend (ImportRequest) -> RepoResult<ImportPreview>,
    private val discard: suspend (String) -> RepoResult<Unit>,
    private val commit: suspend (String, ImportConfirmation) -> RepoResult<ImportReceipt> = { _, _ ->
        error("Import commit was not supplied")
    },
    private val activate: suspend (Long) -> RepoResult<Unit> = { error("Semester activation was not supplied") },
) {
    private val mutableState = MutableStateFlow<OnlineImportPreviewState>(OnlineImportPreviewState.Idle)
    val state: StateFlow<OnlineImportPreviewState> = mutableState
    private var generation = 0L
    private var previewId: String? = null

    fun show(parsed: ParsedZhengfangSchedule) {
        if (mutableState.value is OnlineImportPreviewState.Saving) return
        invalidate()
        val requestedGeneration = generation
        mutableState.value = OnlineImportPreviewState.Loading
        scope.launch {
            try {
                when (val result = prepare(parsed.request)) {
                    is RepoResult.Err -> if (requestedGeneration == generation) {
                        mutableState.value = OnlineImportPreviewState.Error(previewError(result.error.code))
                    }
                    is RepoResult.Ok -> {
                        val preview = result.value
                        if (requestedGeneration != generation) {
                            discard(preview.previewId)
                            return@launch
                        }
                        val summary = try {
                            parsed.toPreviewSummary(preview)
                        } catch (_: IllegalArgumentException) {
                            discard(preview.previewId)
                            mutableState.value = OnlineImportPreviewState.Error("预览数据与读取结果不一致，请重新读取。")
                            return@launch
                        }
                        previewId = preview.previewId
                        mutableState.value = OnlineImportPreviewState.Ready(summary)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (requestedGeneration == generation) {
                    mutableState.value = OnlineImportPreviewState.Error("本地预览准备失败，请重新读取。")
                }
            }
        }
    }

    fun invalidate() {
        if (mutableState.value is OnlineImportPreviewState.Saving) return
        generation++
        val oldId = previewId
        previewId = null
        mutableState.value = OnlineImportPreviewState.Idle
        if (oldId != null) scope.launch {
            try {
                discard(oldId)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // The ID is no longer exposed to the UI; the repository also expires pending previews.
            }
        }
    }

    /** Only an explicit UI confirmation enters this path; the repository rechecks every precondition. */
    fun confirm() {
        val current = mutableState.value
        val summary = when (current) {
            is OnlineImportPreviewState.Ready -> current.summary
            is OnlineImportPreviewState.SaveFailed -> if (current.retryable) current.summary else return
            else -> return
        }
        if (!summary.canCommit || summary.completeness.status !=
            com.example.mobileschedule.data.model.CompletenessStatus.VERIFIED_FULL) return
        val scopeToReplace = summary.replacementScope ?: return
        val oldCount = summary.replaceCount ?: return
        if (summary.previewId != previewId) return
        val confirmation = ImportConfirmation(summary.previewId, scopeToReplace, oldCount, summary.validCount)
        mutableState.value = OnlineImportPreviewState.Saving(summary)
        scope.launch {
            try {
                when (val result = commit(summary.previewId, confirmation)) {
                    is RepoResult.Err -> mutableState.value = OnlineImportPreviewState.SaveFailed(
                        summary, saveError(result.error.code),
                        retryable = result.error.code == DataErrorCode.STORAGE_WRITE_FAILED)
                    is RepoResult.Ok -> {
                        previewId = null // A committed token must never be submitted again.
                        val activationError = try {
                            when (activate(result.value.semesterId)) {
                                is RepoResult.Ok -> null
                                is RepoResult.Err -> "课程已保存，但切换到目标学期失败。请在设置中选择该学期。"
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            "课程已保存，但切换到目标学期失败。请在设置中选择该学期。"
                        }
                        mutableState.value = OnlineImportPreviewState.Saved(summary, result.value, activationError)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableState.value = OnlineImportPreviewState.SaveFailed(summary,
                    "保存状态未能确认，请重新读取并核对本地课表后重试。", retryable = false)
            }
        }
    }
}

@HiltViewModel
class OnlineImportPreviewViewModel @Inject constructor(repository: ScheduleRepository) : ViewModel() {
    private val session = OnlineImportPreviewSession(viewModelScope,
        repository::prepareImport, repository::discardImport,
        repository::commitImport, repository::setActiveSemester)
    val state: StateFlow<OnlineImportPreviewState> = session.state

    fun show(parsed: ParsedZhengfangSchedule) = session.show(parsed)
    fun invalidate() = session.invalidate()
    fun confirm() = session.confirm()
}

private fun saveError(code: DataErrorCode): String = when (code) {
    DataErrorCode.STORAGE_WRITE_FAILED -> "保存失败，事务已回滚，旧课表保持不变。请再次确认后重试。"
    DataErrorCode.PREVIEW_STALE -> "预览已失效，旧课表保持不变。请重新读取。"
    DataErrorCode.SCOPE_CONFLICT -> "来源学期绑定已变化，旧课表保持不变。请重新读取并核对目标学期。"
    DataErrorCode.IMPORT_BLOCKED -> "导入校验未通过，旧课表保持不变。请重新读取。"
    DataErrorCode.CONFIG_REQUIRED, DataErrorCode.CONFIG_INVALID, DataErrorCode.CONFIG_CONFLICT ->
        "本地学期或节次配置已变化，旧课表保持不变。请检查设置后重新读取。"
    else -> "保存失败（$code），旧课表保持不变。请重新读取。"
}

private fun previewError(code: DataErrorCode): String = when (code) {
    DataErrorCode.STORAGE_READ_FAILED -> "无法读取本地学期与旧课表，预览准备失败。"
    DataErrorCode.SEMESTER_NOT_FOUND -> "本地目标学期不存在，请返回检查配置。"
    else -> "预览准备失败（$code），请返回重新读取。"
}
