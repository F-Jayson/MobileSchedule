package com.example.mobileschedule.data.reminder

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ScheduleRules
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CourseReminderCalculationTest {

    private val monday = LocalDate.of(2026, 10, 12)
    private val sectionTimes = listOf(
        SectionTime(1, LocalTime.of(8, 20), LocalTime.of(9, 5)),
        SectionTime(2, LocalTime.of(9, 15), LocalTime.of(10, 0)),
        SectionTime(3, LocalTime.of(10, 20), LocalTime.of(11, 5)),
        SectionTime(4, LocalTime.of(11, 15), LocalTime.of(12, 0)),
        SectionTime(5, LocalTime.of(14, 0), LocalTime.of(14, 45)),
        SectionTime(6, LocalTime.of(14, 55), LocalTime.of(15, 40)),
        SectionTime(7, LocalTime.of(15, 50), LocalTime.of(16, 35)),
        SectionTime(8, LocalTime.of(16, 45), LocalTime.of(17, 30)),
    )
    private val config = SemesterConfig(
        firstWeekMonday = monday,
        totalWeeks = 18,
        totalSections = 8,
        sectionTimes = sectionTimes,
        revision = 1L,
    )

    @Test
    fun reminderAlarmTimeCalculatedCorrectlyWithVariousAdvanceMinutes() {
        val course = CourseArrangement(
            id = 101L,
            semesterId = 1L,
            name = "高等数学",
            teacher = "王老师",
            location = "理科楼101",
            dayOfWeek = 1, // Monday
            startSection = 1, // 08:20
            endSection = 2,
            weeks = setOf(1, 2, 3),
            origin = CourseOrigin.Manual,
        )

        val startTime = ScheduleRules.sectionStartTime(config, course.startSection)
        assertNotNull(startTime)
        assertEquals(LocalTime.of(8, 20), startTime)

        val date = monday // Week 1 Monday
        val startDateTime = LocalDateTime.of(date, startTime)

        // 15 minutes in advance (default)
        val alarm15 = startDateTime.minusMinutes(15)
        assertEquals(LocalDateTime.of(date, LocalTime.of(8, 5)), alarm15)

        // 30 minutes in advance
        val alarm30 = startDateTime.minusMinutes(30)
        assertEquals(LocalDateTime.of(date, LocalTime.of(7, 50)), alarm30)

        // 5 minutes in advance
        val alarm5 = startDateTime.minusMinutes(5)
        assertEquals(LocalDateTime.of(date, LocalTime.of(8, 15)), alarm5)

        // 60 minutes in advance
        val alarm60 = startDateTime.minusMinutes(60)
        assertEquals(LocalDateTime.of(date, LocalTime.of(7, 20)), alarm60)
    }

    @Test
    fun afternoonCourseReminderCalculationCrossesNoonCorrectly() {
        val course = CourseArrangement(
            id = 102L,
            semesterId = 1L,
            name = "大学英语",
            teacher = "李老师",
            location = "文科楼202",
            dayOfWeek = 2, // Tuesday
            startSection = 5, // 14:00
            endSection = 6,
            weeks = setOf(1),
            origin = CourseOrigin.Manual,
        )

        val startTime = ScheduleRules.sectionStartTime(config, course.startSection)
        assertNotNull(startTime)
        assertEquals(LocalTime.of(14, 0), startTime)

        val tuesday = monday.plusDays(1)
        val startDateTime = LocalDateTime.of(tuesday, startTime)

        val alarm20 = startDateTime.minusMinutes(20)
        assertEquals(LocalDateTime.of(tuesday, LocalTime.of(13, 40)), alarm20)

        val alarm45 = startDateTime.minusMinutes(45)
        assertEquals(LocalDateTime.of(tuesday, LocalTime.of(13, 15)), alarm45)
    }

    @Test
    fun pastAlarmTimeIdentifiedAndExcluded() {
        val courseStartTime = LocalTime.of(8, 20)
        val courseDate = monday
        val alarmTime = LocalDateTime.of(courseDate, courseStartTime).minusMinutes(15)

        // Current time is 08:10 (after alarm time 08:05) -> should be excluded
        val nowAfterAlarm = LocalDateTime.of(courseDate, LocalTime.of(8, 10))
        assertTrue(alarmTime.isBefore(nowAfterAlarm))

        // Current time is 07:55 (before alarm time 08:05) -> should be included
        val nowBeforeAlarm = LocalDateTime.of(courseDate, LocalTime.of(7, 55))
        assertFalse(alarmTime.isBefore(nowBeforeAlarm))
    }

    @Test
    fun requestCodeGenerationGuaranteesUniquenessAcrossDaysAndCourses() {
        val base = 200000
        val requestCodes = mutableSetOf<Int>()

        for (dayOffset in 0..6) {
            for (courseId in 1L..50L) {
                val code = base + (dayOffset * 10000 + (courseId % 10000)).toInt()
                assertTrue("Collision detected for dayOffset=$dayOffset, courseId=$courseId", requestCodes.add(code))
            }
        }

        assertEquals(7 * 50, requestCodes.size)
    }
}
