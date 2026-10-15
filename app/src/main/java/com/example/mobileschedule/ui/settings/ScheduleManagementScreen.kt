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
import androidx.compose.foundation.selection.selectable
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterImportPreview

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
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    var exportSemesterId by rememberSaveable { mutableStateOf<Long?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val semesterId = exportSemesterId
        exportSemesterId = null
        if (uri != null && semesterId != null) viewModel.exportSemester(semesterId, uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadSemesterJsonBackup(uri)
    }
    ScheduleManagementScreen(
        state = state,
        onBack = onBack,
        onCreate = onCreate,
        onEdit = onEdit,
        onActivate = viewModel::activate,
        onImport = onImport,
        onImportJson = { importer.launch(arrayOf("application/json")) },
        onClear = viewModel::clearSemester,
        onExport = { id ->
            exportSemesterId = id
            exporter.launch("课程表-$id.json")
        },
        busy = busy,
        actionError = error,
        actionMessage = message,
        onDelete = viewModel::deleteSemester,
        importPreview = importPreview,
        onConfirmImport = viewModel::confirmImportJson,
        onDismissImport = viewModel::dismissImportDialog,
    )
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
    onImportJson: () -> Unit = {},
    onClear: (Long) -> Unit = {},
    onExport: (Long) -> Unit = {},
    busy: Boolean = false,
    actionError: String? = null,
    actionMessage: String? = null,
    onDelete: (Long) -> Unit = {},
    importPreview: SemesterImportPreview? = null,
    onConfirmImport: (SemesterImportPreview, Boolean) -> Unit = { _, _ -> },
    onDismissImport: () -> Unit = {},
) {
    var clearing by remember { mutableStateOf<Semester?>(null) }
    var deleting by remember { mutableStateOf<Semester?>(null) }
    deleting?.let { semester ->
        val activeHint = if (state is SettingsHomeUiState.Ready && state.activeId == semester.id) {
            "当前学期删除后，将切换到最近新建的其他学期；没有其他学期时显示空课表。"
        } else ""
        AlertDialog(onDismissRequest = { deleting = null },
            modifier = Modifier.testTag("management_delete_dialog"),
            title = { Text("删除学期") },
            text = { Text("将删除“${semester.displayName}”及其全部课程安排、导入记录、学期日历和节次配置。此操作无法撤销。$activeHint",
                modifier = Modifier.testTag("management_delete_message")) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(semester.id) },
                enabled = !busy, modifier = Modifier.testTag("management_confirm_delete"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null },
                modifier = Modifier.testTag("management_cancel_delete")) { Text("取消") } })
    }
    clearing?.let { semester ->
        AlertDialog(onDismissRequest = { clearing = null },
            title = { Text("清除课程安排") },
            text = { Text("将清除“${semester.displayName}”的全部课程安排和导入记录，保留学期日历与节次配置。") },
            confirmButton = { TextButton(onClick = { clearing = null; onClear(semester.id) },
                enabled = !busy, modifier = Modifier.testTag("management_confirm_clear")) { Text("清除") } },
            dismissButton = { TextButton(onClick = { clearing = null }, modifier = Modifier.testTag("management_cancel_clear")) { Text("取消") } })
    }
    importPreview?.let { preview ->
        var overwrite by remember(preview) { mutableStateOf(preview.existingSemesterId != null) }
        AlertDialog(
            onDismissRequest = onDismissImport,
            modifier = Modifier.testTag("backup_import_dialog"),
            title = { Text("恢复课表备份", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("学期名称：${preview.displayName}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "学期日历：第1周 ${preview.firstWeekMonday} · ${preview.totalWeeks}周 · ${preview.totalSections}节",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "课程安排：共 ${preview.arrangementCount} 条课程安排",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    if (preview.existingSemesterId != null) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            "⚠️ 检测到已存在同名学期“${preview.displayName}”",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = overwrite,
                                    onClick = { overwrite = true },
                                )
                                .padding(vertical = 4.dp)
                                .testTag("backup_import_overwrite_option"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = overwrite,
                                onClick = { overwrite = true },
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text("覆盖同名学期", fontWeight = FontWeight.Medium)
                                Text(
                                    "将清空并替换该学期现有的课程与配置，不可撤销",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = !overwrite,
                                    onClick = { overwrite = false },
                                )
                                .padding(vertical = 4.dp)
                                .testTag("backup_import_new_option"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = !overwrite,
                                onClick = { overwrite = false },
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text("作为新学期导入", fontWeight = FontWeight.Medium)
                                Text(
                                    "保留现有同名学期，自动重命名新建学期",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text(
                            "将把此备份作为全新学期导入到本地课表中。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmImport(preview, overwrite) },
                    enabled = !busy,
                    modifier = Modifier.testTag("backup_import_confirm"),
                    colors = if (preview.existingSemesterId != null && overwrite) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(if (preview.existingSemesterId != null && overwrite) "确认覆盖" else "确认导入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissImport,
                    enabled = !busy,
                    modifier = Modifier.testTag("backup_import_dismiss"),
                ) {
                    Text("取消")
                }
            },
        )
    }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(
            title = { Text("课表管理", fontWeight = FontWeight.Bold) },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("schedule_management_page"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f).testTag("settings_import")) {
                            Text("导入课表")
                        }
                        OutlinedButton(onClick = onCreate, enabled = !busy, modifier = Modifier.weight(1f).testTag("settings_create")) {
                            Text("新建学期")
                        }
                    }
                    OutlinedButton(
                        onClick = onImportJson,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("management_import_backup"),
                    ) {
                        Text("导入备份（JSON）")
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
                                TextButton(onClick = { deleting = semester }, enabled = !busy,
                                    modifier = Modifier.align(Alignment.End).testTag("management_delete_${semester.id}"),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除学期") }
                            }
                        }
                    }
                }
            }
        }
    }
}
