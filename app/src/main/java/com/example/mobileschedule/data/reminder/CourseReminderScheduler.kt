package com.example.mobileschedule.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object CourseReminderScheduler {

    const val ACTION_SHOW_REMINDER = "com.example.mobileschedule.action.SHOW_COURSE_REMINDER"
    const val EXTRA_COURSE_NAME = "extra_course_name"
    const val EXTRA_LOCATION = "extra_location"
    const val EXTRA_TEACHER = "extra_teacher"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_END_TIME = "extra_end_time"
    const val EXTRA_ARRANGEMENT_ID = "extra_arrangement_id"
    private const val MAX_SCHEDULED_DAYS = 7
    private const val REQUEST_CODE_BASE = 200000
    private const val PREFS_NAME = "reminder_scheduler_prefs"
    private const val KEY_SCHEDULED_REQUEST_CODES = "scheduled_request_codes"

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
}
