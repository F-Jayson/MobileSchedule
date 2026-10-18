package com.example.mobileschedule.data.rules

import com.example.mobileschedule.data.model.ArrangementDraft
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.RuleField
import com.example.mobileschedule.data.model.RuleIssue
import com.example.mobileschedule.data.model.RuleIssueCode
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.data.model.CourseStatus
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.TodayCourseItem
import com.example.mobileschedule.data.model.TodaySchedule
import com.example.mobileschedule.data.model.WidgetCourseItem
import com.example.mobileschedule.data.model.WidgetScheduleData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Single source of calendar and range rules for persistence, importing and the UI. */
object ScheduleRules {
    fun validateConfig(draft: SemesterConfigDraft): List<RuleIssue> = buildList {
        if (draft.displayName.isBlank()) add(RuleIssue(RuleField.DISPLAY_NAME, RuleIssueCode.DISPLAY_NAME_REQUIRED))
        if (draft.firstWeekMonday.dayOfWeek != DayOfWeek.MONDAY) {
            add(RuleIssue(RuleField.FIRST_WEEK_MONDAY, RuleIssueCode.FIRST_DAY_NOT_MONDAY))
        }
        if (draft.totalWeeks !in 1..60) add(RuleIssue(RuleField.TOTAL_WEEKS, RuleIssueCode.TOTAL_WEEKS_OUT_OF_RANGE))
        if (draft.totalSections !in 1..99) add(RuleIssue(RuleField.TOTAL_SECTIONS, RuleIssueCode.TOTAL_SECTIONS_OUT_OF_RANGE))

        val times = draft.sectionTimes
        if (times.isNotEmpty()) {
            if (times.size != draft.totalSections) add(RuleIssue(RuleField.SECTION_TIMES, RuleIssueCode.TIMES_INCOMPLETE))
            var previousEnd: LocalTime? = null
            for ((index, time) in times.withIndex()) {
                if (time.section != index + 1 || time.section !in 1..draft.totalSections) {
                    add(RuleIssue(RuleField.SECTION_TIMES, RuleIssueCode.SECTION_ORDER_INVALID, time.section))
                }
                if (time.start.second != 0 || time.start.nano != 0 || time.end.second != 0 || time.end.nano != 0) {
                    add(RuleIssue(RuleField.SECTION_TIMES, RuleIssueCode.TIME_NOT_MINUTE_PRECISE, time.section))
                }
                if (!time.start.isBefore(time.end)) {
                    add(RuleIssue(RuleField.SECTION_TIMES, RuleIssueCode.TIME_RANGE_INVALID, time.section))
                }
                if (previousEnd != null && time.start.isBefore(previousEnd)) {
                    add(RuleIssue(RuleField.SECTION_TIMES, RuleIssueCode.TIME_OVERLAP, time.section))
                }
                previousEnd = time.end
            }
        }
    }

    fun validateArrangement(config: SemesterConfig, draft: ArrangementDraft): List<RuleIssue> = buildList {
        if (draft.name.isBlank()) add(RuleIssue(RuleField.NAME, RuleIssueCode.ARRANGEMENT_NAME_REQUIRED))
        if (draft.dayOfWeek !in 1..7) add(RuleIssue(RuleField.DAY_OF_WEEK, RuleIssueCode.DAY_OUT_OF_RANGE))
        if (draft.startSection < 1 || draft.endSection < draft.startSection || draft.endSection > config.totalSections) {
            add(RuleIssue(RuleField.SECTION_RANGE, RuleIssueCode.SECTION_RANGE_INVALID))
        }
        if (draft.weeks.isEmpty()) add(RuleIssue(RuleField.WEEKS, RuleIssueCode.WEEKS_REQUIRED))
        if (draft.weeks.any { it !in 1..config.totalWeeks }) {
            add(RuleIssue(RuleField.WEEKS, RuleIssueCode.WEEK_OUT_OF_RANGE))
        }
    }

    fun findConflicts(
        target: ArrangementDraft,
        existing: List<CourseArrangement>,
        excludingArrangementId: Long? = null,
    ): List<CourseArrangement> = existing.filter { arrangement ->
        if (excludingArrangementId != null && arrangement.id == excludingArrangementId) false
        else {
            arrangement.dayOfWeek == target.dayOfWeek &&
                maxOf(arrangement.startSection, target.startSection) <= minOf(arrangement.endSection, target.endSection) &&
                arrangement.weeks.intersect(target.weeks).isNotEmpty()
        }
    }

    fun weekPosition(
        config: SemesterConfig,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): WeekPosition {
        val alignedStart = if (firstDayOfWeek == DayOfWeek.SUNDAY) {
            config.firstWeekMonday.minusDays(1)
        } else {
            config.firstWeekMonday
        }
        val days = ChronoUnit.DAYS.between(alignedStart, today)
        if (days < 0) return WeekPosition.BeforeSemester
        val week = days / 7 + 1
        return if (week > config.totalWeeks) WeekPosition.AfterSemester else WeekPosition.InSemester(week.toInt())
    }

