package com.example.mobileschedule.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.ui.schedule.CourseDetailSheet
import com.example.mobileschedule.ui.schedule.CourseDetailUiState
import com.example.mobileschedule.ui.schedule.CourseFormSheet
import com.example.mobileschedule.ui.schedule.CourseFormState
import com.example.mobileschedule.ui.theme.CourseColorStore
import com.example.mobileschedule.ui.theme.CourseColorTheme
import com.example.mobileschedule.ui.theme.LocalIsDarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun TodayRoute(
    onConfigure: (Long?) -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    TodayScreen(
        state = uiState,
        onCourseClick = { viewModel.showCourseDetail(it) },
        onConfigure = { onConfigure(null) },
        onSettings = onSettings,
        onImport = onImport,
    )

    // Course Detail Sheet
    val readyState = uiState as? TodayUiState.Ready
    val semesterConfig = (readyState?.schedule as? TodaySchedule.Ready)?.semester?.config

    CourseDetailSheet(
        state = detailState,
        onDismiss = { viewModel.closeCourseDetail() },
        onEditCourse = { arrangement ->
            if (semesterConfig != null) {
                viewModel.openEditCourse(arrangement, semesterConfig)
            }
        },
        onCopyCourse = { arrangement ->
            if (semesterConfig != null) {
                viewModel.openCopyCourse(arrangement, semesterConfig)
            }
        },
        onDeleteCourse = { arrangementId ->
            viewModel.deleteCourse(arrangementId)
        },
        onCreateTask = { title, content, deadline, type ->
            val courseId = (detailState as? CourseDetailUiState.Ready)?.detail?.arrangement?.id
            if (courseId != null) {
                viewModel.createTask(courseId, title, content, deadline, type)
            }
        },
        onToggleTaskCompletion = viewModel::toggleTaskCompletion,
        onDeleteTask = viewModel::deleteTask,
    )

    // Course Form Sheet for Editing or Copying
    CourseFormSheet(
        formState = formState,
        onDismiss = { viewModel.closeCourseForm() },
        onSave = { draft, colorIndex ->
            viewModel.saveCourse(draft, colorIndex)
        },
        onCheckConflict = { draft, excludingId ->
            var conflicts = emptyList<CourseArrangement>()
            scope.launch {
                conflicts = viewModel.checkConflict(draft, excludingId)
            }
            conflicts
        },
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onCourseClick: (Long) -> Unit = {},
    onConfigure: () -> Unit = {},
    onSettings: () -> Unit = {},
    onImport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_page"),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (state) {
            is TodayUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("today_loading"))
                }
            }
            is TodayUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is TodayUiState.Ready -> {
                TodayContent(
                    schedule = state.schedule,
                    currentTime = state.currentTime,
                    unfinishedTasksByCourse = state.unfinishedTasksByCourse,
                    onCourseClick = onCourseClick,
                    onConfigure = onConfigure,
                    onSettings = onSettings,
                    onImport = onImport,
                )
            }
        }
    }
}

@Composable
private fun TodayContent(
    schedule: TodaySchedule,
    currentTime: LocalTime,
    unfinishedTasksByCourse: Map<Long, List<CourseTask>> = emptyMap(),
    onCourseClick: (Long) -> Unit,
    onConfigure: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
    ) {
        when (schedule) {
            is TodaySchedule.NoActiveSemester -> {
                TodaySimpleHeader(LocalDate.now(), "未选择学期")
                TodayEmptyCard(
                    title = "欢迎使用课程表",
                    message = "还没有设置活动学期。请先创建新学期或导入课表，即可随时查看每日课程。",
                    buttonLabel = "前往设置",
                    onButtonClick = onSettings,
                    secondaryButtonLabel = "导入课表",
                    onSecondaryButtonClick = onImport,
                )
            }
            is TodaySchedule.ConfigRequired -> {
                TodaySimpleHeader(LocalDate.now(), schedule.semester.displayName)
                TodayEmptyCard(
                    title = "学期日历待配置",
                    message = "当前学期缺少首周日期与总周数配置，完成配置后即可生成今日课表。",
                    buttonLabel = "配置学期",
                    onButtonClick = onConfigure,
                )
            }
            is TodaySchedule.BeforeSemester -> {
                TodaySimpleHeader(schedule.today, schedule.semester.displayName)
                TodayEmptyCard(
                    title = "学期尚未开始",
                    message = "距离开学还有 ${schedule.daysUntilStart} 天（开学日期：${schedule.semester.config?.firstWeekMonday}）。好好享受充实美好的假期吧！",
                    badge = "开学倒计时 ${schedule.daysUntilStart} 天",
                )
            }
            is TodaySchedule.AfterSemester -> {
                TodaySimpleHeader(schedule.today, schedule.semester.displayName)
                TodayEmptyCard(
                    title = "本学期已结课",
                    message = "本学期所有教学周课程均已结束。祝你期末考试取得理想成绩，假期愉快！",
                    badge = "学期已完结",
                )
            }
            is TodaySchedule.Ready -> {
                TodayReadyScheduleView(
                    schedule = schedule,
                    currentTime = currentTime,
                    unfinishedTasksByCourse = unfinishedTasksByCourse,
                    onCourseClick = onCourseClick,
                )
            }
        }
    }
}

