package com.example.mobileschedule.ui.widget

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SemesterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleAutoRefreshSchedulerTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(
        firstWeekMonday = monday,
        totalWeeks = 16,
        totalSections = 12,
        sectionTimes = emptyList(), // Default: 1-2: 08:20-10:00, 3-4: 10:20-12:00
        revision = 1L,
    )

    private val course1 = CourseArrangement(
        id = 1L,
        semesterId = 1L,
        name = "高等数学",
        teacher = "张教授",
        location = "知明1-101",
        dayOfWeek = 1, // Monday
        startSection = 1,
        endSection = 2,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.Manual,
    )

    private val course2 = CourseArrangement(
        id = 2L,
        semesterId = 1L,
        name = "大学物理",
        teacher = "李教授",
        location = "实验楼A301",
        dayOfWeek = 1, // Monday
        startSection = 3,
        endSection = 4,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.Manual,
    )

    private val arrangements = listOf(course1, course2)

    @Test
    fun testComputeTransitionTimes_morningBeforeClass() {
        val from = LocalDateTime.of(monday, LocalTime.of(7, 0, 0))
        val transitions = ScheduleAutoRefreshScheduler.computeTransitionTimes(
            config = config,
            arrangements = arrangements,
            from = from,
            lookaheadDays = 1,
        )

        assertTrue(transitions.isNotEmpty())
        // First transition should be 30m before course1 (08:20 - 30m = 07:50)
        assertEquals(LocalDateTime.of(monday, LocalTime.of(7, 50, 0)), transitions.first())

        // Must include all key milestones
        assertTrue(transitions.contains(LocalDateTime.of(monday, LocalTime.of(8, 5, 0)))) // 15m before
        assertTrue(transitions.contains(LocalDateTime.of(monday, LocalTime.of(8, 15, 0)))) // 5m before
        assertTrue(transitions.contains(LocalDateTime.of(monday, LocalTime.of(8, 20, 0)))) // class 1 start
        assertTrue(transitions.contains(LocalDateTime.of(monday, LocalTime.of(10, 0, 0)))) // class 1 end
        assertTrue(transitions.contains(LocalDateTime.of(monday, LocalTime.of(10, 20, 0)))) // class 2 start
        assertTrue(transitions.contains(LocalDateTime.of(monday, LocalTime.of(12, 0, 0)))) // class 2 end
        // Must include midnight rollover
        assertTrue(transitions.contains(LocalDateTime.of(monday.plusDays(1), LocalTime.of(0, 0, 5))))
    }

    @Test
    fun testComputeTransitionTimes_duringClass() {
        val from = LocalDateTime.of(monday, LocalTime.of(9, 0, 0))
        val transitions = ScheduleAutoRefreshScheduler.computeTransitionTimes(
            config = config,
            arrangements = arrangements,
            from = from,
            lookaheadDays = 1,
        )

        assertTrue(transitions.isNotEmpty())
        // Since from is 09:00, earlier transitions are filtered out.
        // Course 2 starts at 10:20, 30m before is 09:50.
        // So the next transition is 09:50 (30m milestone for class 2).
        assertEquals(LocalDateTime.of(monday, LocalTime.of(9, 50, 0)), transitions.first())
    }

    @Test
    fun testComputeTransitionTimes_afterAllClassesToday() {
        val from = LocalDateTime.of(monday, LocalTime.of(15, 0, 0))
        val transitions = ScheduleAutoRefreshScheduler.computeTransitionTimes(
            config = config,
            arrangements = arrangements,
            from = from,
            lookaheadDays = 1,
        )

        assertTrue(transitions.isNotEmpty())
        // Next transition should be midnight rollover at 00:00:05 next day
        assertEquals(LocalDateTime.of(monday.plusDays(1), LocalTime.of(0, 0, 5)), transitions.first())
    }

    @Test
    fun testComputeTransitionTimes_sundayNoClasses() {
        val sunday = monday.plusDays(6) // Sunday, no courses scheduled
        val from = LocalDateTime.of(sunday, LocalTime.of(10, 0, 0))
        val transitions = ScheduleAutoRefreshScheduler.computeTransitionTimes(
            config = config,
            arrangements = arrangements,
            from = from,
            lookaheadDays = 1,
        )

        assertTrue(transitions.isNotEmpty())
        // Next transition is Monday 00:00:05
        assertEquals(LocalDateTime.of(sunday.plusDays(1), LocalTime.of(0, 0, 5)), transitions.first())
    }

    @Test
    fun testUserPresentDebounce() {
        ScheduleAutoRefreshReceiver.resetUserPresentDebounce()
        val t0 = 100000L
        assertTrue("First user present should be handled", ScheduleAutoRefreshReceiver.shouldHandleUserPresent(t0))
        assertFalse("Immediate duplicate should be debounced", ScheduleAutoRefreshReceiver.shouldHandleUserPresent(t0 + 5000L))
        assertFalse("29s later should still be debounced", ScheduleAutoRefreshReceiver.shouldHandleUserPresent(t0 + 29000L))
        assertTrue("31s later should be allowed", ScheduleAutoRefreshReceiver.shouldHandleUserPresent(t0 + 31000L))
    }
}
