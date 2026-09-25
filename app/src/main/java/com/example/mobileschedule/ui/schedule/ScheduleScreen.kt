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
import androidx.compose.ui.res.stringResource
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
import com.example.mobileschedule.ui.common.FoundationPage
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val sectionWidth = 52.dp
private val sectionHeight = 72.dp

@Composable
fun ScheduleRoute(viewModel: ScheduleViewModel = hiltViewModel()) {
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
) {
    when (state) {
        ScheduleUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ScheduleUiState.NoSemester -> FoundationPage(
            title = stringResource(R.string.schedule_empty_title),
            description = stringResource(R.string.schedule_empty_body),
            modifier = Modifier.testTag("schedule_empty"),
        )
        ScheduleUiState.ConfigRequired -> FoundationPage(
            title = stringResource(R.string.schedule_config_title),
            description = stringResource(R.string.schedule_config_body),
            modifier = Modifier.testTag("schedule_config_required"),
        )
        ScheduleUiState.Error -> FoundationPage(
            title = stringResource(R.string.schedule_error_title),
            description = stringResource(R.string.schedule_error_body),
            modifier = Modifier.testTag("schedule_error"),
        )
        is ScheduleUiState.Ready -> Column(Modifier.fillMaxSize()) {
            ScheduleWeekControls(state, onPreviousWeek, onNextWeek, onSelectWeek, onReturnToCurrentWeek)
            if (state.week.schedule.arrangements.isEmpty()) {
                Text("本周没有课程安排", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.weight(1f)) {
                WeekScheduleGrid(state.week, state.today, onCourseClick)
            }
        }
    }
    CourseDetailSheet(detailState, onDismissDetail)
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
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val todayIndex = ChronoUnit.DAYS.between(schedule.monday, today).toInt().takeIf { it in 0..6 }
    var overlappingCourses by remember { mutableStateOf<List<com.example.mobileschedule.data.model.CourseArrangement>?>(null) }

    Column(Modifier.fillMaxSize().testTag("week_grid")) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dayWidth = maxOf(96.dp, (maxWidth - sectionWidth) / 7)
            val gridWidth = dayWidth * 7
            val gridHeight = sectionHeight * config.totalSections
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(54.dp)) {
                    Text("节次\n时间", Modifier.width(sectionWidth).padding(top = 6.dp),
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
                    Column(Modifier.width(sectionWidth)) {
                        repeat(config.totalSections) { index ->
                            val start = config.sectionTimes.firstOrNull { it.section == index + 1 }?.start
                            Column(Modifier.height(sectionHeight).fillMaxWidth().padding(top = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                                Text(start?.format(timeFormat) ?: "—", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).horizontalScroll(horizontal).testTag("week_body_scroll")) {
                        Box(Modifier.width(gridWidth).height(gridHeight)) {
                            WeekGridLines(gridWidth, gridHeight, dayWidth, config.totalSections, todayIndex)
                            buildWeekGridSlots(schedule.arrangements).forEach { slot ->
                                if (slot.arrangements.size == 2 && dayWidth >= 136.dp) {
                                    slot.arrangements.forEachIndexed { lane, course ->
                                        WeekGridCard(
                                            slot = WeekGridSlot(slot.dayOfWeek, course.startSection, course.endSection, listOf(course)),
                                            dayWidth = dayWidth,
                                            lane = lane,
                                            laneCount = 2,
                                            onClick = { onCourseClick(course.id) },
                                        )
                                    }
                                } else {
                                    WeekGridCard(slot = slot, dayWidth = dayWidth, onClick = {
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
private fun WeekGridLines(gridWidth: Dp, gridHeight: Dp, dayWidth: Dp, sectionCount: Int, todayIndex: Int?) {
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
            val y = sectionHeight.toPx() * section
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
    }
}

@Composable
private fun WeekGridCard(slot: WeekGridSlot, dayWidth: Dp, onClick: () -> Unit,
    lane: Int = 0, laneCount: Int = 1) {
    val course = slot.arrangements.singleOrNull()
    val tag = if (course != null) "course_${course.id}" else "overlap_${slot.dayOfWeek}_${slot.startSection}"
    Card(
        modifier = Modifier
            .offset(x = dayWidth * (slot.dayOfWeek - 1) + dayWidth / laneCount * lane + 3.dp,
                y = sectionHeight * (slot.startSection - 1) + 3.dp)
            .width(dayWidth / laneCount - 6.dp)
            .height(sectionHeight * slot.sectionSpan - 6.dp)
            .testTag(tag)
            .clickable(onClick = onClick),
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
