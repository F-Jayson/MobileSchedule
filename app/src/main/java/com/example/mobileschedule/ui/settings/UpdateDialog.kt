package com.example.mobileschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.mobileschedule.data.model.AppUpdateInfo
import com.example.mobileschedule.data.model.DownloadMirror
import com.example.mobileschedule.data.updater.DeviceAbiMatcher
import java.io.File
import java.util.Locale

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Latest(val info: AppUpdateInfo) : UpdateUiState
    data class Available(val info: AppUpdateInfo) : UpdateUiState
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateUiState
    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val apkFile: File,
    ) : UpdateUiState
    data class Error(
        val message: String,
        val info: AppUpdateInfo? = null,
    ) : UpdateUiState
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentMirror: DownloadMirror = DownloadMirror.DEFAULT,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSelectMirror: () -> Unit = {},
) {
    if (state is UpdateUiState.Idle) return

    AlertDialog(
        onDismissRequest = {
            if (state is UpdateUiState.Downloading) {
                onCancelDownload()
            } else {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.testTag("update_dialog"),
        title = {
            when (state) {
                UpdateUiState.Idle -> Unit
                UpdateUiState.Checking -> Text("检查更新", fontWeight = FontWeight.Bold)
                is UpdateUiState.Latest -> Text("已是最新版本", fontWeight = FontWeight.Bold)
                is UpdateUiState.Available -> Text("发现新版本 v${state.info.latestVersionName}", fontWeight = FontWeight.Bold)
                is UpdateUiState.Downloading -> Text("正在下载更新…", fontWeight = FontWeight.Bold)
                is UpdateUiState.ReadyToInstall -> Text("下载完成", fontWeight = FontWeight.Bold)
                is UpdateUiState.Error -> Text("检查更新提示", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            when (state) {
                UpdateUiState.Idle -> Unit
                UpdateUiState.Checking -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        Text("正在查询远端最新版本信息…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UpdateUiState.Latest -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "当前版本 (v${state.info.currentVersionName}) 已是最新版，暂无可用更新。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is UpdateUiState.Available -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "当前: v${state.info.currentVersionName}  →  最新: v${state.info.latestVersionName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.info.targetAbi?.let { abi ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            DeviceAbiMatcher.getAbiDisplayName(abi),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                state.info.apkSize?.let { size ->
                                    Text(
                                        formatFileSize(size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMirror() },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "下载源: ${currentMirror.displayName.substringBefore(" (")}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "切换源 ›",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Text("更新日志：", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    state.info.releaseNotes,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                is UpdateUiState.Downloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (state.totalBytes > 0)
                                    "${formatFileSize(state.downloadedBytes)} / ${formatFileSize(state.totalBytes)}"
                                else
                                    formatFileSize(state.downloadedBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "新版本 v${state.info.latestVersionName} 已下载完毕，准备好安装应用更新。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is UpdateUiState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                UpdateUiState.Idle -> Unit
                UpdateUiState.Checking -> Unit
                is UpdateUiState.Latest -> {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_dialog_confirm"),
                    ) {
                        Text("知道了")
                    }
                }
                is UpdateUiState.Available -> {
                    if (state.info.downloadUrl != null) {
                        Button(
                            onClick = { onStartDownload(state.info) },
                            modifier = Modifier.testTag("update_dialog_confirm"),
                        ) {
                            Text("立即更新")
                        }
                    } else {
                        Button(
                            onClick = { onOpenBrowser(state.info.browserUrl) },
                            modifier = Modifier.testTag("update_dialog_confirm"),
                        ) {
                            Text("前往下载")
                        }
                    }
                }
                is UpdateUiState.Downloading -> {
                    OutlinedButton(
                        onClick = { onOpenBrowser(state.info.browserUrl) },
                        modifier = Modifier.testTag("update_dialog_browser"),
                    ) {
                        Text("浏览器下载")
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstall(state.apkFile) },
                        modifier = Modifier.testTag("update_dialog_confirm"),
                    ) {
                        Text("立即安装")
                    }
                }
                is UpdateUiState.Error -> {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag("update_dialog_confirm"),
                    ) {
                        Text("重试")
                    }
                }
            }
        },
        dismissButton = {
            when (state) {
                UpdateUiState.Idle -> Unit
                UpdateUiState.Checking -> {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_dialog_dismiss"),
                    ) {
                        Text("取消")
                    }
                }
                is UpdateUiState.Latest -> Unit
                is UpdateUiState.Available -> {
                    Row {
                        TextButton(
                            onClick = { onOpenBrowser(state.info.browserUrl) },
                            modifier = Modifier.testTag("update_dialog_browser"),
                        ) {
                            Text("网页查看")
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("update_dialog_dismiss"),
                        ) {
                            Text("稍后再说")
                        }
                    }
                }
                is UpdateUiState.Downloading -> {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag("update_dialog_dismiss"),
                    ) {
                        Text("取消")
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_dialog_dismiss"),
                    ) {
                        Text("稍后安装")
                    }
                }
                is UpdateUiState.Error -> {
                    Row {
                        TextButton(
                            onClick = onSelectMirror,
                            modifier = Modifier.testTag("update_dialog_switch_mirror"),
                        ) {
                            Text("切换下载源")
                        }
                        Spacer(Modifier.width(4.dp))
                        state.info?.browserUrl?.let { url ->
                            TextButton(
                                onClick = { onOpenBrowser(url) },
                                modifier = Modifier.testTag("update_dialog_browser"),
                            ) {
                                Text("网页查看")
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("update_dialog_dismiss"),
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        },
    )
}