    fun weekSchedule(
        semesterId: Long, config: SemesterConfig, week: Int, arrangements: List<CourseArrangement>,
    ): WeekSchedule {
        require(week in 1..config.totalWeeks) { "Week is outside this semester" }
        val monday = config.firstWeekMonday.plusWeeks((week - 1).toLong())
        return WeekSchedule(
            semesterId, week, monday, monday.plusDays(6),
            arrangements.asSequence()
                .filter { it.semesterId == semesterId && week in it.weeks }
                .sortedWith(compareBy(CourseArrangement::dayOfWeek, CourseArrangement::startSection,
                    CourseArrangement::endSection, CourseArrangement::id))
                .toList(),
        )
    }

    val defaultSectionTimes: List<SectionTime> = listOf(
        "08:20" to "09:05", "09:15" to "10:00",
        "10:20" to "11:05", "11:15" to "12:00",
        "14:00" to "14:45", "14:55" to "15:40",
        "15:50" to "16:35", "16:45" to "17:30",
        "18:30" to "19:15", "19:25" to "20:10",
        "20:20" to "21:05", "21:15" to "22:00",
    ).mapIndexed { index, (start, end) ->
        SectionTime(index + 1, LocalTime.parse(start), LocalTime.parse(end))
    }

    fun findSectionTime(config: SemesterConfig, section: Int): SectionTime? =
        config.sectionTimes.firstOrNull { it.section == section }
            ?: defaultSectionTimes.getOrNull(section - 1)

    fun sectionStartTime(config: SemesterConfig, startSection: Int): LocalTime? =
        findSectionTime(config, startSection)?.start

    fun sectionEndTime(config: SemesterConfig, endSection: Int): LocalTime? =
        findSectionTime(config, endSection)?.end

    fun todaySchedule(
        semester: Semester,
        arrangements: List<CourseArrangement>,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ): TodaySchedule {
        val config = semester.config ?: return TodaySchedule.ConfigRequired(semester)
        val position = weekPosition(config, today)
        return when (position) {
            is WeekPosition.BeforeSemester -> {
                val days = ChronoUnit.DAYS.between(today, config.firstWeekMonday)
                TodaySchedule.BeforeSemester(semester, today, maxOf(1L, days))
            }
            is WeekPosition.AfterSemester -> {
                TodaySchedule.AfterSemester(semester, today)
            }
            is WeekPosition.InSemester -> {
                val currentWeek = position.week
                val dayOfWeek = today.dayOfWeek.value
                val todayArrangements = arrangements.asSequence()
                    .filter { it.semesterId == semester.id && currentWeek in it.weeks && it.dayOfWeek == dayOfWeek }
                    .sortedWith(
                        compareBy(
                            CourseArrangement::startSection,
                            CourseArrangement::endSection,
                            CourseArrangement::id,
                        )
                    )
                    .toList()

                val rawItems = todayArrangements.map { arrangement ->
                    val startTime = sectionStartTime(config, arrangement.startSection)
                    val endTime = sectionEndTime(config, arrangement.endSection)
                    val status = when {
                        startTime != null && endTime != null -> {
                            if (now.isBefore(startTime)) CourseStatus.UPCOMING
                            else if (!now.isAfter(endTime)) CourseStatus.CURRENT
                            else CourseStatus.PAST
                        }
                        else -> CourseStatus.UPCOMING
                    }
                    TodayCourseItem(
                        arrangement = arrangement,
                        startTime = startTime,
                        endTime = endTime,
                        status = status,
                    )
                }

                val nextUpcoming = rawItems.firstOrNull { it.status == CourseStatus.UPCOMING }
                val finalizedItems = rawItems.map { item ->
                    if (item == nextUpcoming) item.copy(isNext = true) else item
                }

                TodaySchedule.Ready(
                    semester = semester,
                    currentWeek = currentWeek,
                    date = today,
                    dayOfWeek = dayOfWeek,
                    courses = finalizedItems,
                )
            }
        }
    }