@Composable
private fun TodaySimpleHeader(date: LocalDate, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
            .testTag("today_header"),
    ) {
        val dateText = date.format(DateTimeFormatter.ofPattern("M月d日"))
        val dayChinese = LunarCalendarHelper.dayOfWeekChinese(date.dayOfWeek.value)
        val lunar = LunarCalendarHelper.getLunarDateString(date)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$dateText $dayChinese",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("today_date"),
            )
            if (lunar.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.testTag("today_lunar_badge"),
                ) {
                    Text(
                        text = lunar,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("today_week_badge"),
        )
    }
}

@Composable
private fun TodayReadyScheduleView(
    schedule: TodaySchedule.Ready,
    currentTime: LocalTime,
    unfinishedTasksByCourse: Map<Long, List<CourseTask>> = emptyMap(),
    onCourseClick: (Long) -> Unit,
) {
    val dateText = schedule.date.format(DateTimeFormatter.ofPattern("M月d日"))
    val dayChinese = LunarCalendarHelper.dayOfWeekChinese(schedule.dayOfWeek)
    val lunar = LunarCalendarHelper.getLunarDateString(schedule.date)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
            .testTag("today_header"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$dateText $dayChinese",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("today_date"),
            )
            if (lunar.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.testTag("today_lunar_badge"),
                ) {
                    Text(
                        text = lunar,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "第 ${schedule.currentWeek} 周",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = schedule.semester.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("today_week_badge"),
            )
        }
    }

    if (schedule.courses.isEmpty()) {
        TodayEmptyCard(
            title = "今天没有课",
            message = if (schedule.dayOfWeek in 6..7) {
                "惬意的周末时光，好好放松休息一下，为新的一周积蓄能量吧！"
            } else {
                "今天全天没有排课，给自己充充电，去图书馆看看书或者运动一下吧~"
            },
            badge = "轻松无课日",
        )
    } else {
        // Summary banner for ongoing / upcoming class
        TodayStatusSummaryBanner(schedule.courses, currentTime)

        // Course list timeline
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("today_course_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(schedule.courses, key = { it.arrangement.id }) { item ->
                TodayCourseCard(
                    item = item,
                    currentTime = currentTime,
                    tasks = unfinishedTasksByCourse[item.arrangement.id] ?: emptyList(),
                    onClick = { onCourseClick(item.arrangement.id) },
                )
            }
        }
    }
}

@Composable
private fun TodayStatusSummaryBanner(courses: List<TodayCourseItem>, currentTime: LocalTime) {
    val currentCourse = courses.firstOrNull { it.status == CourseStatus.CURRENT }
    val nextCourse = courses.firstOrNull { it.status == CourseStatus.UPCOMING && it.isNext }

    val bannerModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .testTag("today_summary_banner")

    when {
        currentCourse != null -> {
            val minutesLeft = currentCourse.endTime?.let {
                ChronoUnit.MINUTES.between(currentTime, it).coerceAtLeast(0)
            } ?: 0
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = bannerModifier,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "正在上课：${currentCourse.arrangement.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "距离下课还有 $minutesLeft 分钟（${currentCourse.endTime ?: ""} 下课）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
        nextCourse != null -> {
            val minutesUntilStart = nextCourse.startTime?.let {
                ChronoUnit.MINUTES.between(currentTime, it).coerceAtLeast(0)
            } ?: 0
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = bannerModifier,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "下一节课：${nextCourse.arrangement.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        val timeStr = if (minutesUntilStart <= 60) "还有 $minutesUntilStart 分钟开始" else "预计 ${nextCourse.startTime} 开始"
                        Text(
                            text = "${nextCourse.startTime ?: ""} 开始 · $timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
        courses.all { it.status == CourseStatus.PAST } -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = bannerModifier,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎉", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "今日课程已全部结束，辛苦啦！",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCourseCard(
    item: TodayCourseItem,
    currentTime: LocalTime,
    tasks: List<CourseTask> = emptyList(),
    onClick: () -> Unit,
) {
    val arrangement = item.arrangement
    val isDark = LocalIsDarkTheme.current
    val paletteSize = CourseColorTheme.PaletteNames.size
    val colorIndex = CourseColorStore.getColorIndex(arrangement.name)
        ?: ((arrangement.name.hashCode() % paletteSize + paletteSize) % paletteSize)
    val palette = CourseColorTheme.palette(colorIndex, isDark)

    val cardBorder = when (item.status) {
        CourseStatus.CURRENT -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        CourseStatus.UPCOMING -> if (item.isNext) BorderStroke(1.5.dp, palette.accent) else null
        CourseStatus.PAST -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

    val containerColor = when (item.status) {
        CourseStatus.PAST -> palette.container.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.surface)
        else -> palette.container
    }

    val cardElevation = when (item.status) {
        CourseStatus.CURRENT -> 3.dp
        CourseStatus.UPCOMING -> 1.dp
        CourseStatus.PAST -> 0.dp
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("today_course_card_${arrangement.id}"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row: Section time & Status tag
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Section and time display
                val timeSpan = if (item.startTime != null && item.endTime != null) {
                    "第 ${arrangement.startSection}-${arrangement.endSection} 节 · ${item.startTime} - ${item.endTime}"
                } else {
                    "第 ${arrangement.startSection}-${arrangement.endSection} 节"
                }
                Text(
                    text = timeSpan,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.status == CourseStatus.PAST) palette.accent.copy(alpha = 0.7f) else palette.accent,
                    modifier = Modifier.testTag("today_course_time_${arrangement.id}"),
                )

                // Status Badge
                TodayStatusBadge(item, currentTime, palette.accent)
            }

            // Course Name
            Text(
                text = arrangement.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.status == CourseStatus.PAST) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("today_course_name_${arrangement.id}"),
            )

            // Location & Teacher row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!arrangement.location.isNullOrBlank()) {
                        Text(
                            text = "📍 ${arrangement.location}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("today_course_location_${arrangement.id}"),
                        )
                    }
                    if (!arrangement.teacher.isNullOrBlank()) {
                        Text(
                            text = "👤 ${arrangement.teacher}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("today_course_teacher_${arrangement.id}"),
                        )
                    }
                }

                // Origin tag
                val originLabel = when (arrangement.origin) {
                    is CourseOrigin.Manual -> "手动"
                    is CourseOrigin.SchoolImport -> "教务导入"
                    is CourseOrigin.Legacy -> "已归档"
                }
                Text(
                    text = originLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            // Pending task deadline notice
            val urgentTask = tasks.filter { !it.isCompleted }.minByOrNull { it.deadlineEpochMillis }
            if (urgentTask != null) {
                val (taskNotice, isUrgent) = formatTodayTaskNotice(urgentTask)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isUrgent) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_course_task_badge_${arrangement.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val iconText = when (urgentTask.type) {
                            CourseTaskType.HOMEWORK -> "📝"
                            CourseTaskType.EXAM -> "🚨"
                            CourseTaskType.EXPERIMENT -> "🧪"
                            CourseTaskType.CUSTOM -> "📌"
                        }
                        Text(text = iconText, fontSize = 12.sp)
                        Text(
                            text = taskNotice,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUrgent) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("today_course_task_text_${arrangement.id}"),
                        )
                    }
                }
            }
        }
    }
}

