package com.example.mobileschedule.ui.importentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.importer.ParsedZhengfangSchedule
import com.example.mobileschedule.data.importer.ZhengfangImportPreviewSummary
import com.example.mobileschedule.data.importer.toPreviewSummary
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.ImportPreview
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
}

/** One live preview at a time. Late results are discarded and cannot replace a newer selection. */
internal class OnlineImportPreviewSession(
    private val scope: CoroutineScope,
    private val prepare: suspend (ImportRequest) -> RepoResult<ImportPreview>,
    private val discard: suspend (String) -> RepoResult<Unit>,
) {
    private val mutableState = MutableStateFlow<OnlineImportPreviewState>(OnlineImportPreviewState.Idle)
    val state: StateFlow<OnlineImportPreviewState> = mutableState
    private var generation = 0L
    private var previewId: String? = null

    fun show(parsed: ParsedZhengfangSchedule) {
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
}

@HiltViewModel
class OnlineImportPreviewViewModel @Inject constructor(repository: ScheduleRepository) : ViewModel() {
    private val session = OnlineImportPreviewSession(viewModelScope,
        repository::prepareImport, repository::discardImport)
    val state: StateFlow<OnlineImportPreviewState> = session.state

    fun show(parsed: ParsedZhengfangSchedule) = session.show(parsed)
    fun invalidate() = session.invalidate()
}

private fun previewError(code: DataErrorCode): String = when (code) {
    DataErrorCode.STORAGE_READ_FAILED -> "无法读取本地学期与旧课表，预览准备失败。"
    DataErrorCode.SEMESTER_NOT_FOUND -> "本地目标学期不存在，请返回检查配置。"
    else -> "预览准备失败（$code），请返回重新读取。"
}
