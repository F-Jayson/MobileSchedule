package com.example.mobileschedule.data.calendar

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class IcsCalendarExporterTest {

    private val sectionTimes = listOf(
        SectionTime(1, LocalTime.of(8, 20), LocalTime.of(9, 5)),
        SectionTime(2, LocalTime.of(9, 15), LocalTime.of(10, 0)),
        SectionTime(3, LocalTime.of(10, 20), LocalTime.of(11, 5)),
        SectionTime(4, LocalTime.of(11, 15), LocalTime.of(12, 0)),
        SectionTime(5, LocalTime.of(14, 0), LocalTime.of(14, 45)),
        SectionTime(6, LocalTime.of(14, 55), LocalTime.of(15, 40)),
    )

    private val testConfig = SemesterConfig(
        firstWeekMonday = LocalDate.of(2026, 9, 7), // 2026-09-07 Monday
        totalWeeks = 18,
        totalSections = 6,
        sectionTimes = sectionTimes,
        revision = 1L,
    )

    private val testSemester = Semester(
        id = 101L,
        displayName = "2026-2027学年秋季学期",
        sourceBindings = emptySet(),
        config = testConfig,
    )

    @Test
    fun buildCalendarEvents_correctlyCalculatesDatesAndTimesForDiscreteWeeks() {
        val course = CourseArrangement(
            id = 501L,
            semesterId = 101L,
            name = "软件工程导论",
            teacher = "陈老师",
            location = "旗山校区笃行楼101",
            dayOfWeek = 3, // Wednesday
            startSection = 1,
            endSection = 2,
            weeks = setOf(1, 2, 4), // Discrete jump week
            origin = CourseOrigin.Manual,
        )

        val events = IcsCalendarExporter.buildCalendarEvents(testSemester, listOf(course))

        assertEquals(3, events.size)

        // Week 1 Wednesday: 2026-09-07 + 2 days = 2026-09-09
        val eventW1 = events[0]
        assertEquals("软件工程导论", eventW1.summary)
        assertEquals("旗山校区笃行楼101", eventW1.location)
        assertEquals(LocalDateTime.of(2026, 9, 9, 8, 20), eventW1.startDateTime)
        assertEquals(LocalDateTime.of(2026, 9, 9, 10, 0), eventW1.endDateTime)
        assertTrue(eventW1.uid.contains("course-501-w1-d3-s1"))
        assertTrue(eventW1.description!!.contains("任课教师：陈老师"))
        assertTrue(eventW1.description!!.contains("第1周 (第1~2周、第4周)"))

        // Week 2 Wednesday: 2026-09-07 + 7 + 2 = 2026-09-16
        val eventW2 = events[1]
        assertEquals(LocalDateTime.of(2026, 9, 16, 8, 20), eventW2.startDateTime)
        assertEquals(LocalDateTime.of(2026, 9, 16, 10, 0), eventW2.endDateTime)
        assertTrue(eventW2.uid.contains("course-501-w2-d3-s1"))

        // Week 4 Wednesday: 2026-09-07 + 21 + 2 = 2026-09-30
        val eventW4 = events[2]
        assertEquals(LocalDateTime.of(2026, 9, 30, 8, 20), eventW4.startDateTime)
        assertEquals(LocalDateTime.of(2026, 9, 30, 10, 0), eventW4.endDateTime)
        assertTrue(eventW4.uid.contains("course-501-w4-d3-s1"))
    }

    @Test
    fun generateIcs_producesValidRfc5545Structure() {
        val course = CourseArrangement(
            id = 502L,
            semesterId = 101L,
            name = "操作系统;系统级编程,上机/实验",
            teacher = "李教授",
            location = "仓山校区实验楼202",
            dayOfWeek = 1, // Monday
            startSection = 3,
            endSection = 4,
            weeks = setOf(1),
            origin = CourseOrigin.Manual,
        )

        val ics = IcsCalendarExporter.generateIcs(
            semester = testSemester,
            arrangements = listOf(course),
            schoolName = "福建师范大学",
            nowUtcTime = LocalDateTime.of(2026, 9, 1, 12, 0, 0),
        )

        // RFC 5545 header and footer requirements
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.contains("VERSION:2.0\r\n"))
        assertTrue(ics.contains("PRODID:-//MobileSchedule//FJNU Schedule Calendar//CN\r\n"))
        assertTrue(ics.contains("X-WR-TIMEZONE:Asia/Shanghai\r\n"))
        assertTrue(ics.contains("BEGIN:VTIMEZONE\r\n"))
        assertTrue(ics.contains("TZID:Asia/Shanghai\r\n"))
        assertTrue(ics.contains("END:VTIMEZONE\r\n"))

        // VEVENT requirements
        assertTrue(ics.contains("BEGIN:VEVENT\r\n"))
        assertTrue(ics.contains("UID:course-502-w1-d1-s3@mobileschedule.fjnu\r\n"))
        assertTrue(ics.contains("DTSTAMP:20260901T120000Z\r\n"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260907T102000\r\n"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260907T120000\r\n"))
        assertTrue(ics.contains("STATUS:CONFIRMED\r\n"))
        assertTrue(ics.contains("TRANSP:OPAQUE\r\n"))
        assertTrue(ics.contains("END:VEVENT\r\n"))

        // Special characters properly escaped: ';' and ',' in SUMMARY
        assertTrue(ics.contains("SUMMARY:操作系统\\;系统级编程\\,上机/实验\r\n"))
    }

    @Test
    fun escapeIcsText_handlesSpecialCharactersCorrectly() {
        val input = "Line 1\nLine 2; with semicolons, and, commas and \\backslashes\\."
        val escaped = IcsCalendarExporter.escapeIcsText(input)

        assertEquals(
            "Line 1\\nLine 2\\; with semicolons\\, and\\, commas and \\\\backslashes\\\\.",
            escaped
        )
    }

    @Test
    fun buildCalendarEvents_ignoresCoursesOutsideConfigTotalWeeks() {
        val course = CourseArrangement(
            id = 503L,
            semesterId = 101L,
            name = "课外扩展",
            teacher = null,
            location = null,
            dayOfWeek = 5,
            startSection = 1,
            endSection = 2,
            weeks = setOf(0, 1, 18, 19), // 0 and 19 are outside totalWeeks (1..18)
            origin = CourseOrigin.Manual,
        )

        val events = IcsCalendarExporter.buildCalendarEvents(testSemester, listOf(course))

        assertEquals(2, events.size)
        assertEquals(LocalDateTime.of(2026, 9, 11, 8, 20), events[0].startDateTime)
        // Week 18 Friday
        val expectedW18Friday = LocalDate.of(2026, 9, 7).plusWeeks(17).plusDays(4)
        assertEquals(LocalDateTime.of(expectedW18Friday, LocalTime.of(8, 20)), events[1].startDateTime)
    }
}
