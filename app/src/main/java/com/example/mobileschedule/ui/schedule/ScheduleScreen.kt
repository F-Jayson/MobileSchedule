package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mobileschedule.R
import com.example.mobileschedule.data.model.ActiveWeek
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val sectionWidth = 40.dp
private val sectionHeight = 72.dp

@Composable
fun ScheduleRoute(
    onConfigure: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onDisplaySettings: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val displayStore = remember(context) { ScheduleDisplaySettingsStore(context) }
    var displayOptions by remember(displayStore) { mutableStateOf(displayStore.read()) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
        displayOptions = displayStore.read()
    }
    ScheduleScreen(
        state = state,
        detailState = detailState,
        displayOptions = displayOptions,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onSelectWeek = viewModel::selectWeek,
        onReturnToCurrentWeek = viewModel::returnToCurrentWeek,
        onCourseClick = viewModel::showCourseDetail,
        onDismissDetail = viewModel::closeCourseDetail,
        onConfigure = onConfigure,
        onSettings = onSettings,
        onImport = onImport,
        onDisplaySettings = onDisplaySettings,
        onRetry = viewModel::retry,
    )
}

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    detailState: CourseDetailUiState = CourseDetailUiState.Closed,
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onSelectWeek: (Int) -> Unit = {},
    onReturnToCurrentWeek: () -> Unit = {},
    onCourseClick: (Long) -> Unit = {},
    onDismissDetail: () -> Unit = {},
    onConfigure: () -> Unit = {},
    onSettings: () -> Unit = {},
    onImport: () -> Unit = {},
    onDisplaySettings: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        ScheduleUiState.Loading -> Column(Modifier.fillMaxSize().testTag("schedule_loading"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            CircularProgressIndicator()
            Text("正在读取本地课表…", modifier = Modifier.padding(top = 12.dp))
        }
        ScheduleUiState.NoSemester -> ScheduleStatusPage(
            title = stringResource(R.string.schedule_empty_title),
            description = stringResource(R.string.schedule_empty_body),
            tag = "schedule_empty", primaryLabel = "配置学期", primaryTag = "schedule_configure",
            onPrimary = onConfigure, secondaryLabel = "导入课程表", secondaryTag = "schedule_import",
            onSecondary = onImport,
        )
        ScheduleUiState.ConfigRequired -> ScheduleStatusPage(
            title = stringResource(R.string.schedule_config_title),
            description = stringResource(R.string.schedule_config_body),
            tag = "schedule_config_required", primaryLabel = "完善学期配置", primaryTag = "schedule_settings",
            onPrimary = onSettings, secondaryLabel = "导入课程表", secondaryTag = "schedule_import",
            onSecondary = onImport,
        )
        ScheduleUiState.Error -> ScheduleStatusPage(
            title = stringResource(R.string.schedule_error_title),
            description = stringResource(R.string.schedule_error_body),
            tag = "schedule_error", primaryLabel = "重试读取", primaryTag = "schedule_retry",
            onPrimary = onRetry, secondaryLabel = "打开设置", secondaryTag = "schedule_settings",
            onSecondary = onSettings,
        )
        is ScheduleUiState.Ready -> {
            var showWeekPicker by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                ScheduleWeekControls(state, onPreviousWeek, onNextWeek, onReturnToCurrentWeek, onImport)
                if (state.week.schedule.arrangements.isEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("本周没有课程安排", modifier = Modifier.weight(1f).testTag("schedule_week_empty"),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onImport, modifier = Modifier.testTag("schedule_import")) {
                            Text("导入课表", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    WeekScheduleGrid(state.week, state.today, onCourseClick,
                        displayOptions = displayOptions,
                        onWeekTitleClick = { showWeekPicker = true },
                        onPreviousWeek = onPreviousWeek, onNextWeek = onNextWeek)
                }
                TextButton(onClick = onDisplaySettings,
                    modifier = Modifier.fillMaxWidth().testTag("schedule_display_settings")) {
                    Text("课表设置", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (showWeekPicker) WeekPickerDialog(state, onSelectWeek) { showWeekPicker = false }
        }
    }
    CourseDetailSheet(detailState, onDismissDetail)
}

@Composable
private fun ScheduleStatusPage(
    title: String, description: String, tag: String,
    primaryLabel: String, primaryTag: String, onPrimary: () -> Unit,
    secondaryLabel: String, secondaryTag: String, onSecondary: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag(tag),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onPrimary, modifier = Modifier.testTag(primaryTag)) { Text(primaryLabel) }
        TextButton(onClick = onSecondary, modifier = Modifier.testTag(secondaryTag)) { Text(secondaryLabel) }
    }
}

/** All seven days share the available width; horizontal drags change weeks, vertical drags scroll sections. */
@Composable
fun WeekScheduleGrid(
    week: ActiveWeek,
    today: LocalDate = LocalDate.now(),
    onCourseClick: (Long) -> Unit = {},
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
    onWeekTitleClick: () -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
) {
    val config = requireNotNull(week.semester.config)
    val schedule = week.schedule
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    val rowHeight = sectionHeight * fontScale
    val rulerWidth = (sectionWidth * fontScale.coerceAtMost(1.15f)).coerceAtMost(46.dp)
    val vertical = rememberScrollState()
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val todayIndex = ChronoUnit.DAYS.between(schedule.monday, today).toInt().takeIf { it in 0..6 }
    var overlappingCourses by remember { mutableStateOf<List<com.example.mobileschedule.data.model.CourseArrangement>?>(null) }

    Column(Modifier.fillMaxSize().testTag("week_grid")
        .pointerInput(schedule.week, config.totalWeeks, onPreviousWeek, onNextWeek) {
            var dragDistance = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragDistance = 0f },
                onDragEnd = {
                    if (dragDistance >= swipeThresholdPx && schedule.week > 1) onPreviousWeek()
                    if (dragDistance <= -swipeThresholdPx && schedule.week < config.totalWeeks) onNextWeek()
                    dragDistance = 0f
                },
                onDragCancel = { dragDistance = 0f },
                onHorizontalDrag = { change, amount -> dragDistance += amount; change.consume() },
            )
        }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dayWidth = (maxWidth - rulerWidth) / 7
            val gridWidth = dayWidth * 7
            val gridHeight = rowHeight * config.totalSections
            val compactHeader = dayWidth < 45.dp || fontScale > 1.3f
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height((48.dp * fontScale).coerceAtMost(82.dp))) {
                    TextButton(onClick = onWeekTitleClick,
                        modifier = Modifier.width(rulerWidth).testTag("week_title")) {
                        Text("${schedule.week}\n周", textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                    }
                    Row(Modifier.weight(1f).testTag("week_header")) {
                        weekdays.forEachIndexed { index, day ->
                            val date = schedule.monday.plusDays(index.toLong())
                            val label = if (compactHeader) day.removePrefix("周") else day
                            val dateLabel = if (compactHeader) "${date.dayOfMonth}"
                                else "%02d-%02d".format(date.monthValue, date.dayOfMonth)
                            Text("$label\n$dateLabel",
                                Modifier.width(dayWidth)
                                    .background(if (todayIndex == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(top = 6.dp).testTag("day_${index + 1}"),
                                textAlign = TextAlign.Center, maxLines = 2,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(vertical)) {
                    Column(Modifier.width(rulerWidth)) {
                        repeat(config.totalSections) { index ->
                            val interval = ScheduleDisplayDefaults.forSection(config, index + 1)
                            Column(Modifier.height(rowHeight).fillMaxWidth().padding(top = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp))
                                Text(interval?.let { "${it.start.format(timeFormat)}\n${it.end.format(timeFormat)}" } ?: "—",
                                    modifier = Modifier.testTag("section_time_${index + 1}"),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                            }
                        }
                    }
                    Box(Modifier.weight(1f).testTag("week_body_scroll")) {
                        Box(Modifier.width(gridWidth).height(gridHeight)) {
                            WeekGridLines(gridWidth, gridHeight, dayWidth, rowHeight, config.totalSections, todayIndex)
                            buildWeekGridSlots(schedule.arrangements).forEach { slot ->
                                if (slot.arrangements.size == 2 && dayWidth >= 136.dp) {
                                    slot.arrangements.forEachIndexed { lane, course ->
                                        WeekGridCard(
                                            slot = WeekGridSlot(slot.dayOfWeek, course.startSection, course.endSection, listOf(course)),
                                            dayWidth = dayWidth,
                                            rowHeight = rowHeight,
                                            lane = lane,
                                            laneCount = 2,
                                            displayOptions = displayOptions,
                                            onClick = { onCourseClick(course.id) },
                                        )
                                    }
                                } else {
                                    WeekGridCard(slot = slot, dayWidth = dayWidth, rowHeight = rowHeight,
                                        displayOptions = displayOptions, onClick = {
                                        val course = slot.arrangements.singleOrNull()
                                        if (course != null) onCourseClick(course.id)
                                        else overlappingCourses = slot.arrangements
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    overlappingCourses?.let { courses ->
        AlertDialog(
            onDismissRequest = { overlappingCourses = null },
            title = { Text("同一时段有${courses.size}门课程") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(courses, key = { it.id }) { course ->
                        TextButton(onClick = { overlappingCourses = null; onCourseClick(course.id) },
                            modifier = Modifier.fillMaxWidth().testTag("conflict_course_${course.id}")) {
                            Text("${course.name} · ${course.location ?: "地点未提供"}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { overlappingCourses = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun WeekGridLines(gridWidth: Dp, gridHeight: Dp, dayWidth: Dp, rowHeight: Dp,
    sectionCount: Int, todayIndex: Int?) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val todayColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
    Canvas(Modifier.width(gridWidth).height(gridHeight)) {
        if (todayIndex != null) {
            drawRect(todayColor, topLeft = Offset(dayWidth.toPx() * todayIndex, 0f),
                size = androidx.compose.ui.geometry.Size(dayWidth.toPx(), size.height))
        }
        for (day in 0..7) {
            val x = dayWidth.toPx() * day
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        for (section in 0..sectionCount) {
            val y = rowHeight.toPx() * section
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
    }
}

@Composable
private fun WeekGridCard(slot: WeekGridSlot, dayWidth: Dp, rowHeight: Dp, onClick: () -> Unit,
    lane: Int = 0, laneCount: Int = 1,
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions()) {
    val course = slot.arrangements.singleOrNull()
    val tag = if (course != null) "course_${course.id}" else "overlap_${slot.dayOfWeek}_${slot.startSection}"
    val description = if (course == null) {
        "${weekdays[slot.dayOfWeek - 1]}，第${slot.startSection}至${slot.endSection}节，同一时段${slot.arrangements.size}门课程，点击查看列表"
    } else buildList {
        add("${weekdays[slot.dayOfWeek - 1]}，第${slot.startSection}至${slot.endSection}节，${course.name}")
        if (displayOptions.showLocation) add(course.location ?: "地点未提供")
        if (displayOptions.showTeacher) add(course.teacher ?: "教师未提供")
    }.joinToString("，")
    Card(
        modifier = Modifier
            .offset(x = dayWidth * (slot.dayOfWeek - 1) + dayWidth / laneCount * lane + 2.dp,
                y = rowHeight * (slot.startSection - 1) + 3.dp)
            .width(dayWidth / laneCount - 4.dp)
            .height(rowHeight * slot.sectionSpan - 6.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.fillMaxSize().padding(3.dp)) {
            if (course == null) {
                Text("此时段有${slot.arrangements.size}门课程", maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                Text("点击查看", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            } else {
                Text(course.name, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                if (displayOptions.showLocation) Text(course.location ?: "地点未提供", maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                if (displayOptions.showTeacher) Text(course.teacher ?: "教师未提供", maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                if (laneCount > 1) Text("时间重叠", maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            }
        }
    }
}
