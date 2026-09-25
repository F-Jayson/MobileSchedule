package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mobileschedule.data.model.CourseOrigin
import java.time.format.DateTimeFormatter

private val detailTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val detailWeekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseDetailSheet(state: CourseDetailUiState, onDismiss: () -> Unit) {
    if (state == CourseDetailUiState.Closed) return
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("course_detail")) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            when (state) {
                CourseDetailUiState.Closed -> Unit
                CourseDetailUiState.Loading -> Text("正在读取课程详情…", modifier = Modifier.testTag("detail_loading"))
                CourseDetailUiState.Missing -> Text("这条课程安排已不存在，可能已被重新导入的课表替换。",
                    modifier = Modifier.testTag("detail_missing"))
                CourseDetailUiState.Error -> Text("暂时无法读取课程详情，请关闭后重试。",
                    modifier = Modifier.testTag("detail_error"))
                is CourseDetailUiState.Ready -> {
                    val detail = state.detail
                    val course = detail.arrangement
                    Text(course.name, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp).testTag("detail_name"))
                    DetailLine("教师：${course.teacher?.takeIf(String::isNotBlank) ?: "未提供"}", "detail_teacher")
                    DetailLine("地点：${course.location?.takeIf(String::isNotBlank) ?: "未提供"}", "detail_location")
                    DetailLine("星期：${detailWeekdays.getOrElse(course.dayOfWeek - 1) { "未知" }}", "detail_day")
                    DetailLine("节次：第${course.startSection}–${course.endSection}节", "detail_sections")
                    val time = if (detail.startTime != null && detail.endTime != null) {
                        "${detail.startTime.format(detailTimeFormat)}–${detail.endTime.format(detailTimeFormat)}"
                    } else "未配置节次时间"
                    DetailLine("时间：$time", "detail_time")
                    DetailLine("周次：${course.weeks.sorted().joinToString("、")}", "detail_weeks")
                    DetailLine("本地学期：${detail.semesterDisplayName}", "detail_semester")
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
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("detail_close")) { Text("关闭") }
        }
    }
}

@Composable
private fun DetailLine(text: String, tag: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag(tag),
        style = MaterialTheme.typography.bodyLarge)
}
