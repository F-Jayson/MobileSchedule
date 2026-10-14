package com.example.mobileschedule.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.BuildConfig
import com.example.mobileschedule.data.model.DownloadMirror
import com.example.mobileschedule.data.preferences.AppThemeMode

@Composable
fun SettingsRoute(
    onManageSchedule: () -> Unit,
    onDisplaySettings: () -> Unit,
    onSelectMirror: () -> Unit,
    onAbout: () -> Unit = {},
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.actionError.collectAsStateWithLifecycle()
    val mirror by viewModel.selectedMirror.collectAsStateWithLifecycle()
    val quickImport by viewModel.quickImport.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    SettingsHomeScreen(
        state = state,
        onManageSchedule = onManageSchedule,
        onDisplaySettings = onDisplaySettings,
        onCheckUpdate = { viewModel.checkUpdate() },
        selectedMirror = mirror,
        onSelectMirror = onSelectMirror,
        quickImport = quickImport,
        onQuickImportChange = viewModel::setQuickImport,
        autoUpdate = autoUpdate,
        onAutoUpdateChange = viewModel::setAutoUpdate,
        themeMode = themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        onAbout = onAbout,
        actionError = error,
    )
}

@Composable
fun SettingsHomeScreen(
    state: SettingsHomeUiState,
    onManageSchedule: () -> Unit = {},
    onDisplaySettings: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    selectedMirror: DownloadMirror = DownloadMirror.DEFAULT,
    onSelectMirror: () -> Unit = {},
    quickImport: Boolean = false,
    onQuickImportChange: (Boolean) -> Unit = {},
    autoUpdate: Boolean = true,
    onAutoUpdateChange: (Boolean) -> Unit = {},
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onAbout: () -> Unit = {},
    actionError: String? = null,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    val activeName = (state as? SettingsHomeUiState.Ready)?.let { ready ->
        ready.semesters.firstOrNull { it.id == ready.activeId }?.displayName
    }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_page"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            SettingsGroup("课表管理") {
                SettingsEntry("管理课表", activeName ?: "导入、导出、清除与学期配置", "settings_manage_schedule", onManageSchedule)
            }
        }
        item {
            SettingsGroup("外观主题") {
                SettingsEntry("应用主题", themeMode.title, "settings_theme_entry") {
                    showThemeDialog = true
                }
            }
        }
        item {
            SettingsGroup("偏好设置") {
                SettingsEntry("课表显示", "课程地点、教师与显示时间", "settings_display_settings", onDisplaySettings)
                SettingsToggle("快速导入", "自动选择当前学年和学期，解析完成后导入并返回课表；同来源学期的课程将被替换。",
                    quickImport, onQuickImportChange, "settings_quick_import")
            }
        }
        item {
            SettingsGroup("应用更新") {
                SettingsEntry("检查新版本", "当前版本 v${BuildConfig.VERSION_NAME}", "settings_check_update", onCheckUpdate)
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
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsEntry(title: String, description: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag(tag)) {
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
