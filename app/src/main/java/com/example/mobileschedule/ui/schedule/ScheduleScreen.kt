package com.example.mobileschedule.ui.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.R
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.ui.theme.CourseColorPalette
import com.example.mobileschedule.ui.theme.CourseColorTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    onRetry: () -> Unit = {},
) {
    when (state) {
        ScheduleUiState.Loading -> Column(
            Modifier.fillMaxSize().testTag("schedule_loading"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text(
                "正在读取本地课表…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
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
                ScheduleSemesterHeader(state, onImport)
                if (state.week.schedule.arrangements.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "本周没有课程安排",
                                modifier = Modifier.weight(1f).testTag("schedule_week_empty"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            FilledTonalButton(
                                onClick = onImport,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("schedule_import"),
                            ) {
                                Text("导入课表", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    WeekScheduleGrid(
                        state.week, state.today, onCourseClick,
                        displayOptions = displayOptions,
                        onWeekTitleClick = { showWeekPicker = true },
                        onPreviousWeek = onPreviousWeek, onNextWeek = onNextWeek,
                    )
                }
                ScheduleWeekControls(state, onPreviousWeek, onNextWeek, onReturnToCurrentWeek)
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth().testTag(primaryTag),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth().testTag(secondaryTag),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(secondaryLabel, fontWeight = FontWeight.Medium)
                }
            }
        }
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

    Column(
        Modifier.fillMaxSize().testTag("week_grid")
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
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dayWidth = (maxWidth - rulerWidth) / 7
            val gridWidth = dayWidth * 7
            val gridHeight = rowHeight * config.totalSections
            val compactHeader = dayWidth < 45.dp || fontScale > 1.3f

            Column(Modifier.fillMaxSize()) {
                // Header row containing week title button and seven days
                Row(
                    Modifier.fillMaxWidth().height((48.dp * fontScale).coerceAtMost(82.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onWeekTitleClick,
                        modifier = Modifier.width(rulerWidth).testTag("week_title"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        AnimatedContent(
                            targetState = schedule.week,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220)))
                                    .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.15f, animationSpec = tween(180)))
                            },
                            label = "week_title_transition",
                        ) { currentWeek ->
                            Text(
                                "${currentWeek}\n周",
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                            )
                        }
                    }
                    Row(Modifier.weight(1f).testTag("week_header")) {
                        AnimatedContent(
                            targetState = schedule,
                            transitionSpec = {
                                val direction = if (targetState.week >= initialState.week) 1 else -1
                                (slideInHorizontally(
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                    initialOffsetX = { fullWidth -> fullWidth * direction / 4 },
                                ) + fadeIn(animationSpec = tween(240)))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth -> -fullWidth * direction / 4 },
                                        ) + fadeOut(animationSpec = tween(180))
                                    )
                            },
                            label = "week_header_transition",
                            modifier = Modifier.fillMaxWidth(),
                        ) { targetSchedule ->
                            Row(Modifier.fillMaxWidth()) {
                                weekdays.forEachIndexed { index, day ->
                                    val date = targetSchedule.monday.plusDays(index.toLong())
                                    val label = if (compactHeader) day.removePrefix("周") else day
                                    val dateLabel = if (compactHeader) "${date.dayOfMonth}"
                                        else "%02d-%02d".format(date.monthValue, date.dayOfMonth)
                                    val isToday = todayIndex == index

                                    Text(
                                        "$label\n$dateLabel",
                                        Modifier.width(dayWidth)
                                            .background(
                                                if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .padding(vertical = 5.dp)
                                            .testTag("day_${index + 1}"),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Grid body
                Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(vertical)) {
                    // Left ruler column
                    Column(Modifier.width(rulerWidth)) {
                        repeat(config.totalSections) { index ->
                            val interval = ScheduleDisplayDefaults.forSection(config, index + 1)
                            Column(
                                Modifier.height(rowHeight).fillMaxWidth().padding(top = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                )
                                Text(
                                    interval?.let { "${it.start.format(timeFormat)}\n${it.end.format(timeFormat)}" } ?: "—",
                                    modifier = Modifier.testTag("section_time_${index + 1}"),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                                )
                            }
                        }
                    }

                    // Main grid area
                    Box(Modifier.weight(1f).testTag("week_body_scroll")) {
                        Box(Modifier.width(gridWidth).height(gridHeight)) {
                            WeekGridLines(gridWidth, gridHeight, dayWidth, rowHeight, config.totalSections, todayIndex)
                            AnimatedContent(
                                targetState = schedule,
                                transitionSpec = {
                                    val direction = if (targetState.week >= initialState.week) 1 else -1
                                    (slideInHorizontally(
                                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> fullWidth * direction / 3 },
                                    ) + fadeIn(
                                        animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing),
                                    )).togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth -> -fullWidth * direction / 3 },
                                        ) + fadeOut(
                                            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
                                        )
                                    )
                                },
                                label = "week_cards_transition",
                                modifier = Modifier.fillMaxSize(),
                            ) { targetSchedule ->
                                Box(Modifier.fillMaxSize()) {
                                    buildWeekGridSlots(targetSchedule.arrangements).forEach { slot ->
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
                                            WeekGridCard(
                                                slot = slot,
                                                dayWidth = dayWidth,
                                                rowHeight = rowHeight,
                                                displayOptions = displayOptions,
                                                onClick = {
                                                    val course = slot.arrangements.singleOrNull()
                                                    if (course != null) onCourseClick(course.id)
                                                    else overlappingCourses = slot.arrangements
                                                },
                                            )
                                        }
                                    }
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
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "同一时段有${courses.size}门课程",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(courses, key = { it.id }) { course ->
                        Surface(
                            onClick = { overlappingCourses = null; onCourseClick(course.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().testTag("conflict_course_${course.id}"),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(course.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    course.location ?: "地点未提供",
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overlappingCourses = null }) {
                    Text("关闭", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun WeekGridLines(
    gridWidth: Dp, gridHeight: Dp, dayWidth: Dp, rowHeight: Dp,
    sectionCount: Int, todayIndex: Int?,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val todayColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
    Canvas(Modifier.width(gridWidth).height(gridHeight)) {
        if (todayIndex != null) {
            drawRect(
                todayColor,
                topLeft = Offset(dayWidth.toPx() * todayIndex, 0f),
                size = androidx.compose.ui.geometry.Size(dayWidth.toPx(), size.height),
            )
        }
        for (day in 0..7) {
            val x = dayWidth.toPx() * day
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
        }
        for (section in 0..sectionCount) {
            val y = rowHeight.toPx() * section
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 0.8.dp.toPx())
        }
    }
}

@Composable
private fun WeekGridCard(
    slot: WeekGridSlot, dayWidth: Dp, rowHeight: Dp, onClick: () -> Unit,
    lane: Int = 0, laneCount: Int = 1,
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
) {
    val course = slot.arrangements.singleOrNull()
    val isDark = isSystemInDarkTheme()
    val palette = if (course != null) {
        CourseColorTheme.forCourse(course.name, isDark)
    } else {
        CourseColorPalette(
            container = if (isDark) Color(0xFF3B1D0E) else Color(0xFFFFEDD5),
            onContainer = if (isDark) Color(0xFFFED7AA) else Color(0xFF9A3412),
            secondaryText = if (isDark) Color(0xFFFDBA74) else Color(0xFFC2410C),
            accent = Color(0xFFF97316),
        )
    }

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
            .offset(
                x = dayWidth * (slot.dayOfWeek - 1) + dayWidth / laneCount * lane + 2.dp,
                y = rowHeight * (slot.startSection - 1) + 3.dp,
            )
            .width(dayWidth / laneCount - 4.dp)
            .height(rowHeight * slot.sectionSpan - 6.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.container,
            contentColor = palette.onContainer,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            if (course == null) {
                Text(
                    "此时段有${slot.arrangements.size}门课程",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = palette.onContainer,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                )
                Text(
                    "点击查看",
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                )
            } else {
                Text(
                    course.name,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onContainer,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                )
                if (displayOptions.showLocation) {
                    Text(
                        course.location ?: "地点未提供",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    )
                }
                if (displayOptions.showTeacher) {
                    Text(
                        course.teacher ?: "教师未提供",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    )
                }
                if (laneCount > 1) {
                    Text(
                        "时间重叠",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    )
                }
            }
        }
    }
}
