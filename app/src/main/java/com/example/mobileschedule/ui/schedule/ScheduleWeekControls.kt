package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.example.mobileschedule.data.model.WeekPosition

/** Only this top bar interprets a horizontal drag as a week change. */
@Composable
internal fun ScheduleWeekControls(
    state: ScheduleUiState.Ready,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onReturnToCurrentWeek: () -> Unit,
    onImport: () -> Unit = {},
) {
    val schedule = state.week.schedule
    val totalWeeks = requireNotNull(state.week.semester.config).totalWeeks
    val canPrevious = schedule.week > 1
    val canNext = schedule.week < totalWeeks
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(state.week.semester.displayName, modifier = Modifier.weight(1f).testTag("semester_name"),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onImport, modifier = Modifier.testTag("schedule_import_top")) { Text("导入") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("week_switch_bar")
            .pointerInput(canPrevious, canNext, onPreviousWeek, onNextWeek, thresholdPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onDragEnd = {
                            if (dragDistance > thresholdPx && canPrevious) onPreviousWeek()
                            if (dragDistance < -thresholdPx && canNext) onNextWeek()
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                        onHorizontalDrag = { change, amount ->
                            dragDistance += amount
                            change.consume()
                        },
                    )
                }) {
            val weekLabel = "第${schedule.week}周 · ${schedule.monday.monthValue}/${schedule.monday.dayOfMonth}–${schedule.sunday.monthValue}/${schedule.sunday.dayOfMonth}"
            if (maxWidth < 420.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                Column {
                    TextButton(onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().testTag("week_title")) { Text(weekLabel) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                        TextButton(onClick = onPreviousWeek, enabled = canPrevious,
                            modifier = Modifier.testTag("previous_week")) { Text("‹ 上周") }
                        TextButton(onClick = onNextWeek, enabled = canNext,
                            modifier = Modifier.testTag("next_week")) { Text("下周 ›") }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onPreviousWeek, enabled = canPrevious,
                        modifier = Modifier.testTag("previous_week")) { Text("‹ 上周") }
                    TextButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f).testTag("week_title")) {
                        Text(weekLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onNextWeek, enabled = canNext,
                        modifier = Modifier.testTag("next_week")) { Text("下周 ›") }
                }
            }
        }
        when (val position = state.currentPosition) {
            WeekPosition.BeforeSemester -> {
                Text("学期尚未开始", Modifier.padding(start = 16.dp).testTag("semester_boundary"),
                    style = MaterialTheme.typography.bodyMedium)
                if (schedule.week != 1) {
                    TextButton(onClick = onReturnToCurrentWeek, modifier = Modifier.testTag("return_to_boundary")) {
                        Text("查看第1周")
                    }
                }
            }
            WeekPosition.AfterSemester -> {
                Text("本学期已结束", Modifier.padding(start = 16.dp).testTag("semester_boundary"),
                    style = MaterialTheme.typography.bodyMedium)
                if (schedule.week != totalWeeks) {
                    TextButton(onClick = onReturnToCurrentWeek, modifier = Modifier.testTag("return_to_boundary")) {
                        Text("查看最后一周")
                    }
                }
            }
            is WeekPosition.InSemester -> if (schedule.week != position.week) {
                TextButton(onClick = onReturnToCurrentWeek, modifier = Modifier.testTag("return_to_current")) {
                    Text("回到本周 · 第${position.week}周")
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择周次") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp).testTag("week_picker")) {
                    items((1..totalWeeks).toList()) { week ->
                        val monday = requireNotNull(state.week.semester.config).firstWeekMonday.plusWeeks((week - 1).toLong())
                        val currentMark = if ((state.currentPosition as? WeekPosition.InSemester)?.week == week) " · 本周" else ""
                        TextButton(
                            onClick = { onSelectWeek(week); showPicker = false },
                            modifier = Modifier.fillMaxWidth().testTag("week_option_$week"),
                        ) {
                            Text("第${week}周$currentMark · ${monday.monthValue}/${monday.dayOfMonth}–${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("关闭") } },
        )
    }
}
