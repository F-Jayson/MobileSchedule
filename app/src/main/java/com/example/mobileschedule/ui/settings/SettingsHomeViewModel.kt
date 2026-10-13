package com.example.mobileschedule.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.AppUpdateInfo
import com.example.mobileschedule.data.model.DownloadMirror
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.repository.ScheduleRepository
import com.example.mobileschedule.data.updater.AppUpdateChecker
import com.example.mobileschedule.data.updater.AppUpdateDownloader
import com.example.mobileschedule.data.updater.DownloadSourceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class SettingsHomeViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
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

    var updateChecker: AppUpdateChecker = AppUpdateChecker()
    var updateDownloader: AppUpdateDownloader = AppUpdateDownloader(context)
    val downloadSourceStore = DownloadSourceStore(context)

    private val mutableSelectedMirror = MutableStateFlow(downloadSourceStore.getSelectedMirror())
    val selectedMirror: StateFlow<DownloadMirror> = mutableSelectedMirror.asStateFlow()

    private val mutableCustomPrefix = MutableStateFlow(downloadSourceStore.getCustomPrefix())
    val customPrefix: StateFlow<String> = mutableCustomPrefix.asStateFlow()

    private val mutableBlogOrigin = MutableStateFlow(downloadSourceStore.getBlogOrigin())
    val blogOrigin: StateFlow<String> = mutableBlogOrigin.asStateFlow()

    private val mutableUpdateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = mutableUpdateState.asStateFlow()

    private var downloadJob: Job? = null
    private var lastCheckedInfo: AppUpdateInfo? = null

    fun selectMirror(
        mirror: DownloadMirror,
        customPrefix: String = mutableCustomPrefix.value,
        blogOrigin: String = mutableBlogOrigin.value,
    ) {
        mutableSelectedMirror.value = mirror
        mutableCustomPrefix.value = customPrefix
        mutableBlogOrigin.value = blogOrigin
        downloadSourceStore.saveSelectedMirror(mirror, customPrefix, blogOrigin)
    }

    fun activate(semesterId: Long) {
        mutableActionError.value = null
        viewModelScope.launch {
            if (repository.setActiveSemester(semesterId) is RepoResult.Err) {
                mutableActionError.value = "无法切换当前学期，请重新打开设置页后重试。"
            }
        }
    }

    fun checkUpdate() {
        mutableUpdateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            val result = updateChecker.checkUpdate(
                mirror = mutableSelectedMirror.value,
                blogOrigin = mutableBlogOrigin.value,
            )
            result.onSuccess { info ->
                lastCheckedInfo = info
                if (info.hasUpdate) {
                    mutableUpdateState.value = UpdateUiState.Available(info)
                } else {
                    mutableUpdateState.value = UpdateUiState.Latest(info)
                }
            }.onFailure { error ->
                mutableUpdateState.value = UpdateUiState.Error(
                    message = error.localizedMessage ?: "检查更新失败，请稍后重试。",
                    info = lastCheckedInfo,
                )
            }
        }
    }

    fun startDownload(info: AppUpdateInfo) {
        val downloadUrl = info.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            mutableUpdateState.value = UpdateUiState.Error(
                message = "未找到可用的 APK 下载资源，请使用浏览器下载。",
                info = info,
            )
            return
        }

        downloadJob?.cancel()
        mutableUpdateState.value = UpdateUiState.Downloading(
            info = info,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = info.apkSize ?: 0L,
        )

        downloadJob = viewModelScope.launch {
            val result = updateDownloader.downloadApk(
                downloadUrl = downloadUrl,
                versionName = info.latestVersionName,
                mirror = mutableSelectedMirror.value,
                customPrefix = mutableCustomPrefix.value,
                blogOrigin = mutableBlogOrigin.value,
                onProgress = { progress, downloaded, total ->
                    mutableUpdateState.value = UpdateUiState.Downloading(
                        info = info,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                    )
                },
            )

            result.onSuccess { file ->
                mutableUpdateState.value = UpdateUiState.ReadyToInstall(info, file)
            }.onFailure { error ->
                mutableUpdateState.value = UpdateUiState.Error(
                    message = error.localizedMessage ?: "下载安装包失败，请检查网络或重试。",
                    info = info,
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        mutableUpdateState.value = UpdateUiState.Idle
    }

    fun dismissUpdateDialog() {
        if (mutableUpdateState.value is UpdateUiState.Downloading) {
            cancelDownload()
        } else {
            mutableUpdateState.value = UpdateUiState.Idle
        }
    }

    fun retryUpdate() {
        val current = mutableUpdateState.value
        if (current is UpdateUiState.Error && current.info != null && current.info.hasUpdate) {
            startDownload(current.info)
        } else {
            checkUpdate()
        }
    }
}
