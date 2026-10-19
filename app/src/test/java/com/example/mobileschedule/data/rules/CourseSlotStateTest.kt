package com.example.mobileschedule.data.rules

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SemesterConfig
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSlotStateTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(monday, 20, 12, emptyList(), 1)

    @Test
    fun oddWeekCourseIdentifiedCorrectlyAcrossActiveAndInactiveWeeks() {
        // 单周课程：1, 3, 5, 7, 9
        val oddWeeks = setOf(1, 3, 5, 7, 9)

        // 在单周（1, 3, 5, 7, 9）应为 ACTIVE
        listOf(1, 3, 5, 7, 9).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, oddWeeks)
            assertEquals("Week $week should be ACTIVE", CourseSlotState.ACTIVE, state)
            assertTrue(state.isActive)
        }

        // 在双周（2, 4, 6, 8, 10）应为 INACTIVE，带区间标注
        listOf(2, 4, 6, 8, 10).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, oddWeeks)
            assertTrue("Week $week should be INACTIVE", state is CourseSlotState.INACTIVE)
            assertFalse(state.isActive)
            val inactive = state as CourseSlotState.INACTIVE
            assertEquals("第1~9周(单)", inactive.weekDescription)
            assertEquals("第1~9周(单)上课（非本周）", inactive.notice)
            assertEquals(oddWeeks, inactive.weeks)
        }
    }

    @Test
    fun evenWeekCourseIdentifiedCorrectlyAcrossActiveAndInactiveWeeks() {
        // 双周课程：2, 4, 6, 8, 10
        val evenWeeks = setOf(2, 4, 6, 8, 10)

        // 在双周（2, 4, 6, 8, 10）应为 ACTIVE
        listOf(2, 4, 6, 8, 10).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, evenWeeks)
            assertEquals("Week $week should be ACTIVE", CourseSlotState.ACTIVE, state)
            assertTrue(state.isActive)
        }

        // 在单周（1, 3, 5, 7, 9, 11）应为 INACTIVE
        listOf(1, 3, 5, 7, 9, 11).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, evenWeeks)
            assertTrue("Week $week should be INACTIVE", state is CourseSlotState.INACTIVE)
            assertFalse(state.isActive)
            val inactive = state as CourseSlotState.INACTIVE
            assertEquals("第2~10周(双)", inactive.weekDescription)
            assertEquals("第2~10周(双)上课（非本周）", inactive.notice)
            assertEquals(evenWeeks, inactive.weeks)
        }
    }

    @Test
    fun continuousWeeksIdentifiedCorrectly() {
        // 连续周课程：1..8
        val continuousWeeks = (1..8).toSet()

        // 1..8 周为 ACTIVE
        (1..8).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, continuousWeeks)
            assertEquals(CourseSlotState.ACTIVE, state)
            assertTrue(state.isActive)
        }

        // 9..16 周为 INACTIVE
        (9..16).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, continuousWeeks)
            assertTrue(state is CourseSlotState.INACTIVE)
            val inactive = state as CourseSlotState.INACTIVE
            assertEquals("第1~8周", inactive.weekDescription)
            assertEquals("第1~8周上课（非本周）", inactive.notice)
        }
    }

    @Test
    fun jumpWeeksIdentifiedCorrectly() {
        // 跳周课程：1, 2, 5, 6
        val jumpWeeks = setOf(1, 2, 5, 6)

        listOf(1, 2, 5, 6).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, jumpWeeks)
            assertEquals("Week $week should be ACTIVE", CourseSlotState.ACTIVE, state)
        }

        listOf(3, 4, 7, 8).forEach { week ->
            val state = ScheduleRules.determineCourseSlotState(week, jumpWeeks)
            assertTrue("Week $week should be INACTIVE", state is CourseSlotState.INACTIVE)
            val inactive = state as CourseSlotState.INACTIVE
            assertEquals("第1~2周、第5~6周", inactive.weekDescription)
            assertEquals("第1~2周、第5~6周上课（非本周）", inactive.notice)
        }
    }

    @Test
    fun singleWeekCourseIdentifiedCorrectly() {
        val singleWeek = setOf(3)
        assertEquals(CourseSlotState.ACTIVE, ScheduleRules.determineCourseSlotState(3, singleWeek))

        val inactive = ScheduleRules.determineCourseSlotState(4, singleWeek) as CourseSlotState.INACTIVE
        assertEquals("第3周", inactive.weekDescription)
        assertEquals("第3周上课（非本周）", inactive.notice)
    }

    @Test
    fun discreteSingleWeeksIdentifiedCorrectly() {
        val discrete = setOf(1, 3, 6)
        val inactive = ScheduleRules.determineCourseSlotState(2, discrete) as CourseSlotState.INACTIVE
        assertEquals("第1周、第3周、第6周", inactive.weekDescription)
    }

    @Test
    fun queryDayCoursesIdentifiesActiveAndInactiveSlotsAccurately() {
        val math = course(1, "高等数学", day = 2, start = 1, end = 2, weeks = setOf(1, 3, 5, 7, 9))
        val english = course(2, "大学英语", day = 2, start = 3, end = 4, weeks = setOf(2, 4, 6, 8))
        val physics = course(3, "大学物理", day = 2, start = 5, end = 6, weeks = (1..16).toSet())
        val history = course(4, "近代史", day = 3, start = 1, end = 2, weeks = (1..16).toSet()) // 周三课程

        val arrangements = listOf(math, english, physics, history)

        // 第 2 周查询周二课程
        val tuesdayWeek2 = ScheduleRules.dayCourses(1, dayOfWeek = 2, week = 2, arrangements = arrangements)
        assertEquals(3, tuesdayWeek2.size)

        // 高数在第 2 周是非本周
        assertEquals(1L, tuesdayWeek2[0].arrangement.id)
        assertFalse(tuesdayWeek2[0].isActive)
        assertTrue(tuesdayWeek2[0].isNonCurrentWeek)
        assertEquals("第1~9周(单)", (tuesdayWeek2[0].state as CourseSlotState.INACTIVE).weekDescription)

        // 英语在第 2 周是本周活跃
        assertEquals(2L, tuesdayWeek2[1].arrangement.id)
        assertTrue(tuesdayWeek2[1].isActive)
        assertEquals(CourseSlotState.ACTIVE, tuesdayWeek2[1].state)

        // 物理在第 2 周是本周活跃
        assertEquals(3L, tuesdayWeek2[2].arrangement.id)
        assertTrue(tuesdayWeek2[2].isActive)
        assertEquals(CourseSlotState.ACTIVE, tuesdayWeek2[2].state)

        // 第 3 周查询周二课程
        val tuesdayWeek3 = ScheduleRules.dayCourses(1, dayOfWeek = 2, week = 3, arrangements = arrangements)
        assertEquals(3, tuesdayWeek3.size)
        assertTrue(tuesdayWeek3[0].isActive) // 高数 active
        assertFalse(tuesdayWeek3[1].isActive) // 英语 inactive
        assertEquals("第2~8周(双)", (tuesdayWeek3[1].state as CourseSlotState.INACTIVE).weekDescription)
        assertTrue(tuesdayWeek3[2].isActive) // 物理 active
    }

    @Test
    fun weekCoursesRespectsIncludeNonCurrentWeekParameter() {
        val c1 = course(1, "本周课", day = 1, start = 1, end = 2, weeks = setOf(1, 2))
        val c2 = course(2, "非本周课", day = 1, start = 3, end = 4, weeks = setOf(3, 4))
        val list = listOf(c1, c2)

        val activeOnly = ScheduleRules.weekCourses(1, week = 1, arrangements = list, includeNonCurrentWeek = false)
        assertEquals(1, activeOnly.size)
        assertEquals(1L, activeOnly[0].arrangement.id)
        assertTrue(activeOnly[0].isActive)

        val allCourses = ScheduleRules.weekCourses(1, week = 1, arrangements = list, includeNonCurrentWeek = true)
        assertEquals(2, allCourses.size)
        assertTrue(allCourses[0].isActive)
        assertFalse(allCourses[1].isActive)
    }

    @Test
    fun weekSchedulePopulatesBothActiveAndAllArrangements() {
        val c1 = course(1, "本周课", day = 1, start = 1, end = 2, weeks = setOf(2))
        val c2 = course(2, "下周课", day = 1, start = 3, end = 4, weeks = setOf(3))
        val c3 = course(3, "其他学期", day = 1, start = 1, end = 2, weeks = setOf(2), semesterId = 99)

        val schedule = ScheduleRules.weekSchedule(1, config, 2, listOf(c1, c2, c3))
        assertEquals(1, schedule.arrangements.size)
        assertEquals(1L, schedule.arrangements[0].id)

        assertEquals(2, schedule.allArrangements.size)
        assertEquals(listOf(1L, 2L), schedule.allArrangements.map { it.id })
    }

    private fun course(
        id: Long,
        name: String,
        day: Int,
        start: Int,
        end: Int,
        weeks: Set<Int>,
        semesterId: Long = 1,
    ) = CourseArrangement(
        id = id,
        semesterId = semesterId,
        name = name,
        teacher = null,
        location = null,
        dayOfWeek = day,
        startSection = start,
        endSection = end,
        weeks = weeks,
        origin = CourseOrigin.Manual,
    )
}
