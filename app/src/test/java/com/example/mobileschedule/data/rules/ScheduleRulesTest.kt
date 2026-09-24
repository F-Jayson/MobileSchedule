package com.example.mobileschedule.data.rules

import com.example.mobileschedule.data.model.ArrangementDraft
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.RuleIssueCode
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.WeekPosition
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRulesTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private fun draft(
        firstMonday: LocalDate = monday,
        weeks: Int = 2,
        sections: Int = 3,
        times: List<SectionTime> = emptyList(),
    ) = SemesterConfigDraft("  合成学期  ", firstMonday, weeks, sections, times)

    private fun config(weeks: Int = 2, sections: Int = 3) =
        SemesterConfig(monday, weeks, sections, emptyList(), 1)

    @Test fun boundariesIncludeMondayAndSundayButNotAdjacentWeeks() {
        val config = config()
        val expected = mapOf(
            LocalDate.of(2026, 9, 6) to WeekPosition.BeforeSemester,
            monday to WeekPosition.InSemester(1),
            LocalDate.of(2026, 9, 13) to WeekPosition.InSemester(1),
            LocalDate.of(2026, 9, 14) to WeekPosition.InSemester(2),
            LocalDate.of(2026, 9, 20) to WeekPosition.InSemester(2),
            LocalDate.of(2026, 9, 21) to WeekPosition.AfterSemester,
        )
        for ((date, position) in expected) assertEquals(date.toString(), position, ScheduleRules.weekPosition(config, date))
    }

    @Test fun emptyTimesAreValidAndBoundsRequireConfirmedMonday() {
        assertTrue(ScheduleRules.validateConfig(draft()).isEmpty())
        val issues = ScheduleRules.validateConfig(draft(firstMonday = monday.plusDays(1), weeks = 0, sections = 100).copy(displayName = "  "))
        assertEquals(
            setOf(RuleIssueCode.DISPLAY_NAME_REQUIRED, RuleIssueCode.FIRST_DAY_NOT_MONDAY,
                RuleIssueCode.TOTAL_WEEKS_OUT_OF_RANGE, RuleIssueCode.TOTAL_SECTIONS_OUT_OF_RANGE),
            issues.map { it.code }.toSet(),
        )
        assertTrue(ScheduleRules.validateConfig(draft(weeks = 61)).any { it.code == RuleIssueCode.TOTAL_WEEKS_OUT_OF_RANGE })
    }

    @Test fun timeTableMustBeCompleteOrderedMinutePreciseAndNonOverlapping() {
        val valid = listOf(
            SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            SectionTime(2, LocalTime.of(8, 45), LocalTime.of(9, 30)),
            SectionTime(3, LocalTime.of(9, 40), LocalTime.of(10, 25)),
        )
        assertTrue(ScheduleRules.validateConfig(draft(times = valid)).isEmpty())
        assertTrue(ScheduleRules.validateConfig(draft(times = valid.dropLast(1))).any { it.code == RuleIssueCode.TIMES_INCOMPLETE })
        assertTrue(ScheduleRules.validateConfig(draft(times = valid.reversed())).any { it.code == RuleIssueCode.SECTION_ORDER_INVALID })
        assertTrue(ScheduleRules.validateConfig(draft(times = valid.toMutableList().apply { this[1] = valid[0] })).any { it.code == RuleIssueCode.SECTION_ORDER_INVALID })
        assertTrue(ScheduleRules.validateConfig(draft(times = valid.toMutableList().apply { this[1] = valid[1].copy(start = LocalTime.of(8, 44)) })).any { it.code == RuleIssueCode.TIME_OVERLAP })
        assertTrue(ScheduleRules.validateConfig(draft(times = valid.toMutableList().apply { this[1] = valid[1].copy(end = LocalTime.of(8, 40)) })).any { it.code == RuleIssueCode.TIME_RANGE_INVALID })
        assertTrue(ScheduleRules.validateConfig(draft(times = valid.toMutableList().apply { this[2] = valid[2].copy(start = LocalTime.of(9, 40, 1)) })).any { it.code == RuleIssueCode.TIME_NOT_MINUTE_PRECISE })
    }

    @Test fun importArrangementValidationUsesSameWeekAndSectionLimits() {
        val valid = ArrangementDraft("课程", null, null, 7, 2, 3, setOf(1, 2))
        assertTrue(ScheduleRules.validateArrangement(config(), valid).isEmpty())
        val bad = valid.copy(name = " ", dayOfWeek = 8, startSection = 0, endSection = 4, weeks = setOf(0, 3))
        assertEquals(
            setOf(RuleIssueCode.ARRANGEMENT_NAME_REQUIRED, RuleIssueCode.DAY_OUT_OF_RANGE,
                RuleIssueCode.SECTION_RANGE_INVALID, RuleIssueCode.WEEK_OUT_OF_RANGE),
            ScheduleRules.validateArrangement(config(), bad).map { it.code }.toSet(),
        )
        assertTrue(ScheduleRules.validateArrangement(config(), valid.copy(weeks = emptySet())).any { it.code == RuleIssueCode.WEEKS_REQUIRED })
        assertTrue(ScheduleRules.validateArrangement(config(), valid.copy(startSection = 3, endSection = 2)).any { it.code == RuleIssueCode.SECTION_RANGE_INVALID })
    }

    @Test fun requestedWeekPreservesSparseWeeksAndAllOverlappingArrangements() {
        val rows = listOf(
            CourseArrangement(4, 9, "同名", null, null, 2, 1, 2, setOf(1, 3, 9), CourseOrigin.Manual),
            CourseArrangement(1, 9, "同名", null, "B101", 2, 1, 2, setOf(3), CourseOrigin.Manual),
            CourseArrangement(2, 9, "另课", null, null, 1, 1, 1, setOf(1, 2), CourseOrigin.Legacy),
            CourseArrangement(3, 10, "别学期", null, null, 1, 1, 1, setOf(3), CourseOrigin.Manual),
        )
        val week3 = ScheduleRules.weekSchedule(9, config(weeks = 9), 3, rows)
        assertEquals(LocalDate.of(2026, 9, 21), week3.monday)
        assertEquals(LocalDate.of(2026, 9, 27), week3.sunday)
        assertEquals(listOf(1L, 4L), week3.arrangements.map { it.id })
        assertEquals(listOf(2L, 4L), ScheduleRules.weekSchedule(9, config(weeks = 9), 1, rows).arrangements.map { it.id })
        assertTrue(ScheduleRules.weekSchedule(9, config(weeks = 9), 4, rows).arrangements.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun requestedWeekCannotExceedConfiguredTotal() {
        ScheduleRules.weekSchedule(9, config(), 3, emptyList())
    }
}
