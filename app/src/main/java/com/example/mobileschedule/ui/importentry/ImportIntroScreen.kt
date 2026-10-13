package com.example.mobileschedule.ui.importentry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.settings.SettingsHomeViewModel

@Composable
fun ImportIntroRoute(
    onBack: () -> Unit,
    onConfigure: (Long?) -> Unit,
    onContinue: (Long) -> Unit,
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeId = (state as? SettingsHomeUiState.Ready)?.activeId
    val context = LocalContext.current
    val quickImport = remember { AppPreferencesStore(context).isQuickImportEnabled() }
    ImportIntroScreen(
        state = state,
        onBack = onBack,
        onConfigure = { onConfigure(activeId) },
        onContinue = { activeId?.let(onContinue) },
        quickImport = quickImport,
    )
}

/** Entry to the school timetable workflow with the user's selected import preference. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportIntroScreen(
    state: SettingsHomeUiState,
    onBack: () -> Unit,
    onConfigure: () -> Unit,
    onContinue: () -> Unit,
    quickImport: Boolean = false,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("import_intro"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("导入课程表", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("import_back")) {
                        Text("返回", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp),
                        ) {}
                        Text(
                            "教务对接支持",
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        "福建师范大学 · 正方教务",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (quickImport) "请在学校页面完成本人登录，学年和学期已自动选择；点击解析课表后自动导入并返回课表。"
                        else "导入时需由你在学校页面完成本人登录、选择来源学期，再预览课程安排并确认保存。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Steps guide card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("导入流程规范", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (quickImport) "快速导入会保存本次解析通过的课程，替换同来源学期的已有课程；解析或保存失败时可查看原因并重试。"
                        else "登录并读取后会展示课程安排预览和校验原因。确认完整学期、核对替换范围后才可保存；取消或保存失败不会改变已有本地课程。",
                        modifier = Modifier.testTag("import_read_only"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            when (state) {
                SettingsHomeUiState.Loading -> {
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                        Text("正在读取本地学期配置…", modifier = Modifier.padding(start = 12.dp))
                    }
                }
                SettingsHomeUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "暂时无法读取本地学期，请返回设置页重试。",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                is SettingsHomeUiState.Ready -> {
                    val active = state.semesters.firstOrNull { it.id == state.activeId }
                    val canContinue = active?.config != null

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (canContinue) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (canContinue) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "本地学期状态",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val targetText = when {
                                active == null -> "尚无活动学期。请先配置本地学期。"
                                active.config == null -> "${active.displayName} 尚缺周次或节次配置。"
                                else -> "本地目标学期：${active.displayName}"
                            }
                            Text(
                                targetText,
                                modifier = Modifier.testTag("import_target"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (canContinue) FontWeight.Medium else FontWeight.Normal,
                                color = if (canContinue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(
                            onClick = onContinue,
                            enabled = canContinue,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("import_continue"),
                        ) {
                            Text("开始学校登录与读取", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onConfigure,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("import_configure"),
                        ) {
                            Text(if (active == null) "配置学期" else "检查学期配置", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
