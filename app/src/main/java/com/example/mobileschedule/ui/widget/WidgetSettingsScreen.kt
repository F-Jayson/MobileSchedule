package com.example.mobileschedule.ui.widget

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.R
import com.example.mobileschedule.data.preferences.WidgetClickAction
import com.example.mobileschedule.data.preferences.WidgetColorPreset
import com.example.mobileschedule.data.preferences.WidgetCornerRadius
import com.example.mobileschedule.data.preferences.WidgetCustomizationOptions
import com.example.mobileschedule.data.preferences.WidgetSettingsStore
import com.example.mobileschedule.data.preferences.WidgetTextColorMode
import kotlinx.coroutines.launch

@Composable
fun WidgetSettingsRoute(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { WidgetSettingsStore(context) }
    var options by remember(store) { mutableStateOf(store.read()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    WidgetSettingsScreen(
        options = options,
        onOptionsChange = { options = it },
        onSave = {
            store.save(options)
            scope.launch {
                ScheduleWidgetUpdater.updateAllWidgets(context)
                snackbarHostState.showSnackbar("小组件配置已保存并同步至桌面！")
            }
        },
        onReset = {
            val def = WidgetCustomizationOptions()
            options = def
            store.save(def)
            scope.launch {
                ScheduleWidgetUpdater.updateAllWidgets(context)
                snackbarHostState.showSnackbar("已恢复为默认样式")
            }
        },
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    options: WidgetCustomizationOptions,
    onOptionsChange: (WidgetCustomizationOptions) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val isSystemDark = isSystemInDarkTheme()
    val resolvedColors = remember(options, isSystemDark) {
        WidgetThemeHelper.resolveColors(options, isSystemDark)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("widget_settings_screen"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("桌面小组件定制", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("widget_settings_back"),
                    ) {
                        Text("返回", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("widget_btn_reset"),
                    ) {
                        Text("恢复默认")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("widget_settings_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. 实时预览区
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("实时预览效果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "预览底衬采用渐变壁纸模拟桌面环境，可直观查看透明度与文字对比度效果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    WidgetLivePreviewCard(options = options, resolved = resolvedColors)
                }
            }

            // 2. 背景与颜色定制
            item {
                WidgetSettingSection(title = "背景颜色与透明度") {
                    // 跟随系统主题开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionsChange(options.copy(isFollowTheme = !options.isFollowTheme)) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("跟随系统深浅外观", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "浅色模式采用明亮白色，深色模式采用沉浸黑色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Switch(
                            checked = options.isFollowTheme,
                            onCheckedChange = { onOptionsChange(options.copy(isFollowTheme = it)) },
                            modifier = Modifier.testTag("widget_toggle_follow_theme"),
                        )
                    }

                    if (!options.isFollowTheme) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        Text("预设颜色", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        PresetColorsGrid(
                            selectedHex = options.customColorHex,
                            onSelectHex = { hex -> onOptionsChange(options.copy(customColorHex = hex)) },
                        )

                        Spacer(Modifier.height(12.dp))
                        HexColorInputField(
                            currentHex = options.customColorHex,
                            onHexChanged = { hex -> onOptionsChange(options.copy(customColorHex = hex)) },
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )

                    // 透明度调节
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("背景不透明度", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${(options.opacity * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = options.opacity,
                        onValueChange = { onOptionsChange(options.copy(opacity = it)) },
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier.testTag("widget_opacity_slider"),
                    )
                    // 快捷透明度按钮组
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0.0f to "全透 (0%)", 0.35f to "35%", 0.65f to "65%", 1.0f to "纯色 (100%)").forEach { (v, label) ->
                            OutlinedButton(
                                onClick = { onOptionsChange(options.copy(opacity = v)) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 3. 样式与排版
            item {
                WidgetSettingSection(title = "外观与排版样式") {
                    Text("小组件圆角弧度", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    CornerRadiusSelector(
                        selected = options.cornerRadius,
                        onSelect = { onOptionsChange(options.copy(cornerRadius = it)) },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )

                    Text("文字对比度模式", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "“自动适配”将根据背景亮度自动切换深浅文字；高透明时优先白色保证壁纸可读性。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextColorModeSelector(
                        selected = options.textColorMode,
                        onSelect = { onOptionsChange(options.copy(textColorMode = it)) },
                    )
                }
            }

            // 4. 展示内容开关
            item {
                WidgetSettingSection(title = "小组件卡片内容") {
                    // 显示地点
                    ContentToggleRow(
                        title = "显示教室 / 地点",
                        subtitle = "在课程卡片副标题中显示上课教学楼与教室",
                        checked = options.showLocation,
                        onCheckedChange = { onOptionsChange(options.copy(showLocation = it)) },
                        testTag = "widget_toggle_location",
                    )
                    // 显示教师
                    ContentToggleRow(
                        title = "显示任课教师",
                        subtitle = "在课程卡片副标题中显示主讲教师姓名",
                        checked = options.showTeacher,
                        onCheckedChange = { onOptionsChange(options.copy(showTeacher = it)) },
                        testTag = "widget_toggle_teacher",
                    )
                    // 显示农历
                    ContentToggleRow(
                        title = "顶部显示农历日期",
                        subtitle = "在小组件日期标题后附带农历月份与初日（如 九月初六）",
                        checked = options.showLunar,
                        onCheckedChange = { onOptionsChange(options.copy(showLunar = it)) },
                        testTag = "widget_toggle_lunar",
                    )
                    // 明日预告
                    ContentToggleRow(
                        title = "展示明日课程预告",
                        subtitle = "今日课程全部结束后，自动展示明日课表与提醒",
                        checked = options.showTomorrowPreview,
                        onCheckedChange = { onOptionsChange(options.copy(showTomorrowPreview = it)) },
                        testTag = "widget_toggle_tomorrow",
                    )

                    // 显示当日全部课程
                    ContentToggleRow(
                        title = "显示当日全部课程",
                        subtitle = "展示当天所有课程并在小组件内支持上下滑动浏览；关闭时仅展示未结束课程",
                        checked = options.showAllCourses,
                        onCheckedChange = { onOptionsChange(options.copy(showAllCourses = it)) },
                        testTag = "widget_toggle_show_all_courses",
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )

                    if (options.showAllCourses) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_schedule),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "已开启当日全部课程滚动模式：小组件内可直接上下滑动浏览当日全部课程；课程未占满时默认靠顶端对齐。",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    } else {
                        // 课程行数选择
                        Text("最大展示课程行数", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "4x2 尺寸小组件推荐展示 2 节；开启 3 节可容纳更多课程，卡片排版将更为紧凑。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            listOf(2 to "展示 2 节课程 (默认)", 3 to "展示 3 节课程 (紧凑)").forEach { (rows, label) ->
                                val isSelected = options.maxCourseRows == rows
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOptionsChange(options.copy(maxCourseRows = rows)) }
                                        .testTag(if (rows == 2) "widget_rows_2" else "widget_rows_3"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onOptionsChange(options.copy(maxCourseRows = rows)) },
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. 交互行为
            item {
                WidgetSettingSection(title = "小组件点击跳转行为") {
                    Text("点击小组件空白区域或头部时打开：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    WidgetClickAction.entries.forEach { action ->
                        val isSelected = options.clickAction == action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOptionsChange(options.copy(clickAction = action)) }
                                .padding(vertical = 8.dp)
                                .testTag(if (action == WidgetClickAction.OPEN_SCHEDULE) "widget_action_schedule" else "widget_action_today"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onOptionsChange(options.copy(clickAction = action)) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(action.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (action == WidgetClickAction.OPEN_SCHEDULE) "进入完整学期周视图课表网格" else "直接进入今日日程与课程提醒视图",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }

            // 6. 保存操作按钮
            item {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("widget_btn_save"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("保存并应用到桌面小组件", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun WidgetSettingSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ContentToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun WidgetLivePreviewCard(
    options: WidgetCustomizationOptions,
    resolved: WidgetResolvedColors,
) {
    // 壁纸预览底衬
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF3B82F6),
                        Color(0xFF93C5FD),
                    ),
                ),
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 小组件本体模拟
        val cornerRadiusDp = options.cornerRadius.radiusDp.dp
        val bgColor = Color(resolved.effectiveBgColor).copy(alpha = options.opacity.coerceIn(0f, 1f))

        Surface(
            shape = RoundedCornerShape(cornerRadiusDp),
            color = bgColor,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("widget_preview_card"),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                // Header (Single horizontal bar matching real desktop widget)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_schedule),
                        contentDescription = null,
                        tint = Color(resolved.textAccent),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    val headerDate = if (options.showLunar) "10月15日 · 九月初六" else "10月15日 周四"
                    Text(
                        text = headerDate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(resolved.textPrimary),
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(resolved.pillBgColor),
                    ) {
                        Text(
                            text = "第 7 周",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(resolved.textAccent),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (options.showAllCourses) "当日全部课程" else "今日剩余课程",
                        fontSize = 11.sp,
                        color = Color(resolved.textSecondary),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_launch),
                        contentDescription = null,
                        tint = Color(resolved.textSecondary),
                        modifier = Modifier.size(12.dp),
                    )
                }

                HorizontalDivider(
                    color = Color(resolved.dividerColor),
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )

                // Course rows: aligned to top, without separate pill card wrapping
                if (options.showAllCourses) {
                    // All courses mode: showcase full day schedule (with status)
                    PreviewCourseRow(
                        title = "高等数学 (上)",
                        time = "08:00 - 09:35",
                        location = if (options.showLocation) "旗山1号楼102" else "",
                        teacher = if (options.showTeacher) "张教授" else "",
                        badgeText = "已下课",
                        isOngoing = false,
                        isFinished = true,
                        resolved = resolved,
                    )
                    PreviewCourseRow(
                        title = "大学物理 (A)",
                        time = "10:00 - 11:35",
                        location = if (options.showLocation) "旗山2号楼204" else "",
                        teacher = if (options.showTeacher) "王老师" else "",
                        badgeText = "● 正在上课",
                        isOngoing = true,
                        isFinished = false,
                        resolved = resolved,
                    )
                    PreviewCourseRow(
                        title = "面向对象程序设计",
                        time = "14:00 - 15:35",
                        location = if (options.showLocation) "实验中心301" else "",
                        teacher = if (options.showTeacher) "李老师" else "",
                        badgeText = "第5-6节",
                        isOngoing = false,
                        isFinished = false,
                        resolved = resolved,
                    )
                    PreviewCourseRow(
                        title = "移动应用交互设计",
                        time = "15:50 - 17:25",
                        location = if (options.showLocation) "理工楼1#105" else "",
                        teacher = if (options.showTeacher) "陈老师" else "",
                        badgeText = "第7-8节",
                        isOngoing = false,
                        isFinished = false,
                        resolved = resolved,
                    )
                } else {
                    // Remaining courses mode: display up to maxCourseRows
                    PreviewCourseRow(
                        title = "大学物理 (A)",
                        time = "10:00 - 11:35",
                        location = if (options.showLocation) "旗山2号楼204" else "",
                        teacher = if (options.showTeacher) "王老师" else "",
                        badgeText = "● 正在上课",
                        isOngoing = true,
                        isFinished = false,
                        resolved = resolved,
                    )
                    PreviewCourseRow(
                        title = "面向对象程序设计",
                        time = "14:00 - 15:35",
                        location = if (options.showLocation) "实验中心301" else "",
                        teacher = if (options.showTeacher) "李老师" else "",
                        badgeText = "第5-6节",
                        isOngoing = false,
                        isFinished = false,
                        resolved = resolved,
                    )
                    if (options.maxCourseRows >= 3) {
                        PreviewCourseRow(
                            title = "移动应用交互设计",
                            time = "15:50 - 17:25",
                            location = if (options.showLocation) "理工楼1#105" else "",
                            teacher = if (options.showTeacher) "陈老师" else "",
                            badgeText = "第7-8节",
                            isOngoing = false,
                            isFinished = false,
                            resolved = resolved,
                        )
                    }
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val hint = if (options.showAllCourses) {
                        "↕ 当日共 4 节课 · 支持小组件内上下滑动浏览"
                    } else {
                        val remainingCount = 4 - options.maxCourseRows
                        "+ 还有 $remainingCount 节课程，点击查看完整课表"
                    }
                    Text(
                        text = hint,
                        fontSize = 10.sp,
                        color = Color(resolved.textAccent),
                    )
                    Text(
                        text = "点击将${options.clickAction.title}",
                        fontSize = 10.sp,
                        color = Color(resolved.textSecondary),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCourseRow(
    title: String,
    time: String,
    location: String,
    teacher: String,
    badgeText: String,
    isOngoing: Boolean,
    isFinished: Boolean = false,
    resolved: WidgetResolvedColors,
) {
    val metaText = WidgetThemeHelper.formatCourseInfo(
        timeText = time,
        location = location,
        teacher = teacher,
        showLocation = location.isNotBlank(),
        showTeacher = teacher.isNotBlank(),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Badge (Never wrapped, clean fixed padding)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = when {
                isOngoing -> if (resolved.isLightContent) Color(0xFF15803D) else Color(0xFFDCFCE7)
                isFinished -> Color(resolved.pillBgColor)
                else -> Color(resolved.badgeNormalBg)
            },
        ) {
            Text(
                text = badgeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isOngoing -> if (resolved.isLightContent) Color(0xFF86EFAC) else Color(0xFF15803D)
                    isFinished -> Color(resolved.textSecondary)
                    else -> Color(resolved.badgeNormalText)
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Right Info: Title on top, Meta on bottom (Consistent with desktop widget)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(resolved.textPrimary),
                maxLines = 1,
            )
            Text(
                text = metaText,
                fontSize = 11.sp,
                color = Color(resolved.textSecondary),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetColorsGrid(
    selectedHex: String,
    onSelectHex: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WidgetCustomizationOptions.PRESETS.forEach { preset ->
            val color = try {
                Color(android.graphics.Color.parseColor(preset.hex))
            } catch (_: Exception) {
                Color.White
            }
            val isSelected = preset.hex.equals(selectedHex, ignoreCase = true)

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color,
                border = BorderStroke(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                ),
                modifier = Modifier
                    .size(width = 82.dp, height = 36.dp)
                    .clickable { onSelectHex(preset.hex) }
                    .testTag("widget_preset_${preset.hex}"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val textColor = if (androidx.core.graphics.ColorUtils.calculateLuminance(color.hashCode()) < 0.5) Color.White else Color.Black
                    Text(preset.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                }
            }
        }
    }
}

@Composable
private fun HexColorInputField(
    currentHex: String,
    onHexChanged: (String) -> Unit,
) {
    var text by remember(currentHex) { mutableStateOf(currentHex) }
    var isError by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val previewColor = try {
            Color(android.graphics.Color.parseColor(text))
        } catch (_: Exception) {
            Color.Transparent
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(previewColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )

        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val formatted = if (input.startsWith("#")) input else "#$input"
                text = formatted
                try {
                    android.graphics.Color.parseColor(formatted)
                    isError = false
                    onHexChanged(formatted)
                } catch (_: Exception) {
                    isError = true
                }
            },
            label = { Text("自定义颜色 HEX") },
            placeholder = { Text("#FFFFFF") },
            isError = isError,
            supportingText = if (isError) {
                { Text("请输入正确的 HEX 色值 (如 #2563EB)") }
            } else null,
            modifier = Modifier
                .weight(1f)
                .testTag("widget_hex_input"),
            singleLine = true,
        )
    }
}

@Composable
private fun CornerRadiusSelector(
    selected: WidgetCornerRadius,
    onSelect: (WidgetCornerRadius) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WidgetCornerRadius.entries.forEach { radius ->
            val isSelected = selected == radius
            OutlinedButton(
                onClick = { onSelect(radius) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("widget_corner_${radius.name}"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Text(
                    radius.title.substringBefore(" ("),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TextColorModeSelector(
    selected: WidgetTextColorMode,
    onSelect: (WidgetTextColorMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WidgetTextColorMode.entries.forEach { mode ->
            val isSelected = selected == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) }
                    .padding(vertical = 4.dp)
                    .testTag("widget_text_mode_${mode.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect(mode) },
                )
                Spacer(Modifier.width(8.dp))
                Text(mode.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
