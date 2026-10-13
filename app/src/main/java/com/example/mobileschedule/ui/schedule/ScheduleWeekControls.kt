package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    onImport: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
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
                }
                OutlinedButton(
                    onClick = onImport,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("schedule_import_top"),
                ) {
                    Text("导入", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun ScheduleWeekControls(
    state: ScheduleUiState.Ready,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onReturnToCurrentWeek: () -> Unit,
) {
    val schedule = state.week.schedule
    val totalWeeks = requireNotNull(state.week.semester.config).totalWeeks

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp).testTag("week_switch_bar"),
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
                modifier = Modifier.weight(1f).testTag("previous_week"),
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
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("return_to_current"),
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
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("return_to_boundary"),
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
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("return_to_boundary"),
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
                modifier = Modifier.weight(1f).testTag("next_week"),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("下周 ›", maxLines = 1, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall)
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
