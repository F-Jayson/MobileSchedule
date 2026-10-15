package com.example.mobileschedule.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileschedule.BuildConfig
import com.example.mobileschedule.data.model.AppUpdateInfo
import com.example.mobileschedule.data.updater.AppUpdateChecker
import com.example.mobileschedule.data.updater.DownloadSourceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangelogUiState(
    val entries: List<ChangelogEntry> = ChangelogRepository.bundledChangelogs,
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
    val hasChecked: Boolean = false,
    val currentVersion: String = BuildConfig.VERSION_NAME,
)

@HiltViewModel
class ChangelogViewModel(
    private val updateChecker: AppUpdateChecker,
    private val downloadSourceStore: DownloadSourceStore,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) : ViewModel() {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        AppUpdateChecker(),
        DownloadSourceStore(context),
        BuildConfig.VERSION_NAME,
    )

    private val _uiState = MutableStateFlow(
        ChangelogUiState(
            entries = ChangelogRepository.bundledChangelogs,
            currentVersion = currentVersion,
        )
    )
    val uiState: StateFlow<ChangelogUiState> = _uiState.asStateFlow()

    init {
        checkRemoteUpdate()
    }

    fun checkRemoteUpdate() {
        if (_uiState.value.isChecking) return

        _uiState.update { it.copy(isChecking = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val mirror = downloadSourceStore.getSelectedMirror()
            val blogOrigin = downloadSourceStore.getBlogOrigin()

            val result = updateChecker.checkUpdate(
                currentVersion = currentVersion,
                mirror = mirror,
                blogOrigin = blogOrigin,
            )

            result.onSuccess { info ->
                val merged = ChangelogRepository.mergeWithRemoteUpdate(info, currentVersion)
                _uiState.update {
                    it.copy(
                        entries = merged,
                        isChecking = false,
                        hasChecked = true,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        hasChecked = true,
                        errorMessage = error.localizedMessage ?: "检查最新版本失败，当前仅展示本地历史更新日志",
                    )
                }
            }
        }
    }
}
