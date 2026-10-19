package com.example.mobileschedule.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.data.model.AppUpdateInfo
import com.example.mobileschedule.data.model.DownloadMirror
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterImportPreview
import com.example.mobileschedule.data.repository.ScheduleRepository
import com.example.mobileschedule.data.repository.ScheduleManagementService
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import com.example.mobileschedule.data.preferences.AppThemeMode
import android.net.Uri
import com.example.mobileschedule.data.reminder.CourseReminderNotifier
import com.example.mobileschedule.data.updater.AppUpdateChecker
import com.example.mobileschedule.data.updater.AppUpdateDownloader
import com.example.mobileschedule.data.updater.DownloadSourceStore
import com.example.mobileschedule.data.calendar.SystemCalendarSyncManager
import com.example.mobileschedule.ui.widget.ScheduleWidgetNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SettingsHomeUiState {
    data object Loading : SettingsHomeUiState
    data object Error : SettingsHomeUiState
    data class Ready(val semesters: List<Semester>, val activeId: Long?) : SettingsHomeUiState
}

@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    @param:ApplicationContext private val context: Context,
    private val management: ScheduleManagementService,
    private val calendarSyncManager: SystemCalendarSyncManager = SystemCalendarSyncManager(context),
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
    private val mutableActionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = mutableActionMessage.asStateFlow()
    private val mutableManaging = MutableStateFlow(false)
    val managing: StateFlow<Boolean> = mutableManaging.asStateFlow()
    private val preferences = AppPreferencesStore(context)
    private val preferenceWrites = Mutex()
    private val mutableQuickImport = MutableStateFlow(preferences.isQuickImportEnabled())
    val quickImport: StateFlow<Boolean> = mutableQuickImport.asStateFlow()
    private val mutableAutoUpdate = MutableStateFlow(preferences.isAutoUpdateEnabled())
    val autoUpdate: StateFlow<Boolean> = mutableAutoUpdate.asStateFlow()

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
    private var checkJob: Job? = null
    private var automaticCheck = false
    private var startupCheckDone = false

    fun setQuickImport(enabled: Boolean) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                if (withContext(Dispatchers.IO) { preferences.saveQuickImport(enabled) }) mutableQuickImport.value = enabled
                else mutableActionError.value = "快速导入设置保存失败，请重试。"
            }
        }
    }

    val themeMode: StateFlow<AppThemeMode> = preferences.themeModeFlow

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                if (!withContext(Dispatchers.IO) { preferences.saveThemeMode(mode) }) {
                    mutableActionError.value = "外观主题保存失败，请重试。"
                }
            }
        }
    }

    val reminderEnabled: StateFlow<Boolean> = preferences.reminderEnabledFlow
    val reminderAdvanceMinutes: StateFlow<Int> = preferences.reminderAdvanceMinutesFlow

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                if (withContext(Dispatchers.IO) { preferences.saveReminderEnabled(enabled) }) {
                    CourseReminderNotifier.notifyDataChanged()
                } else {
                    mutableActionError.value = "课前提醒设置保存失败，请重试。"
                }
            }
        }
    }

    fun setReminderAdvanceMinutes(minutes: Int) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                if (withContext(Dispatchers.IO) { preferences.saveReminderAdvanceMinutes(minutes) }) {
                    CourseReminderNotifier.notifyDataChanged()
                } else {
                    mutableActionError.value = "提前提醒时间保存失败，请重试。"
                }
            }
        }
    }

    val ongoingNotificationEnabled: StateFlow<Boolean> = preferences.ongoingNotificationEnabledFlow

    fun setOngoingNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                if (withContext(Dispatchers.IO) { preferences.saveOngoingNotificationEnabled(enabled) }) {
                    if (!enabled) {
                        com.example.mobileschedule.data.notification.OngoingScheduleNotificationManager.cancelNotification(context)
                    } else {
                        com.example.mobileschedule.data.notification.OngoingScheduleNotificationManager.notifyDataChanged()
                    }
                } else {
                    mutableActionError.value = "常驻通知卡片设置保存失败，请重试。"
                }
            }
        }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            preferenceWrites.withLock {
                if (withContext(Dispatchers.IO) { preferences.saveAutoUpdate(enabled) }) {
                    mutableAutoUpdate.value = enabled
                    if (!enabled && automaticCheck) checkJob?.cancel()
                } else mutableActionError.value = "自动检查更新设置保存失败，请重试。"
            }
        }
    }

    fun checkUpdateOnStartup() {
        if (startupCheckDone) return
        startupCheckDone = true
        if (mutableAutoUpdate.value) checkUpdate(automatic = true)
    }

    private val mutableImportPreview = MutableStateFlow<SemesterImportPreview?>(null)
    val importPreview: StateFlow<SemesterImportPreview?> = mutableImportPreview.asStateFlow()

    fun clearSemester(semesterId: Long) = manage("清除失败，请重试。") {
        val count = management.clearSemester(semesterId)
        ScheduleWidgetNotifier.notifyDataChanged()
        CourseReminderNotifier.notifyDataChanged()
        "已清除 $count 条课程安排"
    }

    fun deleteSemester(semesterId: Long) = manage("删除学期失败，请重试。") {
        management.deleteSemester(semesterId)
        ScheduleWidgetNotifier.notifyDataChanged()
        CourseReminderNotifier.notifyDataChanged()
        "学期已删除"
    }

    fun exportSemester(semesterId: Long, uri: Uri) = manage("导出失败，请检查保存位置后重试。") {
        val snapshot = management.exportSemester(semesterId)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
            it.write(snapshot)
        }
        "课表已导出"
    }

    fun exportSemesterIcs(semesterId: Long, uri: Uri) = manage("导出日历文件失败，请检查保存位置后重试。") {
        val icsText = management.exportSemesterIcs(semesterId)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
            it.write(icsText)
        }
        "ICS 日历文件已成功导出"
    }

    fun syncSemesterToSystemCalendar(semesterId: Long) = manage("同步至系统日历失败，请重试。") {
        val events = management.getSemesterCalendarEvents(semesterId)
        if (events.isEmpty()) {
            "该学期暂无课程安排，无需同步"
        } else {
            val result = calendarSyncManager.syncEvents(events)
            if (result.success) {
                result.message
            } else {
                throw IllegalStateException(result.message)
            }
        }
    }

    fun clearSystemCalendar() = manage("移除系统日历课表失败，请重试。") {
        val result = calendarSyncManager.clearCalendar()
        if (result.success) {
            result.message
        } else {
            throw IllegalStateException(result.message)
        }
    }

    fun loadSemesterJsonBackup(uri: Uri) {
        if (mutableManaging.value) return
        mutableManaging.value = true
        mutableActionError.value = null
        mutableActionMessage.value = null
        viewModelScope.launch {
            try {
                val previewResult = management.importSemesterFromJson(uri)
                previewResult.onSuccess { preview ->
                    mutableImportPreview.value = preview
                }.onFailure { error ->
                    mutableActionError.value = error.localizedMessage ?: "备份文件解析失败，请检查文件格式。"
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                mutableActionError.value = e.localizedMessage ?: "备份文件解析失败，请检查文件格式。"
            } finally {
                mutableManaging.value = false
            }
        }
    }

    fun dismissImportDialog() {
        mutableImportPreview.value = null
    }

    fun confirmImportJson(preview: SemesterImportPreview, overwriteExisting: Boolean) {
        mutableImportPreview.value = null
        manage(if (overwriteExisting) "覆盖学期失败，请重试。" else "导入新学期失败，请重试。") {
            val result = management.commitImport(preview, overwriteExisting)
            result.getOrThrow()
            ScheduleWidgetNotifier.notifyDataChanged()
            CourseReminderNotifier.notifyDataChanged()
            if (overwriteExisting) {
                "已恢复并覆盖“${preview.displayName}”（共 ${preview.arrangementCount} 条课程）"
            } else {
                "已导入新学期（共 ${preview.arrangementCount} 条课程）"
            }
        }
    }

    private fun manage(failureMessage: String, action: suspend () -> String) {
        if (mutableManaging.value) return
        mutableManaging.value = true
        mutableActionError.value = null
        mutableActionMessage.value = null
        viewModelScope.launch {
            try {
                mutableActionMessage.value = withContext(Dispatchers.IO) { action() }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableActionError.value = failureMessage
            } finally {
                mutableManaging.value = false
            }
        }
    }

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

    fun checkUpdate(automatic: Boolean = false) {
        if (mutableUpdateState.value is UpdateUiState.Downloading || mutableUpdateState.value is UpdateUiState.ReadyToInstall) return
        if (checkJob?.isActive == true) {
            if (automatic || !automaticCheck) return
            checkJob?.cancel()
        }
        automaticCheck = automatic
        if (!automatic) mutableUpdateState.value = UpdateUiState.Checking
        checkJob = viewModelScope.launch {
            val result = updateChecker.checkUpdate(
                mirror = mutableSelectedMirror.value,
                blogOrigin = mutableBlogOrigin.value,
            )
            result.onSuccess { info ->
                lastCheckedInfo = info
                if (info.hasUpdate) {
                    mutableUpdateState.value = UpdateUiState.Available(info)
                } else {
                    if (!automatic) mutableUpdateState.value = UpdateUiState.Latest(info)
                }
            }.onFailure { error ->
                if (!automatic) mutableUpdateState.value = UpdateUiState.Error(
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
        checkJob?.cancel()
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