private fun formatTodayTaskNotice(task: CourseTask): Pair<String, Boolean> {
    val deadline = Instant.ofEpochMilli(task.deadlineEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val deadlineDate = deadline.toLocalDate()
    val daysDiff = ChronoUnit.DAYS.between(today, deadlineDate)
    val isPast = deadline.isBefore(now)
    val timeStr = deadline.format(DateTimeFormatter.ofPattern("HH:mm"))

    val text = when {
        isPast -> "${task.title} · 已逾期"
        daysDiff == 0L -> "${task.title} · 今天 $timeStr 截止"
        daysDiff == 1L -> "${task.title} · 明天截止"
        daysDiff == 2L -> "${task.title} · 后天截止"
        daysDiff in 3..7 -> "${task.title} · ${daysDiff}天后截止"
        else -> "${task.title} · ${deadline.format(DateTimeFormatter.ofPattern("M月d日"))}截止"
    }
    return Pair(text, isPast || daysDiff <= 1L)
}

@Composable
private fun TodayStatusBadge(item: TodayCourseItem, currentTime: LocalTime, accentColor: Color) {
    val tagModifier = Modifier.testTag("today_course_status_${item.arrangement.id}")

    when (item.status) {
        CourseStatus.CURRENT -> {
            val minutesLeft = item.endTime?.let {
                ChronoUnit.MINUTES.between(currentTime, it).coerceAtLeast(0)
            } ?: 0
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = tagModifier,
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "● 正在上课",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = "剩 ${minutesLeft}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                }
            }
        }
        CourseStatus.UPCOMING -> {
            if (item.isNext) {
                val minutesUntilStart = item.startTime?.let {
                    ChronoUnit.MINUTES.between(currentTime, it).coerceAtLeast(0)
                } ?: 0
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    modifier = tagModifier,
                ) {
                    Text(
                        text = if (minutesUntilStart <= 60) "下一节 · 剩 ${minutesUntilStart}m" else "下一节",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    modifier = tagModifier,
                ) {
                    Text(
                        text = item.startTime?.let { "$it 开始" } ?: "未开始",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        CourseStatus.PAST -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = tagModifier,
            ) {
                Text(
                    text = "已下课",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayEmptyCard(
    title: String,
    message: String,
    badge: String? = null,
    buttonLabel: String? = null,
    onButtonClick: () -> Unit = {},
    secondaryButtonLabel: String? = null,
    onSecondaryButtonClick: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("today_empty"),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("today_empty_title"),
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("today_empty_message"),
                )

                if (buttonLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onButtonClick,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(buttonLabel)
                        }
                        if (secondaryButtonLabel != null) {
                            OutlinedButton(
                                onClick = onSecondaryButtonClick,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(secondaryButtonLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}
