package com.example.mobileschedule.ui.schedule.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.data.model.CourseTaskType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseTaskDialog(
    courseName: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String?, deadlineEpochMillis: Long, type: CourseTaskType) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CourseTaskType.HOMEWORK) }

    // Default to tomorrow 20:00
    val defaultDeadline = remember {
        LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(20, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
    var deadlineEpochMillis by remember { mutableLongStateOf(defaultDeadline) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("添加课程待办 / 日程", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = courseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Task Type Selector
                Column {
                    Text("类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CourseTaskType.entries.forEach { type ->
                            val label = when (type) {
                                CourseTaskType.HOMEWORK -> "作业"
                                CourseTaskType.EXAM -> "考试"
                                CourseTaskType.EXPERIMENT -> "实验"
                                CourseTaskType.CUSTOM -> "其他"
                            }
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.testTag("task_type_${type.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务标题 *") },
                    placeholder = { Text("如：大作业答辩 / 实验报告一") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Deadline Section
                Column {
                    Text("截止时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
                    val deadlineDateTime = Instant.ofEpochMilli(deadlineEpochMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .testTag("task_deadline_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = deadlineDateTime.format(formatter),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "📅 日历",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Quick presets
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickPresetChip("明天", 1) { days ->
                            deadlineEpochMillis = LocalDateTime.of(LocalDate.now().plusDays(days), LocalTime.of(20, 0))
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        QuickPresetChip("3天后", 3) { days ->
                            deadlineEpochMillis = LocalDateTime.of(LocalDate.now().plusDays(days), LocalTime.of(20, 0))
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        QuickPresetChip("下周", 7) { days ->
                            deadlineEpochMillis = LocalDateTime.of(LocalDate.now().plusDays(days), LocalTime.of(20, 0))
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                    }
                }

                // Content / Remark Input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("详细备注（选填）") },
                    placeholder = { Text("要求、资料链接、评分细则等") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_content_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title.trim(),
                            content.trim().takeIf(String::isNotBlank),
                            deadlineEpochMillis,
                            selectedType
                        )
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("task_dialog_confirm")
            ) {
                Text("确认添加")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("task_dialog_cancel")
            ) {
                Text("取消")
            }
        },
        modifier = Modifier.testTag("course_task_dialog")
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadlineEpochMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtcMillis ->
                            val selectedLocalDate = Instant.ofEpochMilli(selectedUtcMillis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            // Keep default 20:00
                            deadlineEpochMillis = LocalDateTime.of(selectedLocalDate, LocalTime.of(20, 0))
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("date_picker_confirm")
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun QuickPresetChip(text: String, daysOffset: Long, onClick: (Long) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.clickable { onClick(daysOffset) }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
