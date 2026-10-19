package com.example.mobileschedule.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.BuildConfig
import com.example.mobileschedule.data.model.DownloadMirror
import com.example.mobileschedule.data.preferences.AppThemeMode

@Composable
fun SettingsRoute(
    onManageSchedule: () -> Unit,
    onDisplaySettings: () -> Unit,
    onWidgetSettings: () -> Unit = {},
    onSelectMirror: () -> Unit,
    onAbout: () -> Unit = {},
    onChangelog: () -> Unit = {},
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.actionError.collectAsStateWithLifecycle()
    val mirror by viewModel.selectedMirror.collectAsStateWithLifecycle()
    val quickImport by viewModel.quickImport.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderAdvanceMinutes by viewModel.reminderAdvanceMinutes.collectAsStateWithLifecycle()
    val ongoingNotificationEnabled by viewModel.ongoingNotificationEnabled.collectAsStateWithLifecycle()

    SettingsHomeScreen(
        state = state,
        onManageSchedule = onManageSchedule,
        onDisplaySettings = onDisplaySettings,
        onWidgetSettings = onWidgetSettings,
        onCheckUpdate = { viewModel.checkUpdate() },
        selectedMirror = mirror,
        onSelectMirror = onSelectMirror,
        quickImport = quickImport,
        onQuickImportChange = viewModel::setQuickImport,
        reminderEnabled = reminderEnabled,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        reminderAdvanceMinutes = reminderAdvanceMinutes,
        onReminderAdvanceMinutesChange = viewModel::setReminderAdvanceMinutes,
        ongoingNotificationEnabled = ongoingNotificationEnabled,
        onOngoingNotificationEnabledChange = viewModel::setOngoingNotificationEnabled,
        autoUpdate = autoUpdate,
        onAutoUpdateChange = viewModel::setAutoUpdate,
        themeMode = themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        onAbout = onAbout,
        onChangelog = onChangelog,
        actionError = error,
    )
}

