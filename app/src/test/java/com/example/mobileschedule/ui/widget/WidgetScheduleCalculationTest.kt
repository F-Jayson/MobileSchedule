package com.example.mobileschedule.ui.widget

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.data.rules.ScheduleRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WidgetScheduleCalculationTest {

    private val sampleConfig = SemesterConfig(
        firstWeekMonday = LocalDate.of(2026, 8, 31), // Week 1: 2026-08-31 (Mon)
        totalWeeks = 18,
        totalSections = 12,
        sectionTimes = listOf(
            SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            SectionTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
            SectionTime(3, LocalTime.of(10, 0), LocalTime.of(10, 45)),
            SectionTime(4, LocalTime.of(10, 50), LocalTime.of(11, 35)),
            SectionTime(5, LocalTime.of(14, 0), LocalTime.of(14, 45)),
            SectionTime(6, LocalTime.of(14, 50), LocalTime.of(15, 35)),
        ),
        revision = 1L,
    )

    private val sampleSemester = Semester(
        id = 1L,
        displayName = "2026-2027学年秋季学期",
        sourceBindings = emptySet(),
        config = sampleConfig,
    )

    private val mathCourse = CourseArrangement(
        id = 101L,
        semesterId = 1L,
        name = "高等数学",
        dayOfWeek = 1, // Monday
        startSection = 1,
        endSection = 2,
        location = "知明 1-101",
        teacher = "李老师",
        weeks = (1..18).toSet(),
        origin = com.example.mobileschedule.data.model.CourseOrigin.Manual,
    )

    private val physicsCourse = CourseArrangement(
        id = 102L,
        semesterId = 1L,
        name = "大学物理",
        dayOfWeek = 1, // Monday
        startSection = 3,
        endSection = 4,
        location = "知明 2-203",
        teacher = "王老师",
        weeks = (1..18).toSet(),
        origin = com.example.mobileschedule.data.model.CourseOrigin.Manual,
    )

    private val chemistryCourseTuesday = CourseArrangement(
        id = 201L,
        semesterId = 1L,
        name = "无机化学",
        dayOfWeek = 2, // Tuesday
        startSection = 1,
        endSection = 2,
        location = "知达 3-305",
        teacher = "张老师",
        weeks = (1..18).toSet(),
        origin = com.example.mobileschedule.data.model.CourseOrigin.Manual,
    )

    @Test
    fun widgetSchedule_inMorning_returnsTodayCoursesWithAllUpcoming() {
        val monday = LocalDate.of(2026, 8, 31) // Week 1 Monday
        val morningTime = LocalTime.of(7, 30)

        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse, physicsCourse, chemistryCourseTuesday),
            today = monday,
            now = morningTime,
        )

        assertTrue(result is WidgetScheduleData.TodayCourses)
        val todayData = result as WidgetScheduleData.TodayCourses
        assertEquals("第 1 周", todayData.weekText)
        assertEquals("今日剩余课程", todayData.subtitle)
        assertEquals(2, todayData.courses.size)
        assertEquals(101L, todayData.courses[0].id)
        assertEquals("高等数学", todayData.courses[0].title)
        assertFalse(todayData.courses[0].isOngoing)
        assertFalse(todayData.courses[0].isTomorrow)
        assertEquals(102L, todayData.courses[1].id)
        assertEquals("大学物理", todayData.courses[1].title)
    }

    @Test
    fun widgetSchedule_duringFirstClass_marksFirstCourseOngoing() {
        val monday = LocalDate.of(2026, 8, 31)
        val inClassTime = LocalTime.of(8, 30) // In Section 1 (08:00 - 08:45)

        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse, physicsCourse),
            today = monday,
            now = inClassTime,
        )

        assertTrue(result is WidgetScheduleData.TodayCourses)
        val todayData = result as WidgetScheduleData.TodayCourses
        assertEquals("正在上课", todayData.subtitle)
        assertEquals(2, todayData.courses.size)
        assertTrue(todayData.courses[0].isOngoing)
        assertFalse(todayData.courses[1].isOngoing)
    }

    @Test
    fun widgetSchedule_afterFirstClass_filtersOutPastCourse() {
        val monday = LocalDate.of(2026, 8, 31)
        val betweenClassesTime = LocalTime.of(9, 45) // After Section 2 (ends 09:35)

        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse, physicsCourse),
            today = monday,
            now = betweenClassesTime,
        )

        assertTrue(result is WidgetScheduleData.TodayCourses)
        val todayData = result as WidgetScheduleData.TodayCourses
        assertEquals(1, todayData.courses.size)
        assertEquals(102L, todayData.courses[0].id)
        assertEquals("大学物理", todayData.courses[0].title)
        assertFalse(todayData.courses[0].isOngoing)
    }

    @Test
    fun widgetSchedule_withShowAllCourses_includesPastCoursesWithFinishedFlag() {
        val monday = LocalDate.of(2026, 8, 31)
        val betweenClassesTime = LocalTime.of(9, 45) // After Section 2 (ends 09:35)

        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse, physicsCourse),
            today = monday,
            now = betweenClassesTime,
            showAllCourses = true,
        )

        assertTrue(result is WidgetScheduleData.TodayCourses)
        val todayData = result as WidgetScheduleData.TodayCourses
        assertEquals(2, todayData.courses.size)
        // First course was in morning (ends 09:35) -> finished
        assertEquals(101L, todayData.courses[0].id)
        assertTrue(todayData.courses[0].isFinished)
        assertFalse(todayData.courses[0].isOngoing)
        // Second course is upcoming (starts 10:00) -> not finished
        assertEquals(102L, todayData.courses[1].id)
        assertFalse(todayData.courses[1].isFinished)
        assertFalse(todayData.courses[1].isOngoing)
    }

    @Test
    fun widgetSchedule_allTodayCoursesEnded_showsTomorrowPreview() {
        val monday = LocalDate.of(2026, 8, 31)
        val afternoonTime = LocalTime.of(12, 0) // After Section 4 (ends 11:35)

        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse, physicsCourse, chemistryCourseTuesday),
            today = monday,
            now = afternoonTime,
        )

        assertTrue(result is WidgetScheduleData.TomorrowPreview)
        val preview = result as WidgetScheduleData.TomorrowPreview
        assertEquals("今日课程已全部上完", preview.statusNotice)
        assertEquals("明日课程预告", preview.subtitle)
        assertEquals(1, preview.tomorrowCourses.size)
        assertEquals(201L, preview.tomorrowCourses[0].id)
        assertEquals("无机化学", preview.tomorrowCourses[0].title)
        assertTrue(preview.tomorrowCourses[0].isTomorrow)
    }

    @Test
    fun widgetSchedule_todayNoCourses_showsTomorrowPreview() {
        val monday = LocalDate.of(2026, 8, 31)
        val morningTime = LocalTime.of(8, 0)

        // Only Tuesday has class
        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(chemistryCourseTuesday),
            today = monday,
            now = morningTime,
        )

        assertTrue(result is WidgetScheduleData.TomorrowPreview)
        val preview = result as WidgetScheduleData.TomorrowPreview
        assertEquals("今日全天无课", preview.statusNotice)
        assertEquals(1, preview.tomorrowCourses.size)
        assertEquals("无机化学", preview.tomorrowCourses[0].title)
    }

    @Test
    fun widgetSchedule_todayAndTomorrowBothEmpty_showsAllCompleted() {
        val friday = LocalDate.of(2026, 9, 4) // Friday, no courses on Fri or Sat
        val afternoonTime = LocalTime.of(15, 0)

        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse), // only on Monday
            today = friday,
            now = afternoonTime,
        )

        assertTrue(result is WidgetScheduleData.AllCompleted)
        val completed = result as WidgetScheduleData.AllCompleted
        assertEquals("今日全天无课", completed.message)
    }

    @Test
    fun widgetSchedule_beforeSemester_showsCountdown() {
        val beforeDate = LocalDate.of(2026, 8, 26) // 5 days before 2026-08-31
        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse),
            today = beforeDate,
            now = LocalTime.NOON,
        )

        assertTrue(result is WidgetScheduleData.BeforeSemester)
        val before = result as WidgetScheduleData.BeforeSemester
        assertEquals("尚未开学", before.weekText)
        assertEquals(5L, before.daysUntilStart)
        assertTrue(before.message.contains("5 天"))
    }

    @Test
    fun widgetSchedule_afterSemester_showsSemesterEnded() {
        val afterDate = LocalDate.of(2027, 2, 1) // After week 18
        val result = ScheduleRules.widgetSchedule(
            semester = sampleSemester,
            arrangements = listOf(mathCourse),
            today = afterDate,
            now = LocalTime.NOON,
        )

        assertTrue(result is WidgetScheduleData.AfterSemester)
        val after = result as WidgetScheduleData.AfterSemester
        assertEquals("已结课", after.weekText)
        assertTrue(after.message.contains("已结课"))
    }

    @Test
    fun widgetSchedule_noActiveSemester_showsNoSemesterPrompt() {
        val result = ScheduleRules.widgetSchedule(
            semester = null,
            arrangements = emptyList(),
            today = LocalDate.of(2026, 9, 1),
            now = LocalTime.NOON,
        )

        assertTrue(result is WidgetScheduleData.NoSemester)
        val noSemester = result as WidgetScheduleData.NoSemester
        assertEquals("未配置学期", noSemester.weekText)
        assertTrue(noSemester.message.contains("配置学期"))
    }
}
