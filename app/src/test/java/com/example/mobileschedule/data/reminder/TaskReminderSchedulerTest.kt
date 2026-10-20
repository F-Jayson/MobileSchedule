package com.example.mobileschedule.data.reminder

import com.example.mobileschedule.data.model.CourseTask
import com.example.mobileschedule.data.model.CourseTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaskReminderSchedulerTest {

    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun calculateTaskReminderSchedules_generatesBothPriorDayAndTwoHoursBefore() {
        // Deadline: 2026-10-22 10:00
        val deadline = LocalDateTime.of(2026, 10, 22, 10, 0)
        val deadlineMillis = deadline.atZone(zoneId).toInstant().toEpochMilli()

        val task = CourseTask(
            id = 42L,
            courseId = 101L,
            title = "编译原理大作业",
            deadlineEpochMillis = deadlineMillis,
            type = CourseTaskType.HOMEWORK,
            isCompleted = false
        )

        // Now: 2026-10-20 15:00 (Two days before deadline)
        val now = LocalDateTime.of(2026, 10, 20, 15, 0)
        val nowMillis = now.atZone(zoneId).toInstant().toEpochMilli()

        val schedules = CourseReminderScheduler.calculateTaskReminderSchedules(
            task = task,
            nowEpochMillis = nowMillis,
            zoneId = zoneId
        )

        assertEquals(2, schedules.size)

        val twoHoursSchedule = schedules.first { it.reminderType == CourseReminderScheduler.TaskReminderType.TWO_HOURS_BEFORE }
        val expectedTwoHours = LocalDateTime.of(2026, 10, 22, 8, 0).atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expectedTwoHours, twoHoursSchedule.triggerTimeMillis)
        assertEquals(CourseReminderScheduler.REQUEST_CODE_TASK_TWO_HOURS + 42, twoHoursSchedule.requestCode)

        // 跨天提前提醒 (前1天晚上 20:00)
        val prevDaySchedule = schedules.first { it.reminderType == CourseReminderScheduler.TaskReminderType.PREVIOUS_DAY_EVENING }
        val expectedPrevDay = LocalDateTime.of(2026, 10, 21, 20, 0).atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expectedPrevDay, prevDaySchedule.triggerTimeMillis)
        assertEquals(CourseReminderScheduler.REQUEST_CODE_TASK_PREV_DAY + 42, prevDaySchedule.requestCode)
    }

    @Test
    fun calculateTaskReminderSchedules_skipsPriorDayWhenAlreadyPassed() {
        // Deadline: 2026-10-22 10:00
        val deadline = LocalDateTime.of(2026, 10, 22, 10, 0)
        val deadlineMillis = deadline.atZone(zoneId).toInstant().toEpochMilli()

        val task = CourseTask(
            id = 7L,
            courseId = 101L,
            title = "期末考试复习",
            deadlineEpochMillis = deadlineMillis,
            type = CourseTaskType.EXAM,
            isCompleted = false
        )

        // Now: 2026-10-21 21:00 (Prior-day evening 20:00 has already passed)
        val now = LocalDateTime.of(2026, 10, 21, 21, 0)
        val nowMillis = now.atZone(zoneId).toInstant().toEpochMilli()

        val schedules = CourseReminderScheduler.calculateTaskReminderSchedules(
            task = task,
            nowEpochMillis = nowMillis,
            zoneId = zoneId
        )

        assertEquals(1, schedules.size)
        val twoHoursSchedule = schedules[0]
        assertEquals(CourseReminderScheduler.TaskReminderType.TWO_HOURS_BEFORE, twoHoursSchedule.reminderType)
        val expectedTwoHours = LocalDateTime.of(2026, 10, 22, 8, 0).atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expectedTwoHours, twoHoursSchedule.triggerTimeMillis)
    }

    @Test
    fun calculateTaskReminderSchedules_completedTaskReturnsEmpty() {
        val deadline = LocalDateTime.of(2026, 10, 22, 10, 0)
        val deadlineMillis = deadline.atZone(zoneId).toInstant().toEpochMilli()

        val task = CourseTask(
            id = 15L,
            courseId = 101L,
            title = "已提交的实验报告",
            deadlineEpochMillis = deadlineMillis,
            type = CourseTaskType.EXPERIMENT,
            isCompleted = true // 已完成
        )

        val now = LocalDateTime.of(2026, 10, 20, 10, 0)
        val nowMillis = now.atZone(zoneId).toInstant().toEpochMilli()

        val schedules = CourseReminderScheduler.calculateTaskReminderSchedules(
            task = task,
            nowEpochMillis = nowMillis,
            zoneId = zoneId
        )

        // 已完成任务取消调度并过滤
        assertTrue(schedules.isEmpty())
    }

    @Test
    fun calculateTaskReminderSchedules_expiredDeadlineReturnsEmpty() {
        val deadline = LocalDateTime.of(2026, 10, 20, 10, 0)
        val deadlineMillis = deadline.atZone(zoneId).toInstant().toEpochMilli()

        val task = CourseTask(
            id = 18L,
            courseId = 101L,
            title = "已过期的日常任务",
            deadlineEpochMillis = deadlineMillis,
            type = CourseTaskType.CUSTOM,
            isCompleted = false
        )

        // Now is after deadline
        val now = LocalDateTime.of(2026, 10, 20, 11, 0)
        val nowMillis = now.atZone(zoneId).toInstant().toEpochMilli()

        val schedules = CourseReminderScheduler.calculateTaskReminderSchedules(
            task = task,
            nowEpochMillis = nowMillis,
            zoneId = zoneId
        )

        assertTrue(schedules.isEmpty())
    }

    @Test
    fun requestCodeIsolation_doesNotCollideWithCourseRemindersOrDnd() {
        val taskIds = listOf(1L, 100L, 9999L, 123456L)
        val courseReminderRange = 200000 until 300000
        val dndRange = 300000 until 500000

        for (id in taskIds) {
            val twoHoursCode = CourseReminderScheduler.REQUEST_CODE_TASK_TWO_HOURS + (id % 99999).toInt()
            val prevDayCode = CourseReminderScheduler.REQUEST_CODE_TASK_PREV_DAY + (id % 99999).toInt()

            assertTrue(twoHoursCode >= 500000 && twoHoursCode < 600000)
            assertTrue(prevDayCode >= 600000 && prevDayCode < 700000)

            assertTrue(twoHoursCode !in courseReminderRange)
            assertTrue(twoHoursCode !in dndRange)
            assertTrue(prevDayCode !in courseReminderRange)
            assertTrue(prevDayCode !in dndRange)
        }
    }
}
