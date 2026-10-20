package com.example.mobileschedule.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseTask
import com.example.mobileschedule.data.model.CourseTaskType
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object CourseReminderScheduler {

    const val ACTION_SHOW_REMINDER = "com.example.mobileschedule.action.SHOW_COURSE_REMINDER"
    const val ACTION_SHOW_TASK_REMINDER = "com.example.mobileschedule.action.SHOW_TASK_REMINDER"

    const val EXTRA_COURSE_NAME = "extra_course_name"
    const val EXTRA_LOCATION = "extra_location"
    const val EXTRA_TEACHER = "extra_teacher"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_END_TIME = "extra_end_time"
    const val EXTRA_ARRANGEMENT_ID = "extra_arrangement_id"

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_TYPE = "extra_task_type"
    const val EXTRA_TASK_CONTENT = "extra_task_content"
    const val EXTRA_TASK_DEADLINE = "extra_task_deadline"
    const val EXTRA_COURSE_ID = "extra_course_id"

    private const val MAX_SCHEDULED_DAYS = 7
    const val REQUEST_CODE_BASE = 200000

    const val REQUEST_CODE_TASK_TWO_HOURS = 500000
    const val REQUEST_CODE_TASK_PREV_DAY = 600000

    private const val PREFS_NAME = "reminder_scheduler_prefs"
    private const val KEY_SCHEDULED_REQUEST_CODES = "scheduled_request_codes"
    private const val KEY_SCHEDULED_TASK_REQUEST_CODES = "scheduled_task_request_codes"

    enum class TaskReminderType(val baseCode: Int) {
        TWO_HOURS_BEFORE(REQUEST_CODE_TASK_TWO_HOURS),
        PREVIOUS_DAY_EVENING(REQUEST_CODE_TASK_PREV_DAY)
    }

    data class TaskReminderScheduleItem(
        val task: CourseTask,
        val reminderType: TaskReminderType,
        val triggerTimeMillis: Long,
        val requestCode: Int
    )

    private fun getSavedRequestCodes(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SCHEDULED_REQUEST_CODES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    private fun saveRequestCodes(context: Context, codes: Set<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SCHEDULED_REQUEST_CODES, codes.map { it.toString() }.toSet()).apply()
    }

    fun scheduleReminders(
        context: Context,
        config: SemesterConfig,
        arrangements: List<CourseArrangement>,
        semesterId: Long,
        advanceMinutes: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelAllReminders(context)

        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val newRequestCodes = mutableSetOf<Int>()

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        for (dayOffset in 0 until MAX_SCHEDULED_DAYS) {
            val date = today.plusDays(dayOffset.toLong())
            val position = ScheduleRules.weekPosition(config, date)
            if (position !is WeekPosition.InSemester) continue

            val week = position.week
            val dayOfWeek = date.dayOfWeek.value

            val todayCourses = arrangements.filter {
                it.semesterId == semesterId && week in it.weeks && it.dayOfWeek == dayOfWeek
            }

            for (course in todayCourses) {
                val startTime = ScheduleRules.sectionStartTime(config, course.startSection) ?: continue
                val endTime = ScheduleRules.sectionEndTime(config, course.endSection)
                val alarmTime = LocalDateTime.of(date, startTime).minusMinutes(advanceMinutes.toLong())

                if (alarmTime.isBefore(now) || alarmTime.isEqual(now)) continue

                val intent = Intent(context, CourseReminderReceiver::class.java).apply {
                    action = ACTION_SHOW_REMINDER
                    putExtra(EXTRA_COURSE_NAME, course.name)
                    putExtra(EXTRA_LOCATION, course.location ?: "")
                    putExtra(EXTRA_TEACHER, course.teacher ?: "")
                    putExtra(EXTRA_START_TIME, startTime.toString())
                    putExtra(EXTRA_END_TIME, endTime?.toString() ?: "")
                    putExtra(EXTRA_ARRANGEMENT_ID, course.id)
                }

                val requestCode = REQUEST_CODE_BASE + (dayOffset * 10000 + (course.id % 10000)).toInt()
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val triggerAtMillis = alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                try {
                    if (canExact) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                        )
                    }
                    newRequestCodes.add(requestCode)
                } catch (_: SecurityException) {
                    try {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        newRequestCodes.add(requestCode)
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
        }
        saveRequestCodes(context, newRequestCodes)
    }

    fun cancelAllReminders(
        context: Context,
        arrangements: List<CourseArrangement> = emptyList(),
        config: SemesterConfig? = null,
        semesterId: Long = 0L,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val savedCodes = getSavedRequestCodes(context)
        for (code in savedCodes) {
            val intent = Intent(context, CourseReminderReceiver::class.java).apply {
                action = ACTION_SHOW_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        saveRequestCodes(context, emptySet())
    }

    private fun getSavedTaskRequestCodes(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SCHEDULED_TASK_REQUEST_CODES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    private fun saveTaskRequestCodes(context: Context, codes: Set<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SCHEDULED_TASK_REQUEST_CODES, codes.map { it.toString() }.toSet()).apply()
    }

    /**
     * AI-C Algorithm: Calculates the schedule triggers for a task.
     * Automatically filters out completed tasks and expired deadlines.
     * Computes:
     * 1. 2 hours before deadline.
     * 2. Prior day evening (20:00) before deadline.
     */
    fun calculateTaskReminderSchedules(
        task: CourseTask,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<TaskReminderScheduleItem> {
        if (task.isCompleted || task.deadlineEpochMillis <= nowEpochMillis) {
            return emptyList()
        }

        val items = mutableListOf<TaskReminderScheduleItem>()
        val taskCodeOffset = (task.id % 99999).toInt()

        // 1. Two hours before deadline
        val twoHoursTrigger = task.deadlineEpochMillis - 2 * 3600 * 1000L
        if (twoHoursTrigger > nowEpochMillis && twoHoursTrigger < task.deadlineEpochMillis) {
            val requestCode = REQUEST_CODE_TASK_TWO_HOURS + taskCodeOffset
            items.add(
                TaskReminderScheduleItem(
                    task = task,
                    reminderType = TaskReminderType.TWO_HOURS_BEFORE,
                    triggerTimeMillis = twoHoursTrigger,
                    requestCode = requestCode
                )
            )
        }

        // 2. Prior day evening at 20:00 (跨天提前提醒)
        val deadlineDateTime = Instant.ofEpochMilli(task.deadlineEpochMillis).atZone(zoneId).toLocalDateTime()
        val prevDayEvening = LocalDateTime.of(deadlineDateTime.toLocalDate().minusDays(1), LocalTime.of(20, 0))
        val prevDayTrigger = prevDayEvening.atZone(zoneId).toInstant().toEpochMilli()

        if (prevDayTrigger > nowEpochMillis && prevDayTrigger < task.deadlineEpochMillis) {
            val requestCode = REQUEST_CODE_TASK_PREV_DAY + taskCodeOffset
            items.add(
                TaskReminderScheduleItem(
                    task = task,
                    reminderType = TaskReminderType.PREVIOUS_DAY_EVENING,
                    triggerTimeMillis = prevDayTrigger,
                    requestCode = requestCode
                )
            )
        }

        return items
    }

    /**
     * AI-B Scheduling: Schedules reminders for unfinished course tasks using AlarmManager.
     */
    fun scheduleTaskReminders(
        context: Context,
        tasks: List<CourseTask>,
        courseNames: Map<Long, String> = emptyMap(),
        nowEpochMillis: Long = System.currentTimeMillis()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelAllTaskReminders(context)

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val newRequestCodes = mutableSetOf<Int>()

        for (task in tasks) {
            val schedules = calculateTaskReminderSchedules(task, nowEpochMillis)
            val courseName = courseNames[task.courseId] ?: "课程"

            for (schedule in schedules) {
                val intent = Intent(context, CourseReminderReceiver::class.java).apply {
                    action = ACTION_SHOW_TASK_REMINDER
                    putExtra(EXTRA_TASK_ID, task.id)
                    putExtra(EXTRA_TASK_TITLE, task.title)
                    putExtra(EXTRA_TASK_TYPE, task.type.name)
                    putExtra(EXTRA_TASK_CONTENT, task.content ?: "")
                    putExtra(EXTRA_TASK_DEADLINE, task.deadlineEpochMillis)
                    putExtra(EXTRA_COURSE_ID, task.courseId)
                    putExtra(EXTRA_COURSE_NAME, courseName)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    schedule.requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (canExact) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, schedule.triggerTimeMillis, pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, schedule.triggerTimeMillis, pendingIntent
                        )
                    }
                    newRequestCodes.add(schedule.requestCode)
                } catch (_: SecurityException) {
                    try {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, schedule.triggerTimeMillis, pendingIntent)
                        newRequestCodes.add(schedule.requestCode)
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
        }
        saveTaskRequestCodes(context, newRequestCodes)
    }

    /**
     * AI-B: Cancels all scheduled task reminder alarms.
     */
    fun cancelAllTaskReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val savedCodes = getSavedTaskRequestCodes(context)
        for (code in savedCodes) {
            val intent = Intent(context, CourseReminderReceiver::class.java).apply {
                action = ACTION_SHOW_TASK_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        saveTaskRequestCodes(context, emptySet())
    }
}
