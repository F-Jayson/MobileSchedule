package com.example.mobileschedule.ui.importentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.settings.SettingsHomeViewModel

@Composable
fun ImportIntroRoute(
    onBack: () -> Unit,
    onConfigure: (Long?) -> Unit,
    viewModel: SettingsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeId = (state as? SettingsHomeUiState.Ready)?.activeId
    ImportIntroScreen(state, onBack, onConfigure = { onConfigure(activeId) })
}

/** Stage 4 entry only. Stage 5 will add authenticated reading and preview behind this route. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportIntroScreen(
    state: SettingsHomeUiState,
    onBack: () -> Unit,
    onConfigure: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize().testTag("import_intro"),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("导入课程表") }, windowInsets = WindowInsets(0),
            navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("import_back")) {
                Text("返回")
            } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("福建师范大学 · 正方教务", style = MaterialTheme.typography.titleLarge)
            Text("导入时需由你在学校页面完成本人登录、选择来源学期，再预览课程安排并确认保存。")
            Text("学校登录与课表读取尚未接入，当前不能开始在线导入。已保存的本地课程不会受此入口影响。",
                modifier = Modifier.testTag("import_unavailable"),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state) {
                SettingsHomeUiState.Loading -> {
                    CircularProgressIndicator()
                    Text("正在读取本地学期配置…")
                }
                SettingsHomeUiState.Error -> Text("暂时无法读取本地学期，请返回设置页重试。",
                    color = MaterialTheme.colorScheme.error)
                is SettingsHomeUiState.Ready -> {
                    val active = state.semesters.firstOrNull { it.id == state.activeId }
                    Text(when {
                        active == null -> "尚无活动学期。请先配置本地学期。"
                        active.config == null -> "${active.displayName} 尚缺周次或节次配置。"
                        else -> "本地目标学期：${active.displayName}"
                    }, modifier = Modifier.testTag("import_target"))
                    Button(onClick = onConfigure, modifier = Modifier.testTag("import_configure")) {
                        Text(if (active == null) "配置学期" else "检查学期配置")
                    }
                }
            }
        }
    }
}
