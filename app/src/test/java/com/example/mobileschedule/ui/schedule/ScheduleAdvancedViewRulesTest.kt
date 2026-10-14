package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.preferences.AppThemeMode
import com.example.mobileschedule.data.rules.ScheduleRules
import com.example.mobileschedule.ui.theme.CourseColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleAdvancedViewRulesTest {

    private val monday = LocalDate.of(2026, 3, 2) // 2026-03-02 is a Monday
    private val config = SemesterConfig(
        firstWeekMonday = monday,
        totalWeeks = 18,
        totalSections = 10,
        sectionTimes = emptyList(),
        revision = 1L,
    )

    @Test
    fun calculateDisplayedDays_whenWeekendHidden_returnsFiveWorkdays() {
        val days = calculateDisplayedDays(
            firstWeekMonday = monday,
            weekNum = 1,
            showWeekend = false,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(5, days.size)
        assertEquals(listOf(1, 2, 3, 4, 5), days.map { it.dayOfWeek })
        assertEquals(listOf(0, 1, 2, 3, 4), days.map { it.columnIndex })
        assertEquals(listOf("周一", "周二", "周三", "周四", "周五"), days.map { it.label })
        assertEquals(monday, days[0].date)
        assertEquals(monday.plusDays(4), days[4].date)
    }

    @Test
    fun calculateDisplayedDays_whenWeekendVisibleAndMondayFirst_returnsSevenDaysMonToSun() {
        val days = calculateDisplayedDays(
            firstWeekMonday = monday,
            weekNum = 2,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        val week2Monday = monday.plusWeeks(1)
        assertEquals(7, days.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), days.map { it.dayOfWeek })
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), days.map { it.columnIndex })
        assertEquals(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"), days.map { it.label })
        assertEquals(week2Monday, days[0].date)
        assertEquals(week2Monday.plusDays(6), days[6].date)
    }

    @Test
    fun calculateDisplayedDays_whenWeekendVisibleAndSundayFirst_returnsSevenDaysSunToSat() {
        val days = calculateDisplayedDays(
            firstWeekMonday = monday,
            weekNum = 1,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )

        assertEquals(7, days.size)
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), days.map { it.dayOfWeek })
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), days.map { it.columnIndex })
        assertEquals(listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六"), days.map { it.label })
        // 周日应为周一前一天
        assertEquals(monday.minusDays(1), days[0].date)
        assertEquals(monday, days[1].date)
        assertEquals(monday.plusDays(5), days[6].date)
    }

    @Test
    fun weekPosition_withSundayStart_alignsSundayToStartOfCycle() {
        // config.firstWeekMonday is 2026-03-02
        // Sunday before is 2026-03-01
        // With MONDAY start: 2026-03-01 is BeforeSemester
        assertEquals(WeekPosition.BeforeSemester, ScheduleRules.weekPosition(config, monday.minusDays(1), DayOfWeek.MONDAY))

        // With SUNDAY start: 2026-03-01 is InSemester(1)
        assertEquals(WeekPosition.InSemester(1), ScheduleRules.weekPosition(config, monday.minusDays(1), DayOfWeek.SUNDAY))
        // Monday 2026-03-02 is still InSemester(1)
        assertEquals(WeekPosition.InSemester(1), ScheduleRules.weekPosition(config, monday, DayOfWeek.SUNDAY))
        // Saturday 2026-03-07 is InSemester(1)
        assertEquals(WeekPosition.InSemester(1), ScheduleRules.weekPosition(config, monday.plusDays(5), DayOfWeek.SUNDAY))
        // Next Sunday 2026-03-08 starts Week 2
        assertEquals(WeekPosition.InSemester(2), ScheduleRules.weekPosition(config, monday.plusDays(6), DayOfWeek.SUNDAY))
    }

    @Test
    fun buildWeekGridSlots_respectsShowWeekendParameter() {
        val weekdayCourse = CourseArrangement(
            id = 1L, semesterId = 1L, name = "软件工程", teacher = "张老师", location = "理科楼101",
            dayOfWeek = 2, startSection = 1, endSection = 2, weeks = setOf(1), origin = CourseOrigin.Manual,
        )
        val saturdayCourse = CourseArrangement(
            id = 2L, semesterId = 1L, name = "算法竞赛", teacher = "李老师", location = "实验楼202",
            dayOfWeek = 6, startSection = 3, endSection = 4, weeks = setOf(1), origin = CourseOrigin.Manual,
        )
        val sundayCourse = CourseArrangement(
            id = 3L, semesterId = 1L, name = "社团活动", teacher = null, location = "学生活动中心",
            dayOfWeek = 7, startSection = 5, endSection = 6, weeks = setOf(1), origin = CourseOrigin.Manual,
        )
        val allCourses = listOf(weekdayCourse, saturdayCourse, sundayCourse)

        val slotsWithWeekend = buildWeekGridSlots(allCourses, showWeekend = true)
        assertEquals(3, slotsWithWeekend.size)

        val slotsWithoutWeekend = buildWeekGridSlots(allCourses, showWeekend = false)
        assertEquals(1, slotsWithoutWeekend.size)
        assertEquals(1L, slotsWithoutWeekend.first().arrangements.first().id)
    }

    @Test
    fun themeModeTitles_areClearlyDefined() {
        assertEquals("跟随系统", AppThemeMode.SYSTEM.title)
        assertEquals("浅色模式", AppThemeMode.LIGHT.title)
        assertEquals("深色模式", AppThemeMode.DARK.title)
    }

    @Test
    fun courseColorPalettes_haveDefinedContrastInBothLightAndDark() {
        val names = CourseColorTheme.PaletteNames
        for (i in names.indices) {
            val lightPalette = CourseColorTheme.palette(i, isDark = false)
            val darkPalette = CourseColorTheme.palette(i, isDark = true)

            // Light: container is light, onContainer is dark
            assertTrue(lightPalette.container.alpha > 0f)
            assertTrue(lightPalette.onContainer.alpha > 0f)

            // Dark: container is dark, onContainer is light/tinted
            assertTrue(darkPalette.container.alpha > 0f)
            assertTrue(darkPalette.onContainer.alpha > 0f)
        }
    }
}