@Composable
fun SettingsHomeScreen(
    state: SettingsHomeUiState,
    onManageSchedule: () -> Unit = {},
    onDisplaySettings: () -> Unit = {},
    onWidgetSettings: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    selectedMirror: DownloadMirror = DownloadMirror.DEFAULT,
    onSelectMirror: () -> Unit = {},
    quickImport: Boolean = false,
    onQuickImportChange: (Boolean) -> Unit = {},
    reminderEnabled: Boolean = false,
    onReminderEnabledChange: (Boolean) -> Unit = {},
    reminderAdvanceMinutes: Int = 15,
    onReminderAdvanceMinutesChange: (Int) -> Unit = {},
    ongoingNotificationEnabled: Boolean = false,
    onOngoingNotificationEnabledChange: (Boolean) -> Unit = {},
    autoUpdate: Boolean = true,
    onAutoUpdateChange: (Boolean) -> Unit = {},
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onAbout: () -> Unit = {},
    onChangelog: () -> Unit = {},
    actionError: String? = null,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            onReminderEnabledChange(true)
        } else {
            onReminderEnabledChange(false)
        }
    }

    val onToggleReminder: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onReminderEnabledChange(true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onReminderEnabledChange(enabled)
        }
    }

    val ongoingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        onOngoingNotificationEnabledChange(isGranted)
    }

    val onToggleOngoingNotification: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onOngoingNotificationEnabledChange(true)
            } else {
                ongoingPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onOngoingNotificationEnabledChange(enabled)
        }
    }

    val activeName = (state as? SettingsHomeUiState.Ready)?.let { ready ->
        ready.semesters.firstOrNull { it.id == ready.activeId }?.displayName
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("settings_page"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            SettingsGroup("课表管理") {
                SettingsEntry("管理课表", activeName ?: "导入、导出、清除与学期配置", "settings_manage_schedule", onManageSchedule)
            }
        }
        item {
            SettingsGroup("外观与样式") {
                SettingsEntry("应用主题", themeMode.title, "settings_theme_entry") {
                    showThemeDialog = true
                }
                SettingsEntry("桌面小组件定制", "背景色、透明度、圆角及展示内容", "settings_widget_customization", onWidgetSettings)
            }
        }
        item {
            SettingsGroup("偏好设置") {
                SettingsEntry("课表显示", "课程地点、教师与显示时间", "settings_display_settings", onDisplaySettings)
                SettingsToggle(
                    "快速导入",
                    "自动选择当前学年和学期，解析完成后导入并返回课表；同来源学期的课程将被替换。",
                    quickImport,
                    onQuickImportChange,
                    "settings_quick_import",
                )
                SettingsToggle(
                    "课前提醒",
                    "在课程开始前发送通知提醒，避免遗忘上课",
                    reminderEnabled,
                    onToggleReminder,
                    "settings_reminder_enabled",
                )
                if (reminderEnabled) {
                    SettingsEntry(
                        "提前提醒时间",
                        "提前 ${reminderAdvanceMinutes} 分钟提醒",
                        "settings_reminder_advance",
                    ) {
                        showReminderDialog = true
                    }
                }
                SettingsToggle(
                    "通知栏常驻上课卡片",
                    "当天有课时在通知栏常驻显示当前与下一节课程，下课或结课后自动撤销",
                    ongoingNotificationEnabled,
                    onToggleOngoingNotification,
                    "settings_ongoing_notification_enabled",
                )
            }
        }
        item {
            SettingsGroup("应用更新") {
                SettingsEntry("检查新版本", "当前版本 v${BuildConfig.VERSION_NAME}", "settings_check_update", onCheckUpdate)
                SettingsEntry("更新日志", "版本演进历史与新特性说明", "settings_changelog_entry", onChangelog)
                SettingsEntry("更新源", selectedMirror.displayName.substringBefore(" ("), "settings_select_mirror", onSelectMirror)
                SettingsToggle("自动检查更新", "启动时检查新版本，有更新时提醒", autoUpdate, onAutoUpdateChange, "settings_auto_update")
            }
        }
        item {
            SettingsGroup("关于与支持") {
                SettingsEntry("关于与帮助", "版本、环境信息、开源许可与隐私说明", "settings_about_entry", onAbout)
            }
        }
        if (actionError != null) item { Text(actionError, color = MaterialTheme.colorScheme.error) }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = themeMode,
            onSelect = {
                onThemeModeChange(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showReminderDialog) {
        ReminderAdvanceDialog(
            currentMinutes = reminderAdvanceMinutes,
            onSelect = {
                onReminderAdvanceMinutesChange(it)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false },
        )
    }
}

@Composable
private fun ReminderAdvanceDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(5, 10, 15, 20, 30, 45, 60)
    var isCustom by remember { mutableStateOf(currentMinutes !in presets) }
    var selectedMinutes by remember { mutableIntStateOf(currentMinutes) }
    var customText by remember { mutableStateOf(if (currentMinutes !in presets) currentMinutes.toString() else "") }
    var customError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reminder_advance_dialog"),
        title = { Text("提前提醒时间", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                presets.forEach { minutes ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                isCustom = false
                                selectedMinutes = minutes
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (!isCustom && selectedMinutes == minutes),
                            onClick = {
                                isCustom = false
                                selectedMinutes = minutes
                            },
                            modifier = Modifier.testTag("reminder_preset_$minutes"),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("提前 $minutes 分钟", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { isCustom = true }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isCustom,
                        onClick = { isCustom = true },
                        modifier = Modifier.testTag("reminder_preset_custom"),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("自定义分钟数", style = MaterialTheme.typography.bodyLarge)
                }

                if (isCustom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(3)
                            customText = filtered
                            val num = filtered.toIntOrNull()
                            if (num != null && num in 1..180) {
                                customError = false
                                selectedMinutes = num
                            } else {
                                customError = true
                            }
                        },
                        label = { Text("提前分钟数 (1-180)") },
                        singleLine = true,
                        isError = customError,
                        supportingText = if (customError) {
                            { Text("请输入 1 到 180 之间的整数") }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("reminder_custom_input"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isCustom) {
                        val num = customText.toIntOrNull()
                        if (num != null && num in 1..180) {
                            onSelect(num)
                        } else {
                            customError = true
                        }
                    } else {
                        onSelect(selectedMinutes)
                    }
                },
                modifier = Modifier.testTag("reminder_confirm_btn"),
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentMode: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用主题", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                AppThemeMode.entries.forEach { mode ->
                    val tag = when (mode) {
                        AppThemeMode.SYSTEM -> "theme_radio_system"
                        AppThemeMode.LIGHT -> "theme_radio_light"
                        AppThemeMode.DARK -> "theme_radio_dark"
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (currentMode == mode),
                            onClick = { onSelect(mode) },
                            modifier = Modifier.testTag(tag),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(mode.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsEntry(title: String, description: String, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggle(title: String, description: String, value: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange, modifier = Modifier.padding(start = 12.dp).testTag(tag))
    }
}
