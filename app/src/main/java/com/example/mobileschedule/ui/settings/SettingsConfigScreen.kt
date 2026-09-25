package com.example.mobileschedule.ui.settings

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun SettingsConfigRoute(
    semesterId: Long?,
    onBack: () -> Unit,
    viewModel: SettingsEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(semesterId) { viewModel.open(semesterId) }
    LaunchedEffect(state) { if (state == ConfigEditorState.Saved) onBack() }
    SettingsConfigScreen(state, onInputChange = { next -> viewModel.updateInput { next } },
        onSave = viewModel::save,
        onConfirmImpact = viewModel::confirmImpactAndSave,
        onDismissImpact = viewModel::dismissImpact,
        onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsConfigScreen(
    state: ConfigEditorState,
    onInputChange: (SemesterFormInput) -> Unit,
    onSave: () -> Unit,
    onConfirmImpact: () -> Unit,
    onDismissImpact: () -> Unit,
    onBack: () -> Unit,
) {
    var showDiscard by remember { mutableStateOf(false) }
    val editing = state as? ConfigEditorState.Editing
    fun requestBack() {
        if (editing?.saving == true) return
        if (editing?.dirty == true) showDiscard = true else onBack()
    }
    BackHandler(enabled = state != ConfigEditorState.Saved) { requestBack() }
    val focus = LocalFocusManager.current
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding().testTag("config_page"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(title = { Text(if (editing != null && editing.semesterId == null) "新建学期" else "学期与节次设置") },
                windowInsets = WindowInsets(0),
                navigationIcon = { TextButton(onClick = { requestBack() },
                    enabled = editing?.saving != true && state != ConfigEditorState.Saved,
                    modifier = Modifier.testTag("config_back")) { Text("返回") } })
        },
        bottomBar = {
            if (editing != null) Button(
                onClick = { focus.clearFocus(); onSave() }, enabled = !editing.saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("save_config"),
            ) { Text(if (editing.saving) "正在保存…" else "保存配置") }
        },
    ) { padding ->
        when (state) {
            ConfigEditorState.Loading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            is ConfigEditorState.LoadError -> Text(state.message, Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.error)
            ConfigEditorState.Saved -> Text("配置已保存", Modifier.padding(padding).padding(24.dp))
            is ConfigEditorState.Editing -> ConfigFields(state, onInputChange,
                modifier = Modifier.padding(padding))
        }
    }

    if (editing?.impactPending == true) AlertDialog(
        onDismissRequest = onDismissImpact,
        modifier = Modifier.testTag("impact_dialog"),
        title = { Text("确认配置影响") },
        text = { Text("若已有课程，首周日期或节次时间变更会立即改变课表日期和详情时间；缩短周数或节数若使课程越界，保存会被阻止，原配置与课程保留。",
            modifier = Modifier.testTag("impact_message")) },
        confirmButton = { TextButton(onClick = onConfirmImpact,
            modifier = Modifier.testTag("impact_confirm")) { Text("确认保存") } },
        dismissButton = { TextButton(onClick = onDismissImpact) { Text("继续修改") } },
    )
    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false },
        modifier = Modifier.testTag("discard_dialog"),
        title = { Text("放弃未保存的修改？") },
        text = { Text("返回后本次输入不会保存，原配置与课程保持不变。") },
        confirmButton = { TextButton(onClick = { showDiscard = false; onBack() },
            modifier = Modifier.testTag("discard_confirm")) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { showDiscard = false },
            modifier = Modifier.testTag("discard_keep")) { Text("继续编辑") } },
    )
}

