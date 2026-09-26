package com.example.mobileschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.R

@Composable
fun SettingsRoute(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onImport: () -> Unit,
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.actionError.collectAsStateWithLifecycle()
    SettingsHomeScreen(state, onCreate, onEdit, viewModel::activate, error, onImport)
}

@Composable
fun SettingsHomeScreen(
    state: SettingsHomeUiState,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onActivate: (Long) -> Unit,
    actionError: String? = null,
    onImport: () -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize().testTag("settings_page"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall) }
        when (state) {
            SettingsHomeUiState.Loading -> item { CircularProgressIndicator() }
            SettingsHomeUiState.Error -> item { Text("无法读取学期设置，请重新打开页面后重试。") }
            is SettingsHomeUiState.Ready -> {
                if (state.semesters.isEmpty()) {
                    item { Text("尚无本地学期。请先确认第1周周一、总周数和节次；节次时间可稍后填写。") }
                } else {
                    item { Text("本地学期", style = MaterialTheme.typography.titleMedium) }
                    items(state.semesters, key = { it.id }) { semester ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val active = state.activeId == semester.id
                                Text(semester.displayName + if (active) " · 当前学期" else "",
                                    style = MaterialTheme.typography.titleMedium)
                                val config = semester.config
                                Text(if (config == null) "尚未配置周次和节次" else
                                    "第1周周一 ${config.firstWeekMonday} · ${config.totalWeeks}周 · ${config.totalSections}节")
                                Text(if (config?.sectionTimes.isNullOrEmpty())
                                    "未单独配置时间；课表显示第1–8节默认时间" else "已配置每节起止时间")
                                Column {
                                    TextButton(onClick = { onEdit(semester.id) },
                                        modifier = Modifier.testTag("settings_edit_${semester.id}")) { Text("编辑配置") }
                                    if (!active) TextButton(onClick = { onActivate(semester.id) },
                                        modifier = Modifier.testTag("settings_activate_${semester.id}")) {
                                        Text("设为当前学期")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreate, modifier = Modifier.testTag("settings_create")) {
                            Text("新建本地学期")
                        }
                        TextButton(onClick = onImport, modifier = Modifier.testTag("settings_import")) {
                            Text("导入课程表")
                        }
                    }
                }
            }
        }
        if (actionError != null) item { Text(actionError, color = MaterialTheme.colorScheme.error) }
    }
}
