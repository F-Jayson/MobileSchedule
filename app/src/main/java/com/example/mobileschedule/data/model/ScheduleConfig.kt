package com.example.mobileschedule.data.model

import java.time.LocalDate

/** User-confirmed values. A non-Monday input must be confirmed in the UI before constructing this draft. */
data class SemesterConfigDraft(
    val displayName: String,
    val firstWeekMonday: LocalDate,
    val totalWeeks: Int,
    val totalSections: Int,
    val sectionTimes: List<SectionTime>,
)

/** Fields shared by school-import candidates and stored arrangements after parsing. */
data class ArrangementDraft(
    val name: String,
    val teacher: String?,
    val location: String?,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: Set<Int>,
)

enum class RuleField {
    DISPLAY_NAME, FIRST_WEEK_MONDAY, TOTAL_WEEKS, TOTAL_SECTIONS, SECTION_TIMES,
    NAME, DAY_OF_WEEK, SECTION_RANGE, WEEKS,
}

enum class RuleIssueCode {
    DISPLAY_NAME_REQUIRED, FIRST_DAY_NOT_MONDAY, TOTAL_WEEKS_OUT_OF_RANGE,
    TOTAL_SECTIONS_OUT_OF_RANGE, TIMES_INCOMPLETE, SECTION_ORDER_INVALID,
    TIME_NOT_MINUTE_PRECISE, TIME_RANGE_INVALID, TIME_OVERLAP,
    ARRANGEMENT_NAME_REQUIRED, DAY_OUT_OF_RANGE, SECTION_RANGE_INVALID,
    WEEKS_REQUIRED, WEEK_OUT_OF_RANGE,
}

data class RuleIssue(val field: RuleField, val code: RuleIssueCode, val section: Int? = null)

sealed interface WeekPosition {
    data object BeforeSemester : WeekPosition
    data class InSemester(val week: Int) : WeekPosition
    data object AfterSemester : WeekPosition
}

data class WeekSchedule(
    val semesterId: Long,
    val week: Int,
    val monday: LocalDate,
    val sunday: LocalDate,
    val arrangements: List<CourseArrangement>,
)
