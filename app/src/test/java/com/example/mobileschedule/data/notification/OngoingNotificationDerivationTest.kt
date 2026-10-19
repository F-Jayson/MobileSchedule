package com.example.mobileschedule.data.notification

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SourceScope
import com.example.mobileschedule.data.model.TodaySchedule
import com.example.mobileschedule.data.rules.ScheduleRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OngoingNotificationDerivationTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(
        firstWeekMonday = monday,
        totalWeeks = 16,
        totalSections = 12,
        sectionTimes = emptyList(), // uses default section times (e.g., 1-2: 08:20-10:00, 3-4: 10:20-12:00, 5-6: 14:00-15:40)
        revision = 1L,
    )
    private val semester = Semester(
        id = 1L,
        displayName = "福建师范大学 2026秋季学期",
        sourceBindings = emptySet(),
        config = config,
    )

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

    private val allCourses = listOf(course1, course2, course3)

    @Test
    fun testBeforeAllClasses_displaysNextClassAndCountdown() {
        val today = monday // Week 1, Monday
        val now = LocalTime.of(7, 30) // 50 mins before 08:20

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        val data = OngoingScheduleNotificationManager.deriveCapsuleNotificationData(schedule, now)

        assertTrue(data.shouldShow)
        assertEquals("下一节课（还有 50 分钟）：高等数学 - 知明1-101", data.title)
        assertTrue(data.contentText.contains("08:20 - 10:00"))
        assertTrue(data.contentText.contains("第1~2节"))
        assertTrue(data.contentText.contains("张教授"))
        assertEquals("课表胶囊", data.subText)
        assertEquals(101L, data.targetCourseId)
    }

    @Test
    fun testDuringFirstClass_displaysInProgressAndNextClass() {
        val today = monday
        val now = LocalTime.of(9, 0) // During 08:20 - 10:00

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        val data = OngoingScheduleNotificationManager.deriveCapsuleNotificationData(schedule, now)

        assertTrue(data.shouldShow)
        assertEquals("正在上课：高等数学 - 知明1-101", data.title)
        assertEquals("下节课：大学英语 - 知明2-202", data.contentText)
        assertEquals("正在上课", data.subText)
        assertEquals(101L, data.targetCourseId)
    }

    @Test
    fun testDuringBreakBetweenClasses_displaysNextClassCountdown() {
        val today = monday
        val now = LocalTime.of(10, 10) // 10 mins before 10:20

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        val data = OngoingScheduleNotificationManager.deriveCapsuleNotificationData(schedule, now)

        assertTrue(data.shouldShow)
        assertEquals("下一节课（还有 10 分钟）：大学英语 - 知明2-202", data.title)
        assertTrue(data.contentText.contains("10:20 - 12:00"))
        assertTrue(data.contentText.contains("第3~4节"))
        assertEquals(102L, data.targetCourseId)
    }

    @Test
    fun testDuringLastClass_displaysInProgressAndLastClassNotice() {
        val today = monday
        val now = LocalTime.of(14, 30) // During 14:00 - 15:40

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        val data = OngoingScheduleNotificationManager.deriveCapsuleNotificationData(schedule, now)

        assertTrue(data.shouldShow)
        assertEquals("正在上课：计算机系统 - 实验楼A301", data.title)
        assertEquals("今日最后一节课", data.contentText)
        assertEquals("正在上课", data.subText)
        assertEquals(103L, data.targetCourseId)
    }

    @Test
    fun testAfterAllClassesEnded_dismissesNotification() {
        val today = monday
        val now = LocalTime.of(16, 0) // After 15:40

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, today, now)
        val data = OngoingScheduleNotificationManager.deriveCapsuleNotificationData(schedule, now)

        assertFalse("All classes ended should not show notification", data.shouldShow)
    }

    @Test
    fun testNoClassesToday_dismissesNotification() {
        val sunday = monday.plusDays(6) // Sunday, no courses scheduled
        val now = LocalTime.of(10, 0)

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, sunday, now)
        val data = OngoingScheduleNotificationManager.deriveCapsuleNotificationData(schedule, now)

        assertFalse("No classes today should not show notification", data.shouldShow)
    }

    @Test
    fun testNotStartedOrEndedSemester_dismissesNotification() {
        val beforeSemester = monday.minusWeeks(2)
        val now = LocalTime.of(9, 0)

        val schedule = ScheduleRules.todaySchedule(semester, allCourses, beforeSemester, now)
        val data = OngoingScheduleNotificationManager.deriveCapsuleNotificationData(schedule, now)

        assertFalse("Before semester should not show notification", data.shouldShow)
    }
}
