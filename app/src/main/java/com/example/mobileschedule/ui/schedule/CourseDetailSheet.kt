package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.CourseTask
import com.example.mobileschedule.data.model.CourseTaskType
import com.example.mobileschedule.data.rules.CourseSlotState
import com.example.mobileschedule.data.rules.ScheduleRules
import com.example.mobileschedule.ui.schedule.task.CourseTaskDialog
import com.example.mobileschedule.ui.theme.CourseColorTheme
import com.example.mobileschedule.ui.theme.LocalIsDarkTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val detailTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val detailWeekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseDetailSheet(
    state: CourseDetailUiState,
    currentWeek: Int? = null,
    onDismiss: () -> Unit,
    onEditCourse: (CourseArrangement) -> Unit = {},
    onDeleteCourse: (Long) -> Unit = {},
    onCopyCourse: (CourseArrangement) -> Unit = {},
    onCreateTask: (title: String, content: String?, deadlineEpochMillis: Long, type: CourseTaskType) -> Unit = { _, _, _, _ -> },
    onToggleTaskCompletion: (taskId: Long, isCompleted: Boolean) -> Unit = { _, _ -> },
    onDeleteTask: (taskId: Long) -> Unit = {},
) {
    if (state == CourseDetailUiState.Closed) return
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("course_detail"),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state) {
                    CourseDetailUiState.Closed -> Unit
                    CourseDetailUiState.Loading -> {
                        Row(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                            Text(
                                "正在读取课程详情…",
                                modifier = Modifier.padding(start = 12.dp).testTag("detail_loading"),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    CourseDetailUiState.Missing -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        ) {
                            Text(
                                "这条课程安排已不存在，可能已被重新导入的课表替换。",
                                modifier = Modifier.padding(16.dp).testTag("detail_missing"),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    CourseDetailUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        ) {
                            Text(
                                "暂时无法读取课程详情，请关闭后重试。",
                                modifier = Modifier.padding(16.dp).testTag("detail_error"),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    is CourseDetailUiState.Ready -> {
                        val detail = state.detail
                        val course = detail.arrangement
                        val isDark = LocalIsDarkTheme.current
                        val colorPalette = CourseColorTheme.forCourse(course.name, isDark)

                        // 头部：课程标题与颜色标识
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colorPalette.container,
                            border = BorderStroke(1.dp, colorPalette.accent.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colorPalette.accent,
                                        modifier = Modifier.size(10.dp),
                                    ) {}
                                    Text(
                                        "课程信息",
                                        modifier = Modifier.padding(start = 8.dp),
                                        color = colorPalette.secondaryText,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    course.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorPalette.onContainer,
                                    modifier = Modifier.testTag("detail_name"),
                                )
                            }
                        }

                        if (currentWeek != null) {
                            val slotState = ScheduleRules.determineCourseSlotState(currentWeek, course.weeks)
                            if (slotState is CourseSlotState.INACTIVE) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth().testTag("detail_non_current_week_notice"),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            "非本周",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                        Text(
                                            slotState.notice,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.testTag("detail_non_current_week_text"),
                                        )
                                    }
                                }
                            }
                        }

                        // 核心安排卡片（时间与地点）
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DetailLine("地点：${course.location?.takeIf(String::isNotBlank) ?: "未提供"}", "detail_location", isHighlight = true)
                                DetailLine("星期：${detailWeekdays.getOrElse(course.dayOfWeek - 1) { "未知" }}", "detail_day")
                                DetailLine("节次：第${course.startSection}–${course.endSection}节", "detail_sections")

                                val time = if (detail.startTime != null && detail.endTime != null) {
                                    "${detail.startTime.format(detailTimeFormat)}–${detail.endTime.format(detailTimeFormat)}"
                                } else {
                                    val first = ScheduleDisplayDefaults.forUnconfiguredSection(course.startSection)
                                    val last = ScheduleDisplayDefaults.forUnconfiguredSection(course.endSection)
                                    if (first != null && last != null)
                                        "${first.start.format(detailTimeFormat)}–${last.end.format(detailTimeFormat)}（默认节次时间）"
                                    else "未配置节次时间"
                                }
                                DetailLine("时间：$time", "detail_time")
                            }
                        }

                        // 教学与周次卡片
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DetailLine("教师：${course.teacher?.takeIf(String::isNotBlank) ?: "未提供"}", "detail_teacher")
                                DetailLine("周次：${course.weeks.sorted().joinToString("、")}", "detail_weeks")
                                DetailLine("本地学期：${detail.semesterDisplayName}", "detail_semester")
                            }
                        }

                        // 来源归属卡片
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (val origin = course.origin) {
                                    is CourseOrigin.SchoolImport -> {
                                        val school = if (origin.scope.schoolId.equals("fjnu", ignoreCase = true)) "福建师范大学"
                                            else origin.scope.schoolId
                                        DetailLine("学校：$school", "detail_school")
                                        DetailLine("来源学期：${state.sourceTermLabel ?: origin.scope.sourceTermId}", "detail_source_term")
                                    }
                                    CourseOrigin.Manual -> DetailLine("来源：手动课程", "detail_source")
                                    CourseOrigin.Legacy -> DetailLine("来源：历史课程", "detail_source")
                                }
                            }
                        }

                        // 待办与日程卡片
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().testTag("detail_tasks_section"),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "待办与日程",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        val uncompletedCount = state.tasks.count { !it.isCompleted }
                                        if (uncompletedCount > 0) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                            ) {
                                                Text(
                                                    "$uncompletedCount 待办",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = { showAddTaskDialog = true },
                                        modifier = Modifier.testTag("detail_add_task"),
                                    ) {
                                        Text("+ 添加", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (state.tasks.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().testTag("detail_tasks_empty"),
                                    ) {
                                        Text(
                                            "暂无课程待办或考试，点击右上角添加",
                                            modifier = Modifier.padding(14.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.tasks.forEach { task ->
                                            TaskItemRow(
                                                task = task,
                                                onToggle = { isChecked -> onToggleTaskCompletion(task.id, isChecked) },
                                                onDelete = { onDeleteTask(task.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state is CourseDetailUiState.Ready) {
                val course = state.detail.arrangement
                when (course.origin) {
                    CourseOrigin.Manual -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onEditCourse(course) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_edit_course"),
                            ) {
                                Text("编辑课程", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_delete_course"),
                            ) {
                                Text("删除课程", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    is CourseOrigin.SchoolImport -> {
                        FilledTonalButton(
                            onClick = { onCopyCourse(course) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .testTag("detail_copy_course"),
                        ) {
                            Text("复制为手动课程", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    CourseOrigin.Legacy -> Unit
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("删除课程") },
                        text = { Text("确定要删除课程“${course.name}”吗？此操作无法撤销。") },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteDialog = false },
                                modifier = Modifier.testTag("delete_course_dialog_cancel"),
                            ) {
                                Text("取消")
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteDialog = false
                                    onDeleteCourse(course.id)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.testTag("delete_course_dialog_confirm"),
                            ) {
                                Text("确认删除")
                            }
                        },
                        modifier = Modifier.testTag("delete_course_dialog"),
                    )
                }
                if (showAddTaskDialog) {
                    CourseTaskDialog(
                        courseName = course.name,
                        onDismiss = { showAddTaskDialog = false },
                        onConfirm = { title, content, deadline, type ->
                            onCreateTask(title, content, deadline, type)
                            showAddTaskDialog = false
                        },
                    )
                }
            }

            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("detail_close"),
            ) {
                Text("关闭", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailLine(text: String, tag: String, isHighlight: Boolean = false) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isHighlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        style = if (isHighlight) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun TaskItemRow(
    task: CourseTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val (deadlineText, isUrgent) = remember(task.deadlineEpochMillis, task.isCompleted) {
        formatTaskDeadline(task.deadlineEpochMillis, task.isCompleted)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (task.isCompleted) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (isUrgent) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}")
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("task_checkbox_${task.id}"),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val (typeLabel, typeBgColor, typeTextColor) = when (task.type) {
                        CourseTaskType.HOMEWORK -> Triple("作业", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        CourseTaskType.EXAM -> Triple("考试", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                        CourseTaskType.EXPERIMENT -> Triple("实验", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        CourseTaskType.CUSTOM -> Triple("其他", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeBgColor,
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = typeTextColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("task_title_${task.id}")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = deadlineText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (!task.content.isNullOrBlank()) {
                        Text(
                            text = task.content,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("task_delete_${task.id}")
            ) {
                Text(
                    "删除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun formatTaskDeadline(deadlineEpochMillis: Long, isCompleted: Boolean): Pair<String, Boolean> {
    val deadline = Instant.ofEpochMilli(deadlineEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val deadlineDate = deadline.toLocalDate()
    val timeStr = deadline.format(DateTimeFormatter.ofPattern("HH:mm"))

    val daysDiff = ChronoUnit.DAYS.between(today, deadlineDate)
    val isPast = deadline.isBefore(now)
    val isUrgent = !isCompleted && isPast

    val text = when {
        isPast && !isCompleted -> "已逾期 · ${deadline.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}"
        daysDiff == 0L -> "今天 $timeStr 截止"
        daysDiff == 1L -> "明天 $timeStr 截止"
        daysDiff == 2L -> "后天 $timeStr 截止"
        daysDiff in 3..7 -> "${daysDiff}天后 $timeStr 截止"
        else -> "${deadline.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} 截止"
    }
    return Pair(text, isUrgent)
}
