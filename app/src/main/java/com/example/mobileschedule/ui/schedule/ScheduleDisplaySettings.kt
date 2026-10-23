package com.example.mobileschedule.ui.schedule

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import com.example.mobileschedule.ui.schedule.wallpaper.ScheduleWallpaperManager
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class ScheduleDisplayOptions(
    val showLocation: Boolean = true,
    val showTeacher: Boolean = true,
    val showWeekend: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val showFullCourseName: Boolean = false,
    val showNonCurrentWeekCourses: Boolean = false,
    val wallpaperPath: String? = null,
    val cardOpacity: Float = 0.85f,
    val isWallpaperDark: Boolean = false,
    val wallpaperUpdatedAt: Long = 0L,
)

/** Presentation-only choices; imported courses and semester configuration remain in Room. */
class ScheduleDisplaySettingsStore(context: Context, preferencesName: String = "schedule_display_settings") {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val appPreferences = AppPreferencesStore(context)

    companion object {
        private val _optionsFlow = MutableStateFlow<ScheduleDisplayOptions?>(null)
        val optionsFlow: StateFlow<ScheduleDisplayOptions?> = _optionsFlow.asStateFlow()

        fun notifyOptionsChanged(options: ScheduleDisplayOptions) {
            _optionsFlow.value = options
        }
    }

    fun read(): ScheduleDisplayOptions {
        val showWeekend = if (preferences.contains("show_weekend")) {
            preferences.getBoolean("show_weekend", true)
        } else {
            appPreferences.isShowWeekend()
        }
        val firstDay = if (preferences.contains("first_day_of_week")) {
            val name = preferences.getString("first_day_of_week", DayOfWeek.MONDAY.name) ?: DayOfWeek.MONDAY.name
            runCatching { DayOfWeek.valueOf(name) }.getOrDefault(DayOfWeek.MONDAY)
        } else {
            appPreferences.getFirstDayOfWeek()
        }
        val showNonCurrentWeek = if (preferences.contains("show_non_current_week_courses")) {
            preferences.getBoolean("show_non_current_week_courses", false)
        } else {
            appPreferences.isShowNonCurrentWeekCourses()
        }
        val rawWallpaperPath = preferences.getString("schedule_wallpaper_path", null)
        val validWallpaperPath = rawWallpaperPath?.takeIf {
            val file = java.io.File(it)
            file.exists() && file.length() > 0L
        }
        val cardOpacity = preferences.getFloat("schedule_card_opacity", 0.85f).coerceIn(0.30f, 1.00f)
        val isWallpaperDark = preferences.getBoolean("schedule_wallpaper_dark", false)
        val wallpaperUpdatedAt = if (validWallpaperPath != null) {
            preferences.getLong("schedule_wallpaper_updated_at", 0L)
        } else {
            0L
        }

        return ScheduleDisplayOptions(
            showLocation = preferences.getBoolean("show_location", true),
            showTeacher = preferences.getBoolean("show_teacher", true),
            showWeekend = showWeekend,
            firstDayOfWeek = firstDay,
            showFullCourseName = preferences.getBoolean("show_full_course_name", false),
            showNonCurrentWeekCourses = showNonCurrentWeek,
            wallpaperPath = validWallpaperPath,
            cardOpacity = cardOpacity,
            isWallpaperDark = isWallpaperDark,
            wallpaperUpdatedAt = wallpaperUpdatedAt,
        )
    }

