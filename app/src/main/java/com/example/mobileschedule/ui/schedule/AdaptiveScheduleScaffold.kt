package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.ui.theme.CourseColorTheme
import com.example.mobileschedule.ui.theme.LocalIsDarkTheme
import com.example.mobileschedule.ui.today.LunarCalendarHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Calculates standard Material 3 WindowWidthSizeClass based on dp width.
 * Compact: < 600dp (standard portrait phones)
 * Medium: 600dp ..< 840dp (portrait tablets, folded foldables)
 * Expanded: >= 840dp (landscape tablets, unfolded foldable inner screen)
 */
fun calculateWindowWidthClass(widthDp: Dp): WindowWidthSizeClass = when {
    widthDp < 600.dp -> WindowWidthSizeClass.Compact
    widthDp < 840.dp -> WindowWidthSizeClass.Medium
    else -> WindowWidthSizeClass.Expanded
}

@Composable
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass {
    val configuration = LocalConfiguration.current
    return calculateWindowWidthClass(configuration.screenWidthDp.dp)
}

/**
 * Dual-column adaptive layout scaffold for Expanded screens (tablets, foldables unfolded, landscape).
 * Left column (35% width): Today timeline, tasks summary, and mini week navigator.
 * Right column (65% width): Full week schedule grid with optimal card proportions.
 */
@Composable
fun AdaptiveScheduleScaffold(
    state: ScheduleUiState.Ready,
    detailState: CourseDetailUiState,
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
    onCourseClick: (Long) -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onSelectWeek: (Int) -> Unit = {},
    onReturnToCurrentWeek: () -> Unit = {},
    onAddCourse: () -> Unit = {},
    onImport: () -> Unit = {},
    onExportImage: () -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .testTag("adaptive_schedule_scaffold"),
    ) {
        // Left Column (35% width): Today timeline & schedule controls
        Surface(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .testTag("adaptive_left_panel"),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            AdaptiveSidePanel(
                state = state,
                onCourseClick = onCourseClick,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onSelectWeek = onSelectWeek,
                onReturnToCurrentWeek = onReturnToCurrentWeek,
                onAddCourse = onAddCourse,
                onImport = onImport,
                onExportImage = onExportImage,
                onSettings = onSettings,
            )
        }

        VerticalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        // Right Column (65% width): Big screen week schedule grid
        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .testTag("adaptive_right_grid"),
        ) {
            Column(Modifier.fillMaxSize()) {
                AdaptiveRightHeader(
                    state = state,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onReturnToCurrentWeek = onReturnToCurrentWeek,
                    onAddCourse = onAddCourse,
                    onExportImage = onExportImage,
                )

                Box(Modifier.weight(1f)) {
                    WeekScheduleGrid(
                        week = state.week,
                        today = state.today,
                        onCourseClick = onCourseClick,
                        displayOptions = displayOptions,
                        unfinishedTaskCounts = state.unfinishedTaskCounts,
                        onWeekTitleClick = {},
                        onPreviousWeek = onPreviousWeek,
                        onNextWeek = onNextWeek,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveSidePanel(
    state: ScheduleUiState.Ready,
    onCourseClick: (Long) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onReturnToCurrentWeek: () -> Unit,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    onExportImage: () -> Unit,
    onSettings: () -> Unit,
) {
    val today = state.today
    val todayDayOfWeek = today.dayOfWeek.value
    val todayCourses = remember(state.week.schedule.arrangements, todayDayOfWeek) {
        state.week.schedule.arrangements
            .filter { it.dayOfWeek == todayDayOfWeek }
            .sortedBy { it.startSection }
    }

    val totalTasks = remember(state.unfinishedTaskCounts) {
        state.unfinishedTaskCounts.values.sum()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Semester Title & Today Badge
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = state.week.semester.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val dateText = today.format(DateTimeFormatter.ofPattern("M月d日"))
                    val dayChinese = LunarCalendarHelper.dayOfWeekChinese(todayDayOfWeek)
                    val lunar = LunarCalendarHelper.getLunarDateString(today)

                    Text(
                        text = "$dateText $dayChinese",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lunar.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = lunar,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Mini Week Navigator Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("adaptive_mini_week_switcher"),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "第 ${state.week.schedule.week} 周",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }

                            val isCurrentWeek = state.currentPosition is WeekPosition.InSemester &&
                                (state.currentPosition as WeekPosition.InSemester).week == state.week.schedule.week
                            if (isCurrentWeek) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        text = "当前周",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = onPreviousWeek,
                                enabled = state.week.schedule.week > 1,
                                modifier = Modifier.testTag("adaptive_prev_week"),
                            ) {
                                Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = onNextWeek,
                                enabled = state.week.schedule.week < (state.week.semester.config?.totalWeeks ?: 25),
                                modifier = Modifier.testTag("adaptive_next_week"),
                            ) {
                                Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    val startText = state.week.schedule.monday.format(DateTimeFormatter.ofPattern("M.d"))
                    val endText = state.week.schedule.sunday.format(DateTimeFormatter.ofPattern("M.d"))
                    Text(
                        text = "周历跨度：$startText ~ $endText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Today Courses Timeline Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "今日课程 (${todayCourses.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (totalTasks > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = "待办 $totalTasks 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Today Courses Timeline Items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("adaptive_today_timeline"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (todayCourses.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "今天全天没有排课，尽情享受自习或休息时光吧~",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    todayCourses.forEach { course ->
                        AdaptiveTimelineCard(
                            course = course,
                            unfinishedTasks = state.unfinishedTaskCounts[course.id] ?: 0,
                            onClick = { onCourseClick(course.id) },
                        )
                    }
                }
            }
        }

        // Quick Tools Bottom Row (Docked at bottom of left panel)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onAddCourse,
                modifier = Modifier
                    .weight(1f)
                    .testTag("adaptive_add_course_button"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("＋ 新增课程", style = MaterialTheme.typography.labelMedium)
            }

            FilledTonalButton(
                onClick = onExportImage,
                modifier = Modifier
                    .weight(1f)
                    .testTag("adaptive_export_button"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("🖼️ 导出分享", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AdaptiveTimelineCard(
    course: CourseArrangement,
    unfinishedTasks: Int,
    onClick: () -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val palette = CourseColorTheme.forCourse(course.name, isDark)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.container.copy(alpha = 0.85f),
        ),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adaptive_today_course_card_${course.id}")
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "第 ${course.startSection}-${course.endSection} 节",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                )

                if (unfinishedTasks > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.error,
                    ) {
                        Text(
                            text = "待办 $unfinishedTasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Text(
                text = course.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!course.location.isNullOrBlank()) {
                    Text(
                        text = "📍 ${course.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                if (!course.teacher.isNullOrBlank()) {
                    Text(
                        text = "👤 ${course.teacher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveRightHeader(
    state: ScheduleUiState.Ready,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onReturnToCurrentWeek: () -> Unit,
    onAddCourse: () -> Unit,
    onExportImage: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adaptive_right_header"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "周课表概览 · 第 ${state.week.schedule.week} 周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                val isCurrent = state.currentPosition is WeekPosition.InSemester &&
                    (state.currentPosition as WeekPosition.InSemester).week == state.week.schedule.week
                if (!isCurrent) {
                    TextButton(
                        onClick = onReturnToCurrentWeek,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("adaptive_return_current_week"),
                    ) {
                        Text("回到本周", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPreviousWeek,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("上一周", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onNextWeek,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("下一周", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
