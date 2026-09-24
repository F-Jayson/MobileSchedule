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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
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

    fun weekPosition(config: SemesterConfig, today: LocalDate): WeekPosition {
        val days = ChronoUnit.DAYS.between(config.firstWeekMonday, today)
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
}