    /**
     * Pure calculation of App Widget display state (4x2 widget format).
     * Filters remaining courses today, or falls back to tomorrow's preview or empty states.
     */
    fun widgetSchedule(
        semester: Semester?,
        arrangements: List<CourseArrangement>,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
        showAllCourses: Boolean = false,
    ): WidgetScheduleData {
        val weekdays = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val weekdayName = weekdays.getOrElse(today.dayOfWeek.value) { "周一" }
        val dateText = "${today.monthValue}月${today.dayOfMonth}日 $weekdayName"

        if (semester == null || semester.config == null) {
            return WidgetScheduleData.NoSemester(
                dateText = dateText,
                weekText = "未配置学期",
                message = "点击进入应用配置学期与课表",
            )
        }

        val config = semester.config
        val position = weekPosition(config, today)

        return when (position) {
            is WeekPosition.BeforeSemester -> {
                val days = ChronoUnit.DAYS.between(today, config.firstWeekMonday)
                WidgetScheduleData.BeforeSemester(
                    dateText = dateText,
                    weekText = "尚未开学",
                    daysUntilStart = maxOf(1L, days),
                    message = "学期尚未开始 · 距开学还有 ${maxOf(1L, days)} 天",
                )
            }
            is WeekPosition.AfterSemester -> {
                WidgetScheduleData.AfterSemester(
                    dateText = dateText,
                    weekText = "已结课",
                    message = "本学期已结课，假期愉快！",
                )
            }
            is WeekPosition.InSemester -> {
                val currentWeek = position.week
                val dayOfWeek = today.dayOfWeek.value
                val weekText = "第 ${currentWeek} 周"

                // 1. Calculate today's courses
                val todayArrangements = arrangements.asSequence()
                    .filter { it.semesterId == semester.id && currentWeek in it.weeks && it.dayOfWeek == dayOfWeek }
                    .sortedWith(compareBy(CourseArrangement::startSection, CourseArrangement::endSection, CourseArrangement::id))
                    .toList()

                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

                fun toWidgetItem(arrangement: CourseArrangement, isTomorrow: Boolean): WidgetCourseItem {
                    val startTime = sectionStartTime(config, arrangement.startSection)
                    val endTime = sectionEndTime(config, arrangement.endSection)
                    val isOngoing = if (!isTomorrow && startTime != null && endTime != null) {
                        !now.isBefore(startTime) && !now.isAfter(endTime)
                    } else false
                    val isFinished = if (!isTomorrow && endTime != null) {
                        now.isAfter(endTime)
                    } else false

                    val timeStr = if (startTime != null && endTime != null) {
                        "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)}"
                    } else {
                        "第${arrangement.startSection}-${arrangement.endSection}节"
                    }
                    val locationStr = arrangement.location?.takeIf { it.isNotBlank() } ?: "未指定地点"

                    return WidgetCourseItem(
                        id = arrangement.id,
                        title = arrangement.name,
                        sectionText = "第${arrangement.startSection}-${arrangement.endSection}节",
                        timeText = timeStr,
                        location = locationStr,
                        teacher = arrangement.teacher,
                        isOngoing = isOngoing,
                        isTomorrow = isTomorrow,
                        isFinished = isFinished,
                    )
                }

                // Filter remaining courses today (CURRENT or UPCOMING)
                val remainingItems = todayArrangements.mapNotNull { arrangement ->
                    val endTime = sectionEndTime(config, arrangement.endSection)
                    val isPast = endTime != null && now.isAfter(endTime)
                    if (isPast) null else toWidgetItem(arrangement, isTomorrow = false)
                }

                val allTodayItems = todayArrangements.map { toWidgetItem(it, isTomorrow = false) }

                if (remainingItems.isNotEmpty()) {
                    val displayCourses = if (showAllCourses) allTodayItems else remainingItems
                    val hasOngoing = displayCourses.any { it.isOngoing }
                    val subtitle = if (hasOngoing) "正在上课" else if (showAllCourses) "当日全部课程" else "今日剩余课程"
                    WidgetScheduleData.TodayCourses(
                        dateText = dateText,
                        weekText = weekText,
                        subtitle = subtitle,
                        courses = displayCourses,
                        totalRemainingCount = if (showAllCourses) allTodayItems.size else remainingItems.size,
                    )
                } else {
                    // Today finished or no courses scheduled today -> Check tomorrow
                    val wasTodayEmpty = todayArrangements.isEmpty()
                    val statusNotice = if (wasTodayEmpty) "今日全天无课" else "今日课程已全部上完"

                    val tomorrow = today.plusDays(1)
                    val tomorrowPosition = weekPosition(config, tomorrow)

                    if (tomorrowPosition is WeekPosition.InSemester) {
                        val tomorrowWeek = tomorrowPosition.week
                        val tomorrowDay = tomorrow.dayOfWeek.value
                        val tomorrowArrangements = arrangements.asSequence()
                            .filter { it.semesterId == semester.id && tomorrowWeek in it.weeks && it.dayOfWeek == tomorrowDay }
                            .sortedWith(compareBy(CourseArrangement::startSection, CourseArrangement::endSection, CourseArrangement::id))
                            .map { toWidgetItem(it, isTomorrow = true) }
                            .toList()

                        if (tomorrowArrangements.isNotEmpty()) {
                            WidgetScheduleData.TomorrowPreview(
                                dateText = dateText,
                                weekText = weekText,
                                statusNotice = statusNotice,
                                subtitle = "明日课程预告",
                                tomorrowCourses = tomorrowArrangements,
                            )
                        } else {
                            WidgetScheduleData.AllCompleted(
                                dateText = dateText,
                                weekText = weekText,
                                message = statusNotice,
                                hint = "明日亦无课，好好休息吧~",
                            )
                        }
                    } else {
                        WidgetScheduleData.AllCompleted(
                            dateText = dateText,
                            weekText = weekText,
                            message = statusNotice,
                            hint = "明日本学期均无课，好好休息吧~",
                        )
                    }
                }
            }
        }
    }
}