    fun save(options: ScheduleDisplayOptions): Boolean {
        val editor = preferences.edit()
            .putBoolean("show_location", options.showLocation)
            .putBoolean("show_teacher", options.showTeacher)
            .putBoolean("show_weekend", options.showWeekend)
            .putString("first_day_of_week", options.firstDayOfWeek.name)
            .putBoolean("show_full_course_name", options.showFullCourseName)
            .putBoolean("show_non_current_week_courses", options.showNonCurrentWeekCourses)
            .putFloat("schedule_card_opacity", options.cardOpacity.coerceIn(0.30f, 1.00f))
            .putBoolean("schedule_wallpaper_dark", options.isWallpaperDark)

        if (options.wallpaperPath != null) {
            editor.putString("schedule_wallpaper_path", options.wallpaperPath)
            editor.putLong("schedule_wallpaper_updated_at", options.wallpaperUpdatedAt)
        } else {
            editor.remove("schedule_wallpaper_path")
            editor.remove("schedule_wallpaper_updated_at")
        }

        val ok1 = editor.commit()
        val ok2 = appPreferences.saveShowWeekend(options.showWeekend)
        val ok3 = appPreferences.saveFirstDayOfWeek(options.firstDayOfWeek)
        val ok4 = appPreferences.saveShowNonCurrentWeekCourses(options.showNonCurrentWeekCourses)
        val success = ok1 && ok2 && ok3 && ok4
        if (success) {
            notifyOptionsChanged(options)
        }
        return success
    }
}

