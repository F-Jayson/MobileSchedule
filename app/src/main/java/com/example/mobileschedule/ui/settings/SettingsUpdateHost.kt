package com.example.mobileschedule.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.data.updater.AppUpdateInstaller

@Composable
fun SettingsUpdateHost(viewModel: SettingsHomeViewModel, showMirror: Boolean, onShowMirror: () -> Unit, onHideMirror: () -> Unit) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val mirror by viewModel.selectedMirror.collectAsStateWithLifecycle()
    val prefix by viewModel.customPrefix.collectAsStateWithLifecycle()
    val blog by viewModel.blogOrigin.collectAsStateWithLifecycle()
    UpdateDialog(updateState, mirror, viewModel::dismissUpdateDialog, viewModel::retryUpdate,
        viewModel::startDownload, viewModel::cancelDownload,
        { AppUpdateInstaller.installApk(context, it) }, { AppUpdateInstaller.openInBrowser(context, it) }, onShowMirror)
    if (showMirror) DownloadMirrorDialog(mirror, prefix, blog, onHideMirror) { selected, custom, origin ->
        viewModel.selectMirror(selected, custom, origin)
        onHideMirror()
    }
}
