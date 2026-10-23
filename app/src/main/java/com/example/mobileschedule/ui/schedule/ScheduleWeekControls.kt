package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.data.model.WeekPosition

@Composable
internal fun ScheduleSemesterHeader(
    state: ScheduleUiState.Ready,
    showWeekend: Boolean = true,
    hasWallpaper: Boolean = false,
    isWallpaperDark: Boolean = false,
    onImport: () -> Unit = {},
    onAddCourse: () -> Unit = {},
    onExportImage: () -> Unit = {},
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).testTag("schedule_semester_header"),
        shape = RoundedCornerShape(16.dp),
        color = if (hasWallpaper) {
            if (isWallpaperDark) Color(0xFF1E293B).copy(alpha = 0.82f)
            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
        } else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (hasWallpaper) {
            BorderStroke(
                0.8.dp,
                if (isWallpaperDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.65f)
            )
        } else null,
        tonalElevation = if (hasWallpaper) 0.dp else 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (state.week.schedule.arrangements.isEmpty()) 42.dp else 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.week.semester.displayName,
                        modifier = Modifier.testTag("semester_name"),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    when (state.currentPosition) {
                        WeekPosition.BeforeSemester -> Text(
                            "学期尚未开始",
                            Modifier.padding(top = 2.dp).testTag("semester_boundary"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        WeekPosition.AfterSemester -> Text(
                            "本学期已结束",
                            Modifier.padding(top = 2.dp).testTag("semester_boundary"),
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        is WeekPosition.InSemester -> Unit
                    }
                    val hasWeekendCourses = state.week.schedule.arrangements.any { it.dayOfWeek in 6..7 }
                    if (!showWeekend && hasWeekendCourses) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(top = 3.dp).testTag("weekend_course_notice"),
                        ) {
                            Text(
                                "周末有课（已折叠）",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onAddCourse,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.padding(start = 6.dp).testTag("schedule_add_course"),
                    ) {
                        Text("+ 添加课程", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = onExportImage,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.padding(start = 6.dp).testTag("schedule_export_image"),
                    ) {
                        Text("导出长图", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                    if (state.week.schedule.arrangements.isEmpty()) FilledTonalButton(
                        onClick = onImport,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.padding(start = 6.dp).testTag("schedule_import"),
                    ) {
                        Text("导入课表", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun ScheduleWeekControls(
    state: ScheduleUiState.Ready,
    hasWallpaper: Boolean = false,
    isWallpaperDark: Boolean = false,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onReturnToCurrentWeek: () -> Unit,
) {
    val schedule = state.week.schedule
    val totalWeeks = requireNotNull(state.week.semester.config).totalWeeks
    val buttonHeight = if (LocalDensity.current.fontScale > 1.2f) 48.dp else 36.dp

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides buttonHeight) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (hasWallpaper) {
            if (isWallpaperDark) Color(0xFF1E293B).copy(alpha = 0.82f)
            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
        } else MaterialTheme.colorScheme.surfaceContainer,
        border = if (hasWallpaper) {
            BorderStroke(
                0.8.dp,
                if (isWallpaperDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.60f)
            )
        } else null,
        tonalElevation = if (hasWallpaper) 0.dp else 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp).testTag("week_switch_bar"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onPreviousWeek,
                enabled = schedule.week > 1,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f).height(buttonHeight).testTag("previous_week"),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("‹ 上周", maxLines = 1, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall)
            }

            when (val position = state.currentPosition) {
                is WeekPosition.InSemester -> FilledTonalButton(
                    onClick = onReturnToCurrentWeek,
                    enabled = schedule.week != position.week,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(buttonHeight).testTag("return_to_current"),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text("回到本周", maxLines = 1, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                }
                WeekPosition.BeforeSemester -> FilledTonalButton(
                    onClick = onReturnToCurrentWeek,
                    enabled = schedule.week != 1,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(buttonHeight).testTag("return_to_boundary"),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text("到第1周", maxLines = 1, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall)
                }
                WeekPosition.AfterSemester -> FilledTonalButton(
                    onClick = onReturnToCurrentWeek,
                    enabled = schedule.week != totalWeeks,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(buttonHeight).testTag("return_to_boundary"),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text("到末周", maxLines = 1, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall)
                }
            }

            FilledTonalButton(
                onClick = onNextWeek,
                enabled = schedule.week < totalWeeks,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f).height(buttonHeight).testTag("next_week"),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("下周 ›", maxLines = 1, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    }
}

@Composable
internal fun WeekPickerDialog(
    state: ScheduleUiState.Ready,
    onSelectWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val config = requireNotNull(state.week.semester.config)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("选择周次", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 380.dp).testTag("week_picker"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items((1..config.totalWeeks).toList()) { week ->
                    val monday = config.firstWeekMonday.plusWeeks((week - 1).toLong())
                    val isCurrent = (state.currentPosition as? WeekPosition.InSemester)?.week == week
                    val isBrowsing = state.week.schedule.week == week
                    val currentMark = if (isCurrent) " · 本周" else ""

                    Surface(
                        onClick = { onSelectWeek(week); onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isBrowsing -> MaterialTheme.colorScheme.primaryContainer
                            isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = if (isBrowsing) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth().testTag("week_option_$week"),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "第${week}周$currentMark · ${monday.monthValue}/${monday.dayOfMonth}–${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}",
                                fontWeight = if (isBrowsing || isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isBrowsing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontWeight = FontWeight.SemiBold) }
        },
    )
}
