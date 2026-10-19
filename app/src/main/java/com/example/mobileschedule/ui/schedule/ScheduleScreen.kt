package com.example.mobileschedule.ui.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.R
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.ArrangementDraft
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.ui.theme.CourseColorPalette
import com.example.mobileschedule.ui.theme.CourseColorTheme
import com.example.mobileschedule.ui.theme.LocalIsDarkTheme
import android.widget.Toast
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.mobileschedule.data.rules.CourseSlotState
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val sectionWidth = 40.dp
private val sectionHeight = 72.dp

internal data class DisplayedDay(
    val dayOfWeek: Int,
    val label: String,
    val date: LocalDate,
    val columnIndex: Int,
)

internal fun calculateDisplayedDays(
    firstWeekMonday: LocalDate,
    weekNum: Int,
    showWeekend: Boolean,
    firstDayOfWeek: java.time.DayOfWeek,
): List<DisplayedDay> {
    val monday = firstWeekMonday.plusWeeks((weekNum - 1).toLong())
    return if (!showWeekend) {
        listOf(
            DisplayedDay(1, "周一", monday, 0),
            DisplayedDay(2, "周二", monday.plusDays(1), 1),
            DisplayedDay(3, "周三", monday.plusDays(2), 2),
            DisplayedDay(4, "周四", monday.plusDays(3), 3),
            DisplayedDay(5, "周五", monday.plusDays(4), 4),
        )
    } else if (firstDayOfWeek == java.time.DayOfWeek.SUNDAY) {
        val sundayDate = monday.minusDays(1)
        listOf(
            DisplayedDay(7, "周日", sundayDate, 0),
            DisplayedDay(1, "周一", monday, 1),
            DisplayedDay(2, "周二", monday.plusDays(1), 2),
            DisplayedDay(3, "周三", monday.plusDays(2), 3),
            DisplayedDay(4, "周四", monday.plusDays(3), 4),
            DisplayedDay(5, "周五", monday.plusDays(4), 5),
            DisplayedDay(6, "周六", monday.plusDays(5), 6),
        )
    } else {
        listOf(
            DisplayedDay(1, "周一", monday, 0),
            DisplayedDay(2, "周二", monday.plusDays(1), 1),
            DisplayedDay(3, "周三", monday.plusDays(2), 2),
            DisplayedDay(4, "周四", monday.plusDays(3), 3),
            DisplayedDay(5, "周五", monday.plusDays(4), 4),
            DisplayedDay(6, "周六", monday.plusDays(5), 5),
            DisplayedDay(7, "周日", monday.plusDays(6), 6),
        )
    }
}

