package com.example.mobileschedule.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.data.model.Semester

@Composable
fun ScheduleManagementRoute(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onImport: () -> Unit,
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.actionError.collectAsStateWithLifecycle()
    val message by viewModel.actionMessage.collectAsStateWithLifecycle()
    val busy by viewModel.managing.collectAsStateWithLifecycle()
    var exportSemesterId by rememberSaveable { mutableStateOf<Long?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val semesterId = exportSemesterId
        exportSemesterId = null
        if (uri != null && semesterId != null) viewModel.exportSemester(semesterId, uri)
    }
    ScheduleManagementScreen(state, onBack, onCreate, onEdit, viewModel::activate, onImport,
        viewModel::clearSemester, { id ->
            exportSemesterId = id
            exporter.launch("课程表-$id.json")
        }, busy, error, message)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagementScreen(
    state: SettingsHomeUiState,
    onBack: () -> Unit = {},
    onCreate: () -> Unit = {},
    onEdit: (Long) -> Unit = {},
    onActivate: (Long) -> Unit = {},
    onImport: () -> Unit = {},
    onClear: (Long) -> Unit = {},
    onExport: (Long) -> Unit = {},
    busy: Boolean = false,
    actionError: String? = null,
    actionMessage: String? = null,
) {
    var clearing by remember { mutableStateOf<Semester?>(null) }
    clearing?.let { semester ->
        AlertDialog(onDismissRequest = { clearing = null },
            title = { Text("清除课程安排") },
            text = { Text("将清除“${semester.displayName}”的全部课程安排和导入记录，保留学期日历与节次配置。") },
            confirmButton = { TextButton(onClick = { clearing = null; onClear(semester.id) },
                enabled = !busy, modifier = Modifier.testTag("management_confirm_clear")) { Text("清除") } },
            dismissButton = { TextButton(onClick = { clearing = null }, modifier = Modifier.testTag("management_cancel_clear")) { Text("取消") } })
    }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(title = { Text("课表管理", fontWeight = FontWeight.Bold) }, windowInsets = WindowInsets(0),
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("schedule_management_page"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f).testTag("settings_import")) {
                        Text("导入课表")
                    }
                    OutlinedButton(onClick = onCreate, enabled = !busy, modifier = Modifier.weight(1f).testTag("settings_create")) {
                        Text("新建学期")
                    }
                }
            }
            if (busy) item { Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("正在处理课表…", Modifier.padding(start = 12.dp))
            } }
            if (actionError != null) item { Text(actionError, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("management_error")) }
            if (actionMessage != null) item { Text(actionMessage, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("management_result")) }
            when (state) {
                SettingsHomeUiState.Loading -> item { Text("正在读取学期…") }
                SettingsHomeUiState.Error -> item { Text("无法读取学期，请返回后重试。", color = MaterialTheme.colorScheme.error) }
                is SettingsHomeUiState.Ready -> {
                    if (state.semesters.isEmpty()) item { Text("请新建学期并配置第1周周一、总周数和节次。") }
                    items(state.semesters, key = { it.id }) { semester ->
                        val active = semester.id == state.activeId
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(semester.displayName + if (active) " · 当前学期" else "",
                                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(semester.config?.let { "第1周 ${it.firstWeekMonday} · ${it.totalWeeks}周 · ${it.totalSections}节" }
                                    ?: "尚未配置周次与节次", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onEdit(semester.id) }, enabled = !busy,
                                        modifier = Modifier.testTag("settings_edit_${semester.id}")) { Text("编辑配置") }
                                    if (!active) TextButton(onClick = { onActivate(semester.id) }, enabled = !busy,
                                        modifier = Modifier.testTag("settings_activate_${semester.id}")) { Text("设为当前") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onExport(semester.id) }, enabled = !busy,
                                        modifier = Modifier.weight(1f).testTag("management_export_${semester.id}")) { Text("导出 JSON") }
                                    OutlinedButton(onClick = { clearing = semester }, enabled = !busy,
                                        modifier = Modifier.weight(1f).testTag("management_clear_${semester.id}")) { Text("清除课表") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
