package com.example.mobileschedule.ui.today.countdown

import com.example.mobileschedule.data.model.SemesterConfig
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownCalculatorTest {

    @Test
    fun postgraduateExam_calculatesCurrentYearWhenBeforeExam() {
        val today = LocalDate.of(2026, 10, 20)
        val target = CountdownCalculator.resolvePostgraduateExam(today)

        assertNotNull(target)
        assertEquals("preset_kaoyan", target.id)
        assertEquals("考研初试", target.shortTitle)
        assertEquals(2026, target.targetDate.year)
        assertEquals(12, target.targetDate.monthValue)
        assertEquals(DayOfWeek.SATURDAY, target.targetDate.dayOfWeek)
        assertTrue(target.daysRemaining > 0)
        assertEquals("${target.daysRemaining}天", target.displayDaysText)
        assertFalse(target.isPast)
    }

    @Test
    fun postgraduateExam_crossYearCalculation_whenTodayIsAfterThisYearExam() {
        // Assume today is late December 2026 after the 2026 exam date
        val exam2026 = CountdownCalculator.resolvePostgraduateExamDate(2026)
        val today = exam2026.plusDays(2)

        val target = CountdownCalculator.resolvePostgraduateExam(today)

        // Must target 2027
        assertEquals(2027, target.targetDate.year)
        assertEquals(12, target.targetDate.monthValue)
        assertEquals(DayOfWeek.SATURDAY, target.targetDate.dayOfWeek)
        assertTrue(target.daysRemaining > 300)
        assertFalse(target.isPast)
    }

    @Test
    fun cetExam_calculatesJuneExamWhenBeforeJune() {
        val today = LocalDate.of(2026, 3, 15)
        val target = CountdownCalculator.resolveCetExam(today)

        assertEquals("preset_cet", target.id)
        assertEquals(2026, target.targetDate.year)
        assertEquals(6, target.targetDate.monthValue)
        assertEquals(DayOfWeek.SATURDAY, target.targetDate.dayOfWeek)
        assertTrue(target.title.contains("上半年"))
        assertTrue(target.daysRemaining > 0)
    }

    @Test
    fun cetExam_calculatesDecemberExamWhenBetweenJuneAndDecember() {
        val today = LocalDate.of(2026, 7, 1)
        val target = CountdownCalculator.resolveCetExam(today)

        assertEquals("preset_cet", target.id)
        assertEquals(2026, target.targetDate.year)
        assertEquals(12, target.targetDate.monthValue)
        assertEquals(DayOfWeek.SATURDAY, target.targetDate.dayOfWeek)
        assertTrue(target.title.contains("下半年"))
        assertTrue(target.daysRemaining > 0)
    }

    @Test
    fun cetExam_crossYearCalculationWhenAfterDecemberExam() {
        val dec2026 = CountdownCalculator.resolveDecemberCetSaturday(2026)
        val today = dec2026.plusDays(3)

        val target = CountdownCalculator.resolveCetExam(today)
        assertEquals(2027, target.targetDate.year)
        assertEquals(6, target.targetDate.monthValue)
        assertTrue(target.title.contains("上半年"))
        assertTrue(target.daysRemaining > 0)
    }

    @Test
    fun vacationCountdown_springSemesterResolvesSummerVacation() {
        // Spring semester starting in March
        val springConfig = SemesterConfig(
            firstWeekMonday = LocalDate.of(2026, 3, 2),
            totalWeeks = 18,
            totalSections = 12,
            sectionTimes = emptyList(),
            revision = 1L,
        )
        val today = LocalDate.of(2026, 5, 10)
        val target = CountdownCalculator.resolveVacation(today, springConfig, "2025-2026学年春季学期")

        assertNotNull(target)
        assertEquals("暑假放假", target!!.shortTitle)
        assertEquals(CountdownCategory.HOLIDAY, target.category)
        val expectedVacation = LocalDate.of(2026, 3, 2).plusWeeks(18)
        assertEquals(expectedVacation, target.targetDate)
        assertTrue(target.daysRemaining > 0)
    }

    @Test
    fun vacationCountdown_fallSemesterResolvesWinterVacation() {
        // Fall semester starting in September
        val fallConfig = SemesterConfig(
            firstWeekMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 19,
            totalSections = 12,
            sectionTimes = emptyList(),
            revision = 1L,
        )
        val today = LocalDate.of(2026, 10, 1)
        val target = CountdownCalculator.resolveVacation(today, fallConfig, "2026-2027学年秋季学期")

        assertNotNull(target)
        assertEquals("寒假放假", target!!.shortTitle)
        assertEquals(CountdownCategory.HOLIDAY, target.category)
        val expectedVacation = LocalDate.of(2026, 9, 7).plusWeeks(19)
        assertEquals(expectedVacation, target.targetDate)
    }

    @Test
    fun finalExamsCountdown_resolvesLastWeeks() {
        val config = SemesterConfig(
            firstWeekMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 18,
            totalSections = 12,
            sectionTimes = emptyList(),
            revision = 1L,
        )
        val today = LocalDate.of(2026, 11, 1)
        val target = CountdownCalculator.resolveFinalExams(today, config, "秋季学期")

        assertNotNull(target)
        assertEquals("期末考试周", target!!.shortTitle)
        assertEquals(LocalDate.of(2026, 9, 7).plusWeeks(17), target.targetDate)
    }

    @Test
    fun buildAllCountdowns_combinesPresetsAndCustoms_andHonorsPinning() {
        val today = LocalDate.of(2026, 10, 20)
        val custom1 = CustomCountdown(
            id = "custom_ncre",
            title = "全国计算机等级考试",
            targetDate = LocalDate.of(2026, 11, 15),
            note = "稳扎稳打冲刺优秀",
        )
        val custom2 = CustomCountdown(
            id = "custom_thesis",
            title = "毕业论文开题答辩",
            targetDate = LocalDate.of(2026, 10, 25),
        )

        // Without pinning, closest upcoming event is first
        val listUnpinned = CountdownCalculator.buildAllCountdowns(
            today = today,
            config = null,
            customList = listOf(custom1, custom2),
            pinnedId = null,
        )
        // custom2 is 5 days away (10-25), closest!
        assertEquals("custom_thesis", listUnpinned.first().id)

        // With pinning custom1
        val listPinned = CountdownCalculator.buildAllCountdowns(
            today = today,
            config = null,
            customList = listOf(custom1, custom2),
            pinnedId = "custom_ncre",
        )
        // pinned item MUST be first!
        assertEquals("custom_ncre", listPinned.first().id)
        assertTrue(listPinned.first().isPinned)
    }

    @Test
    fun displayDaysText_handlesTodayAndTomorrowAndPast() {
        val today = LocalDate.of(2026, 10, 20)
        val targetToday = CountdownTarget(
            id = "test",
            title = "开题报告",
            shortTitle = "开题",
            targetDate = today,
            category = CountdownCategory.CUSTOM,
            motto = "",
            daysRemaining = 0,
        )
        assertEquals("今天", targetToday.displayDaysText)

        val targetTomorrow = targetToday.copy(daysRemaining = 1)
        assertEquals("明天", targetTomorrow.displayDaysText)

        val targetPast = targetToday.copy(daysRemaining = -3)
        assertEquals("已过", targetPast.displayDaysText)
        assertTrue(targetPast.isPast)
    }
}
