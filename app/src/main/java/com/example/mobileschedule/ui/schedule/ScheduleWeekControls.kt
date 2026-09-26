package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mobileschedule.data.model.WeekPosition

/** Week controls stay above the grid; the picker lives in the grid's top-left corner. */
@Composable
internal fun ScheduleWeekControls(
    state: ScheduleUiState.Ready,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onReturnToCurrentWeek: () -> Unit,
    onImport: () -> Unit = {},
) {
    val schedule = state.week.schedule
    val totalWeeks = requireNotNull(state.week.semester.config).totalWeeks
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(state.week.semester.displayName, modifier = Modifier.weight(1f).testTag("semester_name"),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onImport, modifier = Modifier.testTag("schedule_import_top")) {
                Text("导入", style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.fillMaxWidth().testTag("week_switch_bar"),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPreviousWeek, enabled = schedule.week > 1,
                modifier = Modifier.weight(1f).testTag("previous_week")) {
                Text("‹ 上周", style = MaterialTheme.typography.labelMedium)
            }
            when (val position = state.currentPosition) {
                is WeekPosition.InSemester -> TextButton(onClick = onReturnToCurrentWeek,
                    enabled = schedule.week != position.week,
                    modifier = Modifier.weight(1f).testTag("return_to_current")) {
                    Text("回到本周", style = MaterialTheme.typography.labelMedium)
                }
                WeekPosition.BeforeSemester -> TextButton(onClick = onReturnToCurrentWeek,
                    enabled = schedule.week != 1,
                    modifier = Modifier.weight(1f).testTag("return_to_boundary")) {
                    Text("到第1周", style = MaterialTheme.typography.labelMedium)
                }
                WeekPosition.AfterSemester -> TextButton(onClick = onReturnToCurrentWeek,
                    enabled = schedule.week != totalWeeks,
                    modifier = Modifier.weight(1f).testTag("return_to_boundary")) {
                    Text("到末周", style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = onNextWeek, enabled = schedule.week < totalWeeks,
                modifier = Modifier.weight(1f).testTag("next_week")) {
                Text("下周 ›", style = MaterialTheme.typography.labelMedium)
            }
        }
        when (state.currentPosition) {
            WeekPosition.BeforeSemester -> Text("学期尚未开始",
                Modifier.padding(start = 16.dp).testTag("semester_boundary"),
                style = MaterialTheme.typography.bodySmall)
            WeekPosition.AfterSemester -> Text("本学期已结束",
                Modifier.padding(start = 16.dp).testTag("semester_boundary"),
                style = MaterialTheme.typography.bodySmall)
            is WeekPosition.InSemester -> Unit
        }
    }
}

@Composable
internal fun WeekPickerDialog(state: ScheduleUiState.Ready, onSelectWeek: (Int) -> Unit,
    onDismiss: () -> Unit) {
    val config = requireNotNull(state.week.semester.config)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp).testTag("week_picker")) {
                items((1..config.totalWeeks).toList()) { week ->
                    val monday = config.firstWeekMonday.plusWeeks((week - 1).toLong())
                    val currentMark = if ((state.currentPosition as? WeekPosition.InSemester)?.week == week)
                        " · 本周" else ""
                    TextButton(onClick = { onSelectWeek(week); onDismiss() },
                        modifier = Modifier.fillMaxWidth().testTag("week_option_$week")) {
                        Text("第${week}周$currentMark · ${monday.monthValue}/${monday.dayOfMonth}–" +
                            "${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
