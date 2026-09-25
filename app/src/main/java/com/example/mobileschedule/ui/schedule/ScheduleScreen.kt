package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
private val sectionWidth = 52.dp
private val sectionHeight = 72.dp

@Composable
fun ScheduleRoute(
    onConfigure: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    ScheduleScreen(
        state = state,
        detailState = detailState,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onSelectWeek = viewModel::selectWeek,
        onReturnToCurrentWeek = viewModel::returnToCurrentWeek,
        onCourseClick = viewModel::showCourseDetail,
        onDismissDetail = viewModel::closeCourseDetail,
        onConfigure = onConfigure,
        onSettings = onSettings,
        onImport = onImport,
        onRetry = viewModel::retry,
    )
}

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    detailState: CourseDetailUiState = CourseDetailUiState.Closed,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onSelectWeek: (Int) -> Unit = {},
    onReturnToCurrentWeek: () -> Unit = {},
    onCourseClick: (Long) -> Unit = {},
    onDismissDetail: () -> Unit = {},
    onConfigure: () -> Unit = {},
    onSettings: () -> Unit = {},
    onImport: () -> Unit = {},
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
        is ScheduleUiState.Ready -> Column(Modifier.fillMaxSize()) {
            ScheduleWeekControls(state, onPreviousWeek, onNextWeek, onSelectWeek, onReturnToCurrentWeek, onImport)
            if (state.week.schedule.arrangements.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("本周没有课程安排", modifier = Modifier.weight(1f).testTag("schedule_week_empty"),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onImport, modifier = Modifier.testTag("schedule_import")) {
                        Text("导入课表")
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                WeekScheduleGrid(state.week, state.today, onCourseClick)
            }
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

/** Header and cards share one horizontal position; the left section ruler stays visible. */
@Composable
fun WeekScheduleGrid(
    week: ActiveWeek,
    today: LocalDate = LocalDate.now(),
    onCourseClick: (Long) -> Unit = {},
) {
    val config = requireNotNull(week.semester.config)
    val schedule = week.schedule
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    val rowHeight = sectionHeight * fontScale
    val rulerWidth = sectionWidth * fontScale
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val todayIndex = ChronoUnit.DAYS.between(schedule.monday, today).toInt().takeIf { it in 0..6 }
    var overlappingCourses by remember { mutableStateOf<List<com.example.mobileschedule.data.model.CourseArrangement>?>(null) }

    Column(Modifier.fillMaxSize().testTag("week_grid")) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dayWidth = maxOf(96.dp, (maxWidth - rulerWidth) / 7)
            val gridWidth = dayWidth * 7
            val gridHeight = rowHeight * config.totalSections
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(54.dp * fontScale)) {
                    Text("节次\n时间", Modifier.width(rulerWidth).padding(top = 6.dp),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.weight(1f).horizontalScroll(horizontal).testTag("week_header_scroll")) {
                        weekdays.forEachIndexed { index, day ->
                            val date = schedule.monday.plusDays(index.toLong())
                            Text("$day\n${date.monthValue}/${date.dayOfMonth}",
                                Modifier.width(dayWidth)
                                    .background(if (todayIndex == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(top = 6.dp).testTag("day_${index + 1}"),
                                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(vertical)) {
                    Column(Modifier.width(rulerWidth)) {
                        repeat(config.totalSections) { index ->
                            val start = config.sectionTimes.firstOrNull { it.section == index + 1 }?.start
                            Column(Modifier.height(rowHeight).fillMaxWidth().padding(top = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                                Text(start?.format(timeFormat) ?: "—", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).horizontalScroll(horizontal).testTag("week_body_scroll")) {
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
                                            onClick = { onCourseClick(course.id) },
                                        )
                                    }
                                } else {
                                    WeekGridCard(slot = slot, dayWidth = dayWidth, rowHeight = rowHeight, onClick = {
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
    lane: Int = 0, laneCount: Int = 1) {
    val course = slot.arrangements.singleOrNull()
    val tag = if (course != null) "course_${course.id}" else "overlap_${slot.dayOfWeek}_${slot.startSection}"
    val description = if (course == null) {
        "${weekdays[slot.dayOfWeek - 1]}，第${slot.startSection}至${slot.endSection}节，同一时段${slot.arrangements.size}门课程，点击查看列表"
    } else {
        "${weekdays[slot.dayOfWeek - 1]}，第${slot.startSection}至${slot.endSection}节，${course.name}，${course.location ?: "地点未提供"}"
    }
    Card(
        modifier = Modifier
            .offset(x = dayWidth * (slot.dayOfWeek - 1) + dayWidth / laneCount * lane + 3.dp,
                y = rowHeight * (slot.startSection - 1) + 3.dp)
            .width(dayWidth / laneCount - 6.dp)
            .height(rowHeight * slot.sectionSpan - 6.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp)) {
            if (course == null) {
                Text("此时段有${slot.arrangements.size}门课程", maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                Text("点击查看", style = MaterialTheme.typography.labelSmall)
            } else {
                Text(course.name, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium)
                Text(course.location ?: "地点未提供", maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                if (laneCount > 1) Text("时间重叠", maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
