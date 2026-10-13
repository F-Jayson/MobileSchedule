package com.example.mobileschedule.ui.schedule

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ScheduleDisplayOptions(val showLocation: Boolean = true, val showTeacher: Boolean = true)

/** Presentation-only choices; imported courses and semester configuration remain in Room. */
class ScheduleDisplaySettingsStore(context: Context, preferencesName: String = "schedule_display_settings") {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun read() = ScheduleDisplayOptions(
        showLocation = preferences.getBoolean("show_location", true),
        showTeacher = preferences.getBoolean("show_teacher", true),
    )

    fun save(options: ScheduleDisplayOptions): Boolean = preferences.edit()
        .putBoolean("show_location", options.showLocation)
        .putBoolean("show_teacher", options.showTeacher)
        .commit()
}

@Composable
fun ScheduleDisplaySettingsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ScheduleDisplaySettingsStore(context) }
    var options by remember(store) { mutableStateOf(store.read()) }
    var saveError by remember { mutableStateOf(false) }
    ScheduleDisplaySettingsScreen(
        options = options,
        onOptionsChange = { next ->
            if (store.save(next)) {
                options = next
                saveError = false
            } else saveError = true
        },
        onBack = onBack,
        saveError = saveError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDisplaySettingsScreen(
    options: ScheduleDisplayOptions,
    onOptionsChange: (ScheduleDisplayOptions) -> Unit,
    onBack: () -> Unit,
    saveError: Boolean = false,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("display_settings_page"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("课表显示设置", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("display_settings_back"),
                    ) {
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "课程名称始终显示。这里仅调整周课表卡片；点开课程仍可查看完整教师和地点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOptionsChange(options.copy(showLocation = !options.showLocation)) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示教室 / 地点", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text("在网格中的课程卡片显示教室或教学楼", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = options.showLocation,
                            onCheckedChange = { onOptionsChange(options.copy(showLocation = it)) },
                            modifier = Modifier.testTag("display_location_switch"),
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOptionsChange(options.copy(showTeacher = !options.showTeacher)) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示教师", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text("在网格中的课程卡片显示主讲教师姓名", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = options.showTeacher,
                            onCheckedChange = { onOptionsChange(options.copy(showTeacher = it)) },
                            modifier = Modifier.testTag("display_teacher_switch"),
                        )
                    }
                }
            }

            if (saveError) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "显示设置保存失败，请重试。",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp).testTag("display_settings_error"),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "未单独配置节次时间时，课表显示第1至8节预设时间；学期中已配置的时间优先。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
