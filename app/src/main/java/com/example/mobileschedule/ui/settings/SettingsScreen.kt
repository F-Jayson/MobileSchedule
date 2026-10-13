package com.example.mobileschedule.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.R

@Composable
fun SettingsRoute(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onImport: () -> Unit,
    onDisplaySettings: () -> Unit,
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.actionError.collectAsStateWithLifecycle()
    SettingsHomeScreen(
        state = state,
        onCreate = onCreate,
        onEdit = onEdit,
        onActivate = viewModel::activate,
        actionError = error,
        onImport = onImport,
        onDisplaySettings = onDisplaySettings,
    )
}

@Composable
fun SettingsHomeScreen(
    state: SettingsHomeUiState,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onActivate: (Long) -> Unit,
    actionError: String? = null,
    onImport: () -> Unit = {},
    onDisplaySettings: () -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("settings_page"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "管理本地学期日历、课表显示偏好与教务导入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        when (state) {
            SettingsHomeUiState.Loading -> item {
                Row(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Text(" 正在读取设置…", modifier = Modifier.padding(start = 12.dp))
                }
            }
            SettingsHomeUiState.Error -> item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "无法读取学期设置，请重新打开页面后重试。",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            is SettingsHomeUiState.Ready -> {
                if (state.semesters.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "尚无本地学期",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "尚无本地学期。请先确认第1周周一、总周数和节次；节次时间可稍后填写。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "本地学期",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    items(state.semesters, key = { it.id }) { semester ->
                        val active = state.activeId == semester.id
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            border = if (active) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        semester.displayName + if (active) " · 当前学期" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (active) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                        ) {
                                            Text(
                                                "当前激活",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            )
                                        }
                                    }
                                }

                                val config = semester.config
                                Text(
                                    if (config == null) "尚未配置周次和节次"
                                    else "第1周周一 ${config.firstWeekMonday} · ${config.totalWeeks}周 · ${config.totalSections}节",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Text(
                                    if (config?.sectionTimes.isNullOrEmpty())
                                        "未单独配置时间；课表显示第1–8节默认时间"
                                    else "已配置每节起止时间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { onEdit(semester.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("settings_edit_${semester.id}"),
                                    ) {
                                        Text("编辑配置", fontWeight = FontWeight.SemiBold)
                                    }
                                    if (!active) {
                                        FilledTonalButton(
                                            onClick = { onActivate(semester.id) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("settings_activate_${semester.id}"),
                                        ) {
                                            Text("设为当前学期", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "功能操作与偏好",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onCreate,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("settings_create"),
                            ) {
                                Text("新建本地学期", fontWeight = FontWeight.SemiBold)
                            }

                            FilledTonalButton(
                                onClick = onImport,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("settings_import"),
                            ) {
                                Text("导入课程表", fontWeight = FontWeight.Medium)
                            }

                            OutlinedButton(
                                onClick = onDisplaySettings,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("settings_display_settings"),
                            ) {
                                Text("课表设置", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        if (actionError != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        actionError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}
