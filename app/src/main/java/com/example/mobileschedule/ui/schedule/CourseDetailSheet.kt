package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.ui.theme.CourseColorTheme
import java.time.format.DateTimeFormatter

private val detailTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val detailWeekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseDetailSheet(
    state: CourseDetailUiState,
    onDismiss: () -> Unit,
    onEditCourse: (CourseArrangement) -> Unit = {},
    onDeleteCourse: (Long) -> Unit = {},
    onCopyCourse: (CourseArrangement) -> Unit = {},
) {
    if (state == CourseDetailUiState.Closed) return
    var showDeleteDialog by remember { mutableStateOf(false) }
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
                        val isDark = isSystemInDarkTheme()
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