@Composable
fun ScheduleDisplaySettingsRoute(
    onBack: () -> Unit,
    onExportImage: () -> Unit = {},
) {
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
        onExportImage = onExportImage,
        saveError = saveError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDisplaySettingsScreen(
    options: ScheduleDisplayOptions,
    onOptionsChange: (ScheduleDisplayOptions) -> Unit,
    onBack: () -> Unit,
    onExportImage: () -> Unit = {},
    saveError: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showFirstDayDialog by remember { mutableStateOf(false) }
    var isProcessingWallpaper by remember { mutableStateOf(false) }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingWallpaper = true
            coroutineScope.launch(Dispatchers.IO) {
                val metadata = ScheduleWallpaperManager.saveWallpaperFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    isProcessingWallpaper = false
                    if (metadata != null) {
                        val nextOpacity = if (options.wallpaperPath == null && options.cardOpacity >= 0.85f) {
                            0.65f
                        } else {
                            options.cardOpacity
                        }
                        onOptionsChange(
                            options.copy(
                                wallpaperPath = metadata.filePath,
                                isWallpaperDark = metadata.isDark,
                                cardOpacity = nextOpacity,
                                wallpaperUpdatedAt = metadata.updatedAt,
                            )
                        )
                        android.widget.Toast.makeText(context, "课表背景壁纸已设置", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "壁纸设置失败，请选择其他图片重试", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("display_settings_page"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("课表显示设置", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "课程名称始终显示。这里仅调整周课表卡片与网格排版；点开课程仍可查看完整教师和地点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            // 卡片内容显示控制
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

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOptionsChange(options.copy(showFullCourseName = !options.showFullCourseName)) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示课程全名", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text("关闭时过长名称截断并显示为省略号，开启时完整显示课程名称", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = options.showFullCourseName,
                            onCheckedChange = { onOptionsChange(options.copy(showFullCourseName = it)) },
                            modifier = Modifier.testTag("display_full_course_name_switch"),
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOptionsChange(options.copy(showNonCurrentWeekCourses = !options.showNonCurrentWeekCourses)) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示非本周课程", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text("以半透明及虚线细边框显示本周不上课但在其他周有课的课程", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = options.showNonCurrentWeekCourses,
                            onCheckedChange = { onOptionsChange(options.copy(showNonCurrentWeekCourses = it)) },
                            modifier = Modifier.testTag("display_non_current_week_switch"),
                        )
                    }
                }
            }

            // 网格高级排版控制（显示周末 & 每周起始日）
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
                            .clickable { onOptionsChange(options.copy(showWeekend = !options.showWeekend)) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示周末", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (options.showWeekend) "展示完整七天；工作日与周末均匀排列" else "仅展示周一至周五工作日五天（周末有课将提示）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Switch(
                            checked = options.showWeekend,
                            onCheckedChange = { onOptionsChange(options.copy(showWeekend = it)) },
                            modifier = Modifier.testTag("display_weekend_switch"),
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showFirstDayDialog = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("每周起始日", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (options.firstDayOfWeek == DayOfWeek.SUNDAY) "周日为第一列（周日~周六）" else "周一为第一列（周一~周日）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text(
                            if (options.firstDayOfWeek == DayOfWeek.SUNDAY) "周日" else "周一",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("display_first_day_entry"),
                        )
                    }
                }
            }

            // 自定义课表背景壁纸与卡片透明度视觉定制
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("wallpaper_settings_section"),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("课表背景壁纸", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (options.wallpaperPath != null) {
                                    if (options.isWallpaperDark) "已启用自定义壁纸 · 识别为暗色基调" else "已启用自定义壁纸 · 识别为亮色基调"
                                } else "未设置壁纸（系统默认纯色背景）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!isProcessingWallpaper) {
                                    pickMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            enabled = !isProcessingWallpaper,
                            modifier = Modifier.weight(1f).testTag("display_pick_wallpaper_button"),
                        ) {
                            Text(
                                if (isProcessingWallpaper) "处理中…"
                                else if (options.wallpaperPath == null) "选择背景壁纸"
                                else "更换壁纸"
                            )
                        }
                        if (options.wallpaperPath != null) {
                            OutlinedButton(
                                onClick = {
                                    ScheduleWallpaperManager.clearWallpaper(context)
                                    onOptionsChange(
                                        options.copy(
                                            wallpaperPath = null,
                                            isWallpaperDark = false,
                                            wallpaperUpdatedAt = 0L,
                                        )
                                    )
                                    android.widget.Toast.makeText(context, "已恢复默认无壁纸", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                enabled = !isProcessingWallpaper,
                                modifier = Modifier.weight(1f).testTag("display_clear_wallpaper_button"),
                            ) {
                                Text("恢复默认无壁纸")
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // 卡片不透明度调节 (30% ~ 100%)
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "课程卡片不透明度",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${(options.cardOpacity * 100).roundToInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("display_card_opacity_text"),
                            )
                        }
                        Slider(
                            value = options.cardOpacity,
                            onValueChange = { next ->
                                val stepped = (next * 100).roundToInt() / 100f
                                onOptionsChange(options.copy(cardOpacity = stepped))
                            },
                            valueRange = 0.30f..1.00f,
                            steps = 13,
                            modifier = Modifier.fillMaxWidth().testTag("display_card_opacity_slider"),
                        )
                        Text(
                            "调低不透明度可透出壁纸细节，卡片自动呈现 Material 3 磨砂表面与 WCAG 高对比度文字。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            // 导出长图与壁纸分享入口
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExportImage() }
                    .testTag("display_export_image_entry"),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("导出本周课表长图", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "离线渲染超清 2x/3x 课表长图，支持暗黑壁纸与纯白打印风格",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Text(
                        "导出 >",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
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

    if (showFirstDayDialog) {
        AlertDialog(
            onDismissRequest = { showFirstDayDialog = false },
            title = { Text("选择每周起始日", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOptionsChange(options.copy(firstDayOfWeek = DayOfWeek.MONDAY))
                                showFirstDayDialog = false
                            }
                            .padding(vertical = 8.dp)
                            .testTag("first_day_radio_monday"),
                    ) {
                        RadioButton(
                            selected = options.firstDayOfWeek == DayOfWeek.MONDAY,
                            onClick = {
                                onOptionsChange(options.copy(firstDayOfWeek = DayOfWeek.MONDAY))
                                showFirstDayDialog = false
                            },
                        )
                        Text("周一为第一天（默认）", modifier = Modifier.padding(start = 8.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOptionsChange(options.copy(firstDayOfWeek = DayOfWeek.SUNDAY))
                                showFirstDayDialog = false
                            }
                            .padding(vertical = 8.dp)
                            .testTag("first_day_radio_sunday"),
                    ) {
                        RadioButton(
                            selected = options.firstDayOfWeek == DayOfWeek.SUNDAY,
                            onClick = {
                                onOptionsChange(options.copy(firstDayOfWeek = DayOfWeek.SUNDAY))
                                showFirstDayDialog = false
                            },
                        )
                        Text("周日为第一天", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFirstDayDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
