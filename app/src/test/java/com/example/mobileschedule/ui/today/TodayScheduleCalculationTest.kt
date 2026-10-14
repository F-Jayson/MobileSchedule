package com.example.mobileschedule.ui.today

import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayScheduleCalculationTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(
        firstWeekMonday = monday,
        totalWeeks = 16,
        totalSections = 12,
        sectionTimes = emptyList(), // uses default section times
        revision = 1L,
    )
    private val semester = Semester(
        id = 1L,
        displayName = "福建师范大学 2026秋季学期",
        sourceBindings = emptySet(),
        config = config,
    )

    // Course 1: Mon 1-2 (08:20 - 10:00), weeks 1..16
    private val course1 = CourseArrangement(
        id = 101L,
        semesterId = 1L,
        name = "高等数学",
        teacher = "张教授",
        location = "知明1-101",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.SchoolImport(SourceScope("fjnu", "dom", "2026-1"), "batch-1", "entry-1"),
    )

    // Course 2: Mon 3-4 (10:20 - 12:00), weeks 1..16
    private val course2 = CourseArrangement(
        id = 102L,
        semesterId = 1L,
        name = "大学英语",
        teacher = "王老师",
        location = "知明2-202",
        dayOfWeek = 1,
        startSection = 3,
        endSection = 4,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.SchoolImport(SourceScope("fjnu", "dom", "2026-1"), "batch-1", "entry-2"),
    )

    // Course 3: Mon 5-6 (14:00 - 15:40), weeks 1..16
    private val course3 = CourseArrangement(
        id = 103L,
        semesterId = 1L,
        name = "计算机系统",
        teacher = "李教授",
        location = "实验楼A301",
        dayOfWeek = 1,
        startSection = 5,
        endSection = 6,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.Manual,
    )

    // Course 4: Odd-weeks only, Wed 1-2
    private val courseOddWed = CourseArrangement(
        id = 104L,
        semesterId = 1L,
        name = "线性代数",
        teacher = "陈老师",
        location = "知明1-105",
        dayOfWeek = 3,
        startSection = 1,
        endSection = 2,
        weeks = setOf(1, 3, 5, 7, 9, 11, 13, 15),
        origin = CourseOrigin.Manual,
    )

    private val allCourses = listOf(course1, course2, course3, courseOddWed)

    @Test
    fun morningBeforeAllClasses_allUpcoming_firstIsNext() {
        val today = monday // 2026-09-07, Week 1, Monday
        val now = LocalTime.of(7, 30) // 07:30 before 08:20

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        assertTrue(schedule is TodaySchedule.Ready)
        val ready = schedule as TodaySchedule.Ready

        assertEquals(1, ready.currentWeek)
        assertEquals(1, ready.dayOfWeek)
        assertEquals(3, ready.courses.size)

        // Course 1: UPCOMING, isNext = true
        val c1 = ready.courses[0]
        assertEquals(CourseStatus.UPCOMING, c1.status)
        assertTrue(c1.isNext)
        assertEquals(LocalTime.of(8, 20), c1.startTime)
        assertEquals(LocalTime.of(10, 0), c1.endTime)

        // Course 2: UPCOMING, isNext = false
        val c2 = ready.courses[1]
        assertEquals(CourseStatus.UPCOMING, c2.status)
        assertFalse(c2.isNext)

        // Course 3: UPCOMING, isNext = false
        val c3 = ready.courses[2]
        assertEquals(CourseStatus.UPCOMING, c3.status)
        assertFalse(c3.isNext)
    }

    @Test
    fun duringClass1_course1Current_course2UpcomingAndNext() {
        val today = monday // Week 1 Monday
        val now = LocalTime.of(9, 0) // 09:00 during 08:20 - 10:00

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        assertTrue(schedule is TodaySchedule.Ready)
        val ready = schedule as TodaySchedule.Ready

        // Course 1: CURRENT
        val c1 = ready.courses[0]
        assertEquals(CourseStatus.CURRENT, c1.status)
        assertFalse(c1.isNext)

        // Course 2: UPCOMING, isNext = true
        val c2 = ready.courses[1]
        assertEquals(CourseStatus.UPCOMING, c2.status)
        assertTrue(c2.isNext)

        // Course 3: UPCOMING, isNext = false
        val c3 = ready.courses[2]
        assertEquals(CourseStatus.UPCOMING, c3.status)
        assertFalse(c3.isNext)
    }

    @Test
    fun betweenClass1AndClass2_course1Past_course2UpcomingAndNext() {
        val today = monday
        val now = LocalTime.of(10, 10) // 10:10 (class 1 ended at 10:00, class 2 starts at 10:20)

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        assertTrue(schedule is TodaySchedule.Ready)
        val ready = schedule as TodaySchedule.Ready

        // Course 1: PAST
        val c1 = ready.courses[0]
        assertEquals(CourseStatus.PAST, c1.status)
        assertFalse(c1.isNext)

        // Course 2: UPCOMING, isNext = true
        val c2 = ready.courses[1]
        assertEquals(CourseStatus.UPCOMING, c2.status)
        assertTrue(c2.isNext)

        // Course 3: UPCOMING, isNext = false
        val c3 = ready.courses[2]
        assertEquals(CourseStatus.UPCOMING, c3.status)
        assertFalse(c3.isNext)
    }

    @Test
    fun duringClass2_course1Past_course2Current_course3UpcomingAndNext() {
        val today = monday
        val now = LocalTime.of(11, 0) // 11:00 during 10:20 - 12:00

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        assertTrue(schedule is TodaySchedule.Ready)
        val ready = schedule as TodaySchedule.Ready

        val c1 = ready.courses[0]
        assertEquals(CourseStatus.PAST, c1.status)

        val c2 = ready.courses[1]
        assertEquals(CourseStatus.CURRENT, c2.status)

        val c3 = ready.courses[2]
        assertEquals(CourseStatus.UPCOMING, c3.status)
        assertTrue(c3.isNext)
    }

    @Test
    fun lateNightAfterAllClasses_allPast_noNext() {
        val today = monday
        val now = LocalTime.of(22, 30) // 22:30 after all classes

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        assertTrue(schedule is TodaySchedule.Ready)
        val ready = schedule as TodaySchedule.Ready

        ready.courses.forEach { item ->
            assertEquals(CourseStatus.PAST, item.status)
            assertFalse(item.isNext)
        }
    }

    @Test
    fun weekendOrNoCoursesDay_returnsEmptyCourseList() {
        // Sunday 2026-09-13
        val sunday = monday.plusDays(6)
        val now = LocalTime.of(12, 0)

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, sunday, now)
        assertTrue(schedule is TodaySchedule.Ready)
        val ready = schedule as TodaySchedule.Ready

        assertEquals(7, ready.dayOfWeek)
        assertEquals(1, ready.currentWeek)
        assertTrue(ready.courses.isEmpty())
    }

    @Test
    fun discreteWeekFiltering_oddWeekCourseAppearsOnlyOnOddWeeks() {
        // Wednesday of Week 1 (2026-09-09): odd week
        val wedWeek1 = monday.plusDays(2)
        val schedule1 = ScheduleRules.todaySchedule(semester, allCourses, wedWeek1, LocalTime.of(8, 0))
        assertTrue(schedule1 is TodaySchedule.Ready)
        val ready1 = schedule1 as TodaySchedule.Ready
        assertEquals(1, ready1.courses.size)
        assertEquals("线性代数", ready1.courses[0].arrangement.name)

        // Wednesday of Week 2 (2026-09-16): even week -> should NOT have Course 4
        val wedWeek2 = monday.plusWeeks(1).plusDays(2)
        val schedule2 = ScheduleRules.todaySchedule(semester, allCourses, wedWeek2, LocalTime.of(8, 0))
        assertTrue(schedule2 is TodaySchedule.Ready)
        val ready2 = schedule2 as TodaySchedule.Ready
        assertTrue(ready2.courses.isEmpty())

        // Wednesday of Week 3 (2026-09-23): odd week -> should have Course 4 again
        val wedWeek3 = monday.plusWeeks(2).plusDays(2)
        val schedule3 = ScheduleRules.todaySchedule(semester, allCourses, wedWeek3, LocalTime.of(8, 0))
        assertTrue(schedule3 is TodaySchedule.Ready)
        val ready3 = schedule3 as TodaySchedule.Ready
        assertEquals(1, ready3.courses.size)
        assertEquals("线性代数", ready3.courses[0].arrangement.name)
    }

    @Test
    fun beforeSemester_returnsBeforeSemesterWithCountdown() {
        val beforeDate = LocalDate.of(2026, 8, 31) // 7 days before semester start (2026-09-07)
        val schedule = ScheduleRules.todaySchedule(semester, allCourses, beforeDate, LocalTime.of(10, 0))

        assertTrue(schedule is TodaySchedule.BeforeSemester)
        val before = schedule as TodaySchedule.BeforeSemester
        assertEquals(7L, before.daysUntilStart)
        assertEquals(semester, before.semester)
    }

    @Test
    fun afterSemester_returnsAfterSemester() {
        // Semester has 16 weeks -> ends 2026-12-27. Test date 2027-01-10.
        val afterDate = LocalDate.of(2027, 1, 10)
        val schedule = ScheduleRules.todaySchedule(semester, allCourses, afterDate, LocalTime.of(10, 0))

        assertTrue(schedule is TodaySchedule.AfterSemester)
        val after = schedule as TodaySchedule.AfterSemester
        assertEquals(semester, after.semester)
    }

    @Test
    fun configRequiredWhenSemesterHasNoConfig() {
        val unconfiguredSemester = Semester(id = 2L, displayName = "未配置学期", sourceBindings = emptySet(), config = null)
        val schedule = ScheduleRules.todaySchedule(unconfiguredSemester, allCourses)

        assertTrue(schedule is TodaySchedule.ConfigRequired)
        assertEquals(unconfiguredSemester, (schedule as TodaySchedule.ConfigRequired).semester)
    }
}
