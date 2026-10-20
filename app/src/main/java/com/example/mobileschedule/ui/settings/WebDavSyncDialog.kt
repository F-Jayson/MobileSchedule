package com.example.mobileschedule.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobileschedule.data.cloud.WebDavBackupItem
import com.example.mobileschedule.data.cloud.WebDavConfig

@Composable
fun WebDavSyncCard(
    isConfigured: Boolean,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("management_webdav_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("☁️", fontSize = 16.sp)
                    Text(
                        "WebDAV 私有云同步",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (isConfigured) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "已配置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "支持坚果云、自建 Nextcloud、NAS 或 AList，实现课表跨设备云备份与一键恢复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onOpenSync,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("management_webdav_open_button"),
            ) {
                Text(if (isConfigured) "云端同步" else "配置网盘")
            }
        }
    }
}

@Composable
fun WebDavSyncDialog(
    config: WebDavConfig,
    backups: List<WebDavBackupItem>,
    isTesting: Boolean,
    isSyncing: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    activeSemesterId: Long?,
    onDismiss: () -> Unit,
    onSaveConfig: (WebDavConfig) -> Unit,
    onTestConnection: (WebDavConfig) -> Unit,
    onBackupSemester: (Long) -> Unit,
    onRestoreBackup: (WebDavBackupItem) -> Unit,
    onDeleteBackup: (WebDavBackupItem) -> Unit,
    onRefreshList: () -> Unit,
) {
    var serverUrl by rememberSaveable(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var username by rememberSaveable(config.username) { mutableStateOf(config.username) }
    var password by rememberSaveable(config.password) { mutableStateOf(config.password) }
    var remoteDir by rememberSaveable(config.remoteDir) { mutableStateOf(config.remoteDir) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    var isConfigExpanded by rememberSaveable { mutableStateOf(!config.isConfigured) }
    var pendingDeleteBackup by remember { mutableStateOf<WebDavBackupItem?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag("webdav_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("☁️", fontSize = 20.sp)
                            Text(
                                "WebDAV 课表云同步",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "跨设备同步课表，安全保存至你的私有网盘",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("webdav_dialog_close"),
                    ) {
                        Text("关闭")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Status & Error Messages
                if (!statusMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().testTag("webdav_status_msg"),
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().testTag("webdav_error_msg"),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Section 1: Server Configuration (Collapsible)
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isConfigExpanded = !isConfigExpanded }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text("⚙️", fontSize = 16.sp)
                                        Text(
                                            "服务器连接配置",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = if (isConfigExpanded) "收起 ▲" else "展开修改 ▼",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                if (isConfigExpanded) {
                                    Spacer(Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = serverUrl,
                                        onValueChange = { serverUrl = it },
                                        label = { Text("WebDAV 服务器地址") },
                                        placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("webdav_server_url"),
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text("用户名 / 账号") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("webdav_username"),
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("应用密码 / 访问凭证") },
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        trailingIcon = {
                                            IconButton(onClick = { showPassword = !showPassword }) {
                                                Text(if (showPassword) "🙈" else "👁️")
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("webdav_password"),
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = remoteDir,
                                        onValueChange = { remoteDir = it },
                                        label = { Text("远程备份目录") },
                                        placeholder = { Text("/MobileSchedule/") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("webdav_remote_dir"),
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val currentDraft = WebDavConfig(
                                                    serverUrl = serverUrl,
                                                    username = username,
                                                    password = password,
                                                    remoteDir = remoteDir,
                                                )
                                                onTestConnection(currentDraft)
                                            },
                                            enabled = !isTesting && !isSyncing && serverUrl.isNotBlank() && username.isNotBlank(),
                                            modifier = Modifier.weight(1f).testTag("webdav_test_button"),
                                        ) {
                                            if (isTesting) {
                                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(6.dp))
                                                Text("验证中…")
                                            } else {
                                                Text("测试连接")
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                val newConfig = WebDavConfig(
                                                    serverUrl = serverUrl,
                                                    username = username,
                                                    password = password,
                                                    remoteDir = remoteDir,
                                                )
                                                onSaveConfig(newConfig)
                                                isConfigExpanded = false
                                            },
                                            enabled = !isTesting && !isSyncing && serverUrl.isNotBlank() && username.isNotBlank(),
                                            modifier = Modifier.weight(1f).testTag("webdav_save_button"),
                                        ) {
                                            Text("保存配置")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Actions (Upload Active Semester)
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        "一键备份当前学期",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "将当前活动课表的全部课程、节次与日历静默上传到云端",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (activeSemesterId != null) {
                                            onBackupSemester(activeSemesterId)
                                        }
                                    },
                                    enabled = !isTesting && !isSyncing && config.isConfigured && activeSemesterId != null,
                                    modifier = Modifier.testTag("webdav_backup_now_button"),
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("上传中…")
                                    } else {
                                        Text("立即上传")
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Cloud Backups List
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "云端备份历史 (${backups.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            IconButton(
                                onClick = onRefreshList,
                                enabled = !isTesting && !isSyncing && config.isConfigured,
                                modifier = Modifier.testTag("webdav_refresh_button"),
                            ) {
                                Text("🔄")
                            }
                        }
                    }

                    if (backups.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (!config.isConfigured) {
                                            "请先保存 WebDAV 服务器配置并连接"
                                        } else {
                                            "云端目录暂无课表备份，可点击上方“立即上传”备份课表"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(backups, key = { it.fileName }) { item ->
                            CloudBackupRow(
                                item = item,
                                enabled = !isTesting && !isSyncing,
                                onRestore = { onRestoreBackup(item) },
                                onDelete = { pendingDeleteBackup = item },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteBackup?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBackup = null },
            title = { Text("删除云端备份") },
            text = { Text("确定要从云端删除备份文件“${target.fileName}”吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteBackup = null
                        onDeleteBackup(target)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBackup = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CloudBackupRow(
    item: WebDavBackupItem,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("webdav_backup_item_${item.fileName}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = item.semesterName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.formattedDate} · ${item.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("webdav_restore_button_${item.fileName}"),
                ) {
                    Text("下载恢复", style = MaterialTheme.typography.labelMedium)
                }

                IconButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.size(32.dp).testTag("webdav_delete_button_${item.fileName}"),
                ) {
                    Text("🗑️", fontSize = 14.sp)
                }
            }
        }
    }
}