@Composable
fun ScheduleRoute(
    onConfigure: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    initialArrangementId: Long? = null,
    onClearInitialArrangementId: () -> Unit = {},
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val displayStore = remember(context) { ScheduleDisplaySettingsStore(context) }
    var displayOptions by remember(displayStore) { mutableStateOf(displayStore.read()) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    LaunchedEffect(initialArrangementId) {
        if (initialArrangementId != null && initialArrangementId > 0) {
            viewModel.showCourseDetail(initialArrangementId)
            onClearInitialArrangementId()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
        displayOptions = displayStore.read()
    }
    ScheduleScreen(
        state = state,
        detailState = detailState,
        formState = formState,
        displayOptions = displayOptions,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onSelectWeek = viewModel::selectWeek,
        onReturnToCurrentWeek = viewModel::returnToCurrentWeek,
        onCourseClick = viewModel::showCourseDetail,
        onDismissDetail = viewModel::closeCourseDetail,
        onAddCourse = viewModel::openCreateCourse,
        onEditCourse = viewModel::openEditCourse,
        onCopyCourse = viewModel::openCopyCourse,
        onDeleteCourse = { id -> viewModel.deleteCourse(id) },
        onDismissForm = viewModel::closeCourseForm,
        onSaveCourse = { draft, colorIndex -> viewModel.saveCourse(draft, colorIndex) },
        onCheckConflict = { draft, excludingId -> viewModel.checkConflict(draft, excludingId) },
        onConfigure = onConfigure,
        onSettings = onSettings,
        onImport = onImport,
        onRetry = viewModel::retry,
    )
}

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    detailState: CourseDetailUiState = CourseDetailUiState.Closed,
    formState: CourseFormState = CourseFormState.Closed,
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onSelectWeek: (Int) -> Unit = {},
    onReturnToCurrentWeek: () -> Unit = {},
    onCourseClick: (Long) -> Unit = {},
    onDismissDetail: () -> Unit = {},
    onAddCourse: () -> Unit = {},
    onEditCourse: (CourseArrangement) -> Unit = {},
    onCopyCourse: (CourseArrangement) -> Unit = {},
    onDeleteCourse: (Long) -> Unit = {},
    onDismissForm: () -> Unit = {},
    onSaveCourse: (ArrangementDraft, Int?) -> Unit = { _, _ -> },
    onCheckConflict: suspend (ArrangementDraft, Long?) -> List<CourseArrangement> = { _, _ -> emptyList() },
    onConfigure: () -> Unit = {},
    onSettings: () -> Unit = {},
    onImport: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        ScheduleUiState.Loading -> Column(
            Modifier.fillMaxSize().testTag("schedule_loading"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text(
                "正在读取本地课表…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        ScheduleUiState.NoSemester -> ScheduleStatusPage(
            title = stringResource(R.string.schedule_empty_title),
            description = stringResource(R.string.schedule_empty_body),
            tag = "schedule_empty", primaryLabel = "配置学期", primaryTag = "schedule_configure",
            onPrimary = onConfigure, secondaryLabel = "导入课程表", secondaryTag = "schedule_import",
            onSecondary = onImport,
        )
        ScheduleUiState.ConfigRequired -> ScheduleStatusPage(
            title = stringResource(R.string.schedule_config_title),
            description = stringResource(R.string.schedule_config_body),
            tag = "schedule_config_required", primaryLabel = "完善学期配置", primaryTag = "schedule_settings",
            onPrimary = onSettings, secondaryLabel = "导入课程表", secondaryTag = "schedule_import",
            onSecondary = onImport,
        )
        ScheduleUiState.Error -> ScheduleStatusPage(
            title = stringResource(R.string.schedule_error_title),
            description = stringResource(R.string.schedule_error_body),
            tag = "schedule_error", primaryLabel = "重试读取", primaryTag = "schedule_retry",
            onPrimary = onRetry, secondaryLabel = "打开设置", secondaryTag = "schedule_settings",
            onSecondary = onSettings,
        )
        is ScheduleUiState.Ready -> {
            var showWeekPicker by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                ScheduleSemesterHeader(
                    state = state,
                    showWeekend = displayOptions.showWeekend,
                    onImport = onImport,
                    onAddCourse = onAddCourse,
                )
                Box(Modifier.weight(1f)) {
                    WeekScheduleGrid(
                        state.week, state.today, onCourseClick,
                        displayOptions = displayOptions,
                        onWeekTitleClick = { showWeekPicker = true },
                        onPreviousWeek = onPreviousWeek, onNextWeek = onNextWeek,
                    )
                }
                ScheduleWeekControls(state, onPreviousWeek, onNextWeek, onReturnToCurrentWeek)
            }
            if (showWeekPicker) WeekPickerDialog(state, onSelectWeek) { showWeekPicker = false }
        }
    }
    CourseDetailSheet(
        state = detailState,
        currentWeek = (state as? ScheduleUiState.Ready)?.week?.schedule?.week,
        onDismiss = onDismissDetail,
        onEditCourse = onEditCourse,
        onDeleteCourse = onDeleteCourse,
        onCopyCourse = onCopyCourse,
    )
    CourseFormSheet(
        formState = formState,
        onDismiss = onDismissForm,
        onSave = onSaveCourse,
        onCheckConflict = onCheckConflict,
    )
}

@Composable
private fun ScheduleStatusPage(
    title: String, description: String, tag: String,
    primaryLabel: String, primaryTag: String, onPrimary: () -> Unit,
    secondaryLabel: String, secondaryTag: String, onSecondary: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth().testTag(primaryTag),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth().testTag(secondaryTag),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(secondaryLabel, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** All seven days share the available width; horizontal drags change weeks synchronously with finger, vertical drags scroll sections. */
@Composable
fun WeekScheduleGrid(
    week: ActiveWeek,
    today: LocalDate = LocalDate.now(),
    onCourseClick: (Long) -> Unit = {},
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
    onWeekTitleClick: () -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
) {
    val config = requireNotNull(week.semester.config)
    val schedule = week.schedule
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    val rulerWidth = (sectionWidth * fontScale.coerceAtMost(1.15f)).coerceAtMost(46.dp)
    val vertical = rememberScrollState()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var displayedWeek by remember(week.semester.id) { mutableIntStateOf(schedule.week) }
    var isTransitioning by remember { mutableStateOf(false) }

    LaunchedEffect(schedule.week) {
        displayedWeek = schedule.week
        if (dragOffset.value != 0f) {
            dragOffset.snapTo(0f)
        }
    }

    val arrangementCache = remember(week.semester.id) { mutableStateMapOf<Long, CourseArrangement>() }
    LaunchedEffect(schedule) {
        val all = if (schedule.allArrangements.isNotEmpty()) schedule.allArrangements else schedule.arrangements
        all.forEach { arrangement ->
            arrangementCache[arrangement.id] = arrangement
        }
    }

    var overlappingCourses by remember { mutableStateOf<List<com.example.mobileschedule.data.model.CourseArrangement>?>(null) }

    fun getWeekSchedule(weekNum: Int): WeekSchedule {
        val monday = config.firstWeekMonday.plusWeeks((weekNum - 1).toLong())
        val allArrangements = if (schedule.allArrangements.isNotEmpty()) {
            schedule.allArrangements
        } else if (arrangementCache.isNotEmpty()) {
            arrangementCache.values.toList()
        } else {
            schedule.arrangements
        }
        val arrangements = if (weekNum == schedule.week) schedule.arrangements else allArrangements.filter { weekNum in it.weeks }
        return WeekSchedule(
            semesterId = week.semester.id,
            week = weekNum,
            monday = monday,
            sunday = monday.plusDays(6),
            arrangements = arrangements,
            allArrangements = allArrangements,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("week_grid")) {
        val headerHeight = (44.dp * fontScale).coerceAtMost(78.dp)
        val availableBodyHeight = (maxHeight - headerHeight).coerceAtLeast(0.dp)
        val rowHeight = (availableBodyHeight / config.totalSections)
            .coerceIn(52.dp * fontScale, sectionHeight * fontScale)
        val dayCount = if (displayOptions.showWeekend) 7 else 5
        val dayWidth = (maxWidth - rulerWidth) / dayCount
        val gridWidth = dayWidth * dayCount
        val gridHeight = rowHeight * config.totalSections
        val compactHeader = dayWidth < 45.dp || fontScale > 1.3f
        val gridWidthPx = with(density) { gridWidth.toPx() }
        val hasWeekendCourses = getWeekSchedule(displayedWeek).arrangements.any { it.dayOfWeek in 6..7 }

        Column(
            Modifier.fillMaxSize()
                .pointerInput(displayedWeek, config.totalWeeks) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!isTransitioning) {
                                totalDrag = 0f
                            }
                        },
                        onDragEnd = {
                            if (isTransitioning) return@detectHorizontalDragGestures
                            val threshold = (gridWidthPx * 0.18f).coerceAtLeast(swipeThresholdPx)
                            if (totalDrag <= -threshold && displayedWeek < config.totalWeeks) {
                                isTransitioning = true
                                coroutineScope.launch {
                                    try {
                                        val nextWeek = displayedWeek + 1
                                        dragOffset.animateTo(-gridWidthPx, tween(200, easing = FastOutSlowInEasing))
                                        displayedWeek = nextWeek
                                        dragOffset.snapTo(0f)
                                        onNextWeek()
                                    } finally {
                                        isTransitioning = false
                                    }
                                }
                            } else if (totalDrag >= threshold && displayedWeek > 1) {
                                isTransitioning = true
                                coroutineScope.launch {
                                    try {
                                        val prevWeek = displayedWeek - 1
                                        dragOffset.animateTo(gridWidthPx, tween(200, easing = FastOutSlowInEasing))
                                        displayedWeek = prevWeek
                                        dragOffset.snapTo(0f)
                                        onPreviousWeek()
                                    } finally {
                                        isTransitioning = false
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))
                                }
                            }
                        },
                        onDragCancel = {
                            if (!isTransitioning) {
                                coroutineScope.launch {
                                    dragOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isTransitioning) return@detectHorizontalDragGestures
                            change.consume()
                            totalDrag += dragAmount
                            val canGoLeft = displayedWeek < config.totalWeeks
                            val canGoRight = displayedWeek > 1
                            val clamped = when {
                                totalDrag < 0 && !canGoLeft -> totalDrag * 0.35f
                                totalDrag > 0 && !canGoRight -> totalDrag * 0.35f
                                else -> totalDrag.coerceIn(-gridWidthPx, gridWidthPx)
                            }
                            coroutineScope.launch {
                                dragOffset.snapTo(clamped)
                            }
                        }
                    )
                }
        ) {
            // Header row containing week title button and seven days
            Row(
                Modifier.fillMaxWidth().height(headerHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(rulerWidth)) {
                    TextButton(
                        onClick = onWeekTitleClick,
                        modifier = Modifier.fillMaxWidth().testTag("week_title"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        AnimatedContent(
                            targetState = displayedWeek,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220)))
                                    .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.15f, animationSpec = tween(180)))
                            },
                            label = "week_title_transition",
                        ) { currentWeek ->
                            Text(
                                "${currentWeek}\n周",
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                            )
                        }
                    }
                    if (!displayOptions.showWeekend && hasWeekendCourses) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp)
                                .background(
                                    MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                .testTag("weekend_course_badge"),
                        ) {
                            Text(
                                "末",
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 10.sp,
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    val offset = dragOffset.value
                    // 当前周日期
                    WeekHeaderRow(
                        schedule = getWeekSchedule(displayedWeek),
                        firstWeekMonday = config.firstWeekMonday,
                        today = today,
                        dayWidth = dayWidth,
                        compactHeader = compactHeader,
                        displayOptions = displayOptions,
                        isCurrentPage = true,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { translationX = offset }
                            .testTag("week_header"),
                    )
                    // 向左拖动时右侧滑入的下一周日期
                    if (offset < 0f && displayedWeek < config.totalWeeks) {
                        WeekHeaderRow(
                            schedule = getWeekSchedule(displayedWeek + 1),
                            firstWeekMonday = config.firstWeekMonday,
                            today = today,
                            dayWidth = dayWidth,
                            compactHeader = compactHeader,
                            displayOptions = displayOptions,
                            isCurrentPage = false,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { translationX = offset + gridWidthPx },
                        )
                    }
                    // 向右拖动时左侧滑入的上一周日期
                    if (offset > 0f && displayedWeek > 1) {
                        WeekHeaderRow(
                            schedule = getWeekSchedule(displayedWeek - 1),
                            firstWeekMonday = config.firstWeekMonday,
                            today = today,
                            dayWidth = dayWidth,
                            compactHeader = compactHeader,
                            displayOptions = displayOptions,
                            isCurrentPage = false,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { translationX = offset - gridWidthPx },
                        )
                    }
                }
            }

            // Grid body
            Row(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(vertical).testTag("week_body_scroll"),
            ) {
                // Left ruler column
                Column(Modifier.width(rulerWidth)) {
                    repeat(config.totalSections) { index ->
                        val interval = ScheduleDisplayDefaults.forSection(config, index + 1)
                        Column(
                            Modifier.height(rowHeight).fillMaxWidth().padding(top = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "${index + 1}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            )
                            Text(
                                interval?.let { "${it.start.format(timeFormat)}\n${it.end.format(timeFormat)}" } ?: "—",
                                modifier = Modifier.testTag("section_time_${index + 1}"),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                            )
                        }
                    }
                }

                // Main grid area with real-time finger-synchronized drag gestures
                Box(
                    Modifier.weight(1f).height(gridHeight).clipToBounds()
                ) {
                    val offset = dragOffset.value

                    // 当前周网格及课程
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = offset }) {
                        WeekScheduleContent(
                            schedule = getWeekSchedule(displayedWeek),
                            today = today,
                            config = config,
                            dayWidth = dayWidth,
                            rowHeight = rowHeight,
                            gridWidth = gridWidth,
                            gridHeight = gridHeight,
                            displayOptions = displayOptions,
                            isCurrentPage = true,
                            onCourseClick = onCourseClick,
                            onOverlappingClick = { overlappingCourses = it },
                        )
                    }

                    // 向左拖动时右侧滑入的下一周网格及课程
                    if (offset < 0f && displayedWeek < config.totalWeeks) {
                        Box(Modifier.fillMaxSize().graphicsLayer { translationX = offset + gridWidthPx }) {
                            WeekScheduleContent(
                                schedule = getWeekSchedule(displayedWeek + 1),
                                today = today,
                                config = config,
                                dayWidth = dayWidth,
                                rowHeight = rowHeight,
                                gridWidth = gridWidth,
                                gridHeight = gridHeight,
                                displayOptions = displayOptions,
                                isCurrentPage = false,
                                onCourseClick = onCourseClick,
                                onOverlappingClick = { overlappingCourses = it },
                            )
                        }
                    }

                    // 向右拖动时左侧滑入的上一周网格及课程
                    if (offset > 0f && displayedWeek > 1) {
                        Box(Modifier.fillMaxSize().graphicsLayer { translationX = offset - gridWidthPx }) {
                            WeekScheduleContent(
                                schedule = getWeekSchedule(displayedWeek - 1),
                                today = today,
                                config = config,
                                dayWidth = dayWidth,
                                rowHeight = rowHeight,
                                gridWidth = gridWidth,
                                gridHeight = gridHeight,
                                displayOptions = displayOptions,
                                isCurrentPage = false,
                                onCourseClick = onCourseClick,
                                onOverlappingClick = { overlappingCourses = it },
                            )
                        }
                    }
                }
            }
        }
    }

    overlappingCourses?.let { courses ->
        AlertDialog(
            onDismissRequest = { overlappingCourses = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "同一时段有${courses.size}门课程",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(courses, key = { it.id }) { course ->
                        val courseSlot = ScheduleRules.determineCourseSlotState(displayedWeek, course.weeks)
                        Surface(
                            onClick = { overlappingCourses = null; onCourseClick(course.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().testTag("conflict_course_${course.id}"),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(course.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    if (courseSlot is CourseSlotState.INACTIVE) {
                                        Text(
                                            "非本周",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(start = 6.dp),
                                        )
                                    }
                                }
                                val loc = course.location ?: "地点未提供"
                                val extra = if (courseSlot is CourseSlotState.INACTIVE) " · ${courseSlot.notice}" else ""
                                Text(
                                    "$loc$extra",
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overlappingCourses = null }) {
                    Text("关闭", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun WeekHeaderRow(
    schedule: WeekSchedule,
    firstWeekMonday: LocalDate,
    today: LocalDate,
    dayWidth: Dp,
    compactHeader: Boolean,
    displayOptions: ScheduleDisplayOptions,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayedDays = remember(schedule.week, firstWeekMonday, displayOptions.showWeekend, displayOptions.firstDayOfWeek) {
        calculateDisplayedDays(
            firstWeekMonday = firstWeekMonday,
            weekNum = schedule.week,
            showWeekend = displayOptions.showWeekend,
            firstDayOfWeek = displayOptions.firstDayOfWeek,
        )
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayedDays.forEach { dayItem ->
            val label = if (compactHeader) dayItem.label.removePrefix("周") else dayItem.label
            val dateLabel = if (compactHeader) "${dayItem.date.dayOfMonth}"
                else "%02d-%02d".format(dayItem.date.monthValue, dayItem.date.dayOfMonth)
            val isToday = dayItem.date == today

            Text(
                "$label\n$dateLabel",
                Modifier.width(dayWidth)
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(vertical = 5.dp)
                    .then(if (isCurrentPage) Modifier.testTag("day_${dayItem.dayOfWeek}") else Modifier),
                textAlign = TextAlign.Center,
                maxLines = 2,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            )
        }
    }
}

@Composable
private fun WeekScheduleContent(
    schedule: WeekSchedule,
    today: LocalDate,
    config: SemesterConfig,
    dayWidth: Dp,
    rowHeight: Dp,
    gridWidth: Dp,
    gridHeight: Dp,
    displayOptions: ScheduleDisplayOptions,
    isCurrentPage: Boolean,
    onCourseClick: (Long) -> Unit,
    onOverlappingClick: (List<CourseArrangement>) -> Unit,
) {
    val context = LocalContext.current
    val displayedDays = remember(schedule.week, config.firstWeekMonday, displayOptions.showWeekend, displayOptions.firstDayOfWeek) {
        calculateDisplayedDays(
            firstWeekMonday = config.firstWeekMonday,
            weekNum = schedule.week,
            showWeekend = displayOptions.showWeekend,
            firstDayOfWeek = displayOptions.firstDayOfWeek,
        )
    }
    val todayColumnIndex = displayedDays.firstOrNull { it.date == today }?.columnIndex
    val columnCount = if (displayOptions.showWeekend) 7 else 5

    val coursesToDisplay = if (displayOptions.showNonCurrentWeekCourses) {
        schedule.allArrangements.ifEmpty { schedule.arrangements }
    } else {
        schedule.arrangements
    }

    Box(Modifier.width(gridWidth).height(gridHeight)) {
        WeekGridLines(gridWidth, gridHeight, dayWidth, rowHeight, config.totalSections, columnCount, todayColumnIndex)
        Box(Modifier.fillMaxSize()) {
            buildWeekGridSlots(coursesToDisplay, showWeekend = displayOptions.showWeekend, currentWeek = schedule.week).forEach { slot ->
                val dayItem = displayedDays.firstOrNull { it.dayOfWeek == slot.dayOfWeek }
                if (dayItem != null) {
                    if (slot.arrangements.size == 2 && dayWidth >= 136.dp) {
                        slot.arrangements.forEachIndexed { lane, course ->
                            WeekGridCard(
                                slot = WeekGridSlot(slot.dayOfWeek, course.startSection, course.endSection, listOf(course)),
                                currentWeek = schedule.week,
                                columnIndex = dayItem.columnIndex,
                                dayWidth = dayWidth,
                                rowHeight = rowHeight,
                                lane = lane,
                                laneCount = 2,
                                displayOptions = displayOptions,
                                enableTag = isCurrentPage,
                                onClick = {
                                    val state = ScheduleRules.determineCourseSlotState(schedule.week, course.weeks)
                                    if (state is CourseSlotState.INACTIVE) {
                                        Toast.makeText(context, "${course.name}：${state.notice}", Toast.LENGTH_SHORT).show()
                                    }
                                    onCourseClick(course.id)
                                },
                            )
                        }
                    } else {
                        WeekGridCard(
                            slot = slot,
                            currentWeek = schedule.week,
                            columnIndex = dayItem.columnIndex,
                            dayWidth = dayWidth,
                            rowHeight = rowHeight,
                            displayOptions = displayOptions,
                            enableTag = isCurrentPage,
                            onClick = {
                                val course = slot.arrangements.singleOrNull()
                                if (course != null) {
                                    val state = ScheduleRules.determineCourseSlotState(schedule.week, course.weeks)
                                    if (state is CourseSlotState.INACTIVE) {
                                        Toast.makeText(context, "${course.name}：${state.notice}", Toast.LENGTH_SHORT).show()
                                    }
                                    onCourseClick(course.id)
                                } else {
                                    onOverlappingClick(slot.arrangements)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (coursesToDisplay.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        "本周没有课程安排",
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .then(if (isCurrentPage) Modifier.testTag("schedule_week_empty") else Modifier),
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
private fun WeekGridLines(
    gridWidth: Dp,
    gridHeight: Dp,
    dayWidth: Dp,
    rowHeight: Dp,
    sectionCount: Int,
    columnCount: Int,
    todayIndex: Int?,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val todayColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
    Canvas(Modifier.width(gridWidth).height(gridHeight)) {
        if (todayIndex != null) {
            drawRect(
                todayColor,
                topLeft = Offset(dayWidth.toPx() * todayIndex, 0f),
                size = androidx.compose.ui.geometry.Size(dayWidth.toPx(), size.height),
            )
        }
        for (day in 0..columnCount) {
            val x = dayWidth.toPx() * day
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
        }
        for (section in 0..sectionCount) {
            val y = rowHeight.toPx() * section
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 0.8.dp.toPx())
        }
    }
}

@Composable
private fun WeekGridCard(
    slot: WeekGridSlot,
    columnIndex: Int,
    dayWidth: Dp,
    rowHeight: Dp,
    onClick: () -> Unit,
    lane: Int = 0,
    laneCount: Int = 1,
    currentWeek: Int = 1,
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
    enableTag: Boolean = true,
) {
    val course = slot.arrangements.singleOrNull()
    val isDark = LocalIsDarkTheme.current
    val isInactive = course != null && currentWeek !in course.weeks
    val inactiveState = if (isInactive) {
        ScheduleRules.determineCourseSlotState(currentWeek, course.weeks) as? CourseSlotState.INACTIVE
    } else null

    val palette = if (isInactive) {
        if (isDark) {
            CourseColorPalette(
                container = Color(0xFF2C2C2E),
                onContainer = Color(0xFFD1D5DB),
                secondaryText = Color(0xFF9CA3AF),
                accent = Color(0xFF6B7280),
            )
        } else {
            CourseColorPalette(
                container = Color(0xFFF3F4F6),
                onContainer = Color(0xFF374151),
                secondaryText = Color(0xFF6B7280),
                accent = Color(0xFF9CA3AF),
            )
        }
    } else if (course != null) {
        CourseColorTheme.forCourse(course.name, isDark)
    } else {
        CourseColorPalette(
            container = if (isDark) Color(0xFF3B1D0E) else Color(0xFFFFEDD5),
            onContainer = if (isDark) Color(0xFFFED7AA) else Color(0xFF9A3412),
            secondaryText = if (isDark) Color(0xFFFDBA74) else Color(0xFFC2410C),
            accent = Color(0xFFF97316),
        )
    }

    val tag = if (course != null) "course_${course.id}" else "overlap_${slot.dayOfWeek}_${slot.startSection}"
    val description = if (course == null) {
        "${weekdays[slot.dayOfWeek - 1]}，第${slot.startSection}至${slot.endSection}节，同一时段${slot.arrangements.size}门课程，点击查看列表"
    } else buildList {
        val weekNotice = if (isInactive && inactiveState != null) "（非本周，${inactiveState.weekDescription}上课）" else ""
        add("${weekdays[slot.dayOfWeek - 1]}，第${slot.startSection}至${slot.endSection}节，$weekNotice${course.name}")
        if (displayOptions.showLocation) add(course.location ?: "地点未提供")
        if (displayOptions.showTeacher) add(course.teacher ?: "教师未提供")
    }.joinToString("，")

    val borderColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val cardAlpha = if (isInactive) 0.45f else 1.0f

    val borderModifier = if (isInactive) {
        Modifier.drawWithContent {
            drawContent()
            val strokeWidth = 1.dp.toPx()
            val halfStroke = strokeWidth / 2f
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = strokeWidth, pathEffect = dashEffect),
            )
        }
    } else Modifier

    Card(
        modifier = Modifier
            .offset(
                x = dayWidth * columnIndex + dayWidth / laneCount * lane + 2.dp,
                y = rowHeight * (slot.startSection - 1) + 3.dp,
            )
            .width(dayWidth / laneCount - 4.dp)
            .height(rowHeight * slot.sectionSpan - 6.dp)
            .then(if (enableTag) Modifier.testTag(tag) else Modifier)
            .alpha(cardAlpha)
            .then(borderModifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.container,
            contentColor = palette.onContainer,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            if (course == null) {
                Text(
                    "此时段有${slot.arrangements.size}门课程",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = palette.onContainer,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                )
                Text(
                    "点击查看",
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                )
            } else {
                if (isInactive) {
                    Text(
                        "非本周",
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                        modifier = Modifier.testTag("inactive_badge_${course.id}"),
                    )
                }
                Text(
                    course.name,
                    maxLines = if (displayOptions.showFullCourseName) Int.MAX_VALUE else 3,
                    overflow = if (displayOptions.showFullCourseName) TextOverflow.Clip else TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onContainer,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                )
                if (displayOptions.showLocation) {
                    Text(
                        course.location ?: "地点未提供",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    )
                }
                if (displayOptions.showTeacher) {
                    Text(
                        course.teacher ?: "教师未提供",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    )
                }
                if (laneCount > 1) {
                    Text(
                        "时间重叠",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    )
                }
            }
        }
    }
}