@Composable
private fun ConfigFields(
    state: ConfigEditorState.Editing,
    onInputChange: (SemesterFormInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = state.input
    val errors = if (state.showErrors) input.validate().errors + state.serverErrors else emptyMap()
    val context = LocalContext.current
    val sections = input.totalSections.toIntOrNull()?.takeIf { it in 1..99 } ?: 0
    LazyColumn(modifier.fillMaxSize().testTag("config_fields"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                OutlinedTextField(value = input.displayName,
                    onValueChange = { onInputChange(input.copy(displayName = it)) },
                    label = { Text("学期显示名称") }, singleLine = true, enabled = !state.saving,
                    isError = errors.containsKey("name"),
                    modifier = Modifier.fillMaxWidth().testTag("config_name"))
                ConfigError(errors["name"], "config_error_name")
            }
        }
        item {
            Column {
                TextButton(enabled = !state.saving, onClick = {
                    val initial = input.firstWeekDate ?: LocalDate.now()
                    DatePickerDialog(context, { _, year, month, day ->
                        onInputChange(input.copy(firstWeekDate = LocalDate.of(year, month + 1, day)))
                    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                }, modifier = Modifier.testTag("config_monday")) {
                    Text("第1周周一：${input.firstWeekDate ?: "请选择日期"}")
                }
                input.suggestedMonday?.let { monday ->
                    TextButton(onClick = { onInputChange(input.confirmSuggestedMonday()) },
                        enabled = !state.saving,
                        modifier = Modifier.testTag("use_suggested_monday")) {
                        Text("所选日期不是周一；确认使用该周周一 $monday")
                    }
                }
                ConfigError(errors["monday"], "config_error_monday")
            }
        }
        item {
            Column {
                OutlinedTextField(value = input.totalWeeks,
                    onValueChange = { onInputChange(input.copy(totalWeeks = it)) },
                    label = { Text("总周数") }, singleLine = true, enabled = !state.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errors.containsKey("weeks"),
                    modifier = Modifier.fillMaxWidth().testTag("config_weeks"))
                ConfigError(errors["weeks"], "config_error_weeks")
            }
        }
        item {
            Column {
                OutlinedTextField(value = input.totalSections,
                    onValueChange = { onInputChange(input.copy(totalSections = it)) },
                    label = { Text("节次数量") }, singleLine = true, enabled = !state.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errors.containsKey("sections"),
                    modifier = Modifier.fillMaxWidth().testTag("config_sections"))
                ConfigError(errors["sections"], "config_error_sections")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("填写每节起止时间", style = MaterialTheme.typography.titleMedium)
                    Text("未核实学校作息时可先留空；启用后须填写每一节。")
                }
                Switch(checked = input.timesEnabled, enabled = !state.saving,
                    onCheckedChange = { onInputChange(input.copy(timesEnabled = it)) },
                    modifier = Modifier.testTag("times_toggle"))
            }
        }
        if (input.timesEnabled) {
            items((1..sections).toList(), key = { it }) { section ->
                val row = input.sectionTimes[section] ?: SectionTimeText()
                Column {
                    Text("第${section}节", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(row.start, onValueChange = { next ->
                            onInputChange(input.copy(sectionTimes = input.sectionTimes +
                                (section to row.copy(start = next))))
                        }, label = { Text("开始 HH:mm") }, singleLine = true, enabled = !state.saving,
                            isError = errors.containsKey("time_$section"),
                            modifier = Modifier.weight(1f).testTag("time_start_$section"))
                        OutlinedTextField(row.end, onValueChange = { next ->
                            onInputChange(input.copy(sectionTimes = input.sectionTimes +
                                (section to row.copy(end = next))))
                        }, label = { Text("结束 HH:mm") }, singleLine = true, enabled = !state.saving,
                            isError = errors.containsKey("time_$section"),
                            modifier = Modifier.weight(1f).testTag("time_end_$section"))
                    }
                    ConfigError(errors["time_$section"], "config_error_time_$section")
                }
            }
        }
        if (errors["times"] != null) item { ConfigError(errors["times"], "config_error_times") }
        if (state.saveError != null) item { Text(state.saveError,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("config_save_error")) }
        item { Text("修改首周日期或节次时间会改变已有课程的显示日期与时间；缩短周数或节数如使课程越界，系统会拒绝保存并保留原配置。",
            style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ConfigError(message: String?, tag: String) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(tag))
}
