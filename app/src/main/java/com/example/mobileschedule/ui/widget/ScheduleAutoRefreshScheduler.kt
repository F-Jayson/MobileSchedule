package com.example.mobileschedule.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.rules.ScheduleRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Precision event scheduler that schedules exact alarms at critical course transition points
 * (course start, course end, pre-class milestones, and midnight date rollover).
 * This ensures desktop widgets and status bar notifications update in real-time
 * without requiring the user to open the application.
 */
object ScheduleAutoRefreshScheduler {

    const val ACTION_AUTO_REFRESH = "com.example.mobileschedule.action.AUTO_REFRESH_SCHEDULE"
    const val REQUEST_CODE_AUTO_REFRESH = 900101

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Pure function that calculates all upcoming transition moments within the lookahead window.
     * Transitions include:
     * - Course start times
     * - Course end times
     * - Pre-class countdown refresh milestones (30m, 15m, 5m before start)
     * - Midnight date rollovers (00:00:05 next day)
     */
    fun computeTransitionTimes(
        config: SemesterConfig,
        arrangements: List<CourseArrangement>,
        from: LocalDateTime,
        lookaheadDays: Int = 1,
    ): List<LocalDateTime> {
        val transitions = mutableSetOf<LocalDateTime>()
        val fromDate = from.toLocalDate()

        for (dayOffset in 0..lookaheadDays) {
            val date = fromDate.plusDays(dayOffset.toLong())
            val position = ScheduleRules.weekPosition(config, date)
            if (position is WeekPosition.InSemester) {
                val week = position.week
                val dayOfWeek = date.dayOfWeek.value
                val dayCourses = arrangements.filter { course ->
                    course.dayOfWeek == dayOfWeek && week in course.weeks
                }

                for (course in dayCourses) {
                    val startTime = ScheduleRules.sectionStartTime(config, course.startSection)
                    val endTime = ScheduleRules.sectionEndTime(config, course.endSection)

                    if (startTime != null) {
                        val startDt = LocalDateTime.of(date, startTime)
                        transitions.add(startDt)

                        // Milestone countdown refreshes before class
                        listOf(30L, 15L, 5L).forEach { minutesBefore ->
                            val preDt = startDt.minusMinutes(minutesBefore)
                            if (preDt.toLocalDate() == date) {
                                transitions.add(preDt)
                            }
                        }
                    }

                    if (endTime != null) {
                        val endDt = LocalDateTime.of(date, endTime)
                        transitions.add(endDt)
                    }
                }
            }

            // Midnight rollover to cleanly switch the date at 00:00:05
            transitions.add(LocalDateTime.of(date.plusDays(1), LocalTime.of(0, 0, 5)))
        }

        val threshold = from.plusSeconds(2)
        return transitions.filter { it.isAfter(threshold) }.sorted()
    }

    /**
     * Asynchronously calculates and sets the next exact alarm.
     */
    fun scheduleNextRefresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                scheduleNextRefreshInternal(appContext)
            } catch (_: Exception) {
                // Ignore transient errors
            }
        }
    }

    /**
     * Internal implementation of exact alarm scheduling using [AlarmManager].
     */
    suspend fun scheduleNextRefreshInternal(
        context: Context,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val db = AppDatabase.getDatabase(context)
        val activeId = db.semesterDao().getActiveSemesterId()

        val nextTime: LocalDateTime = if (activeId != null) {
            val semesterRow = db.semesterDao().getSemester(activeId)
            val config = semesterRow?.toModel()?.config
            if (config != null) {
                val courses = db.courseDao().getSemesterCourses(activeId).map { it.toArrangement() }
                computeTransitionTimes(config, courses, now).firstOrNull()
            } else {
                null
            }
        } else {
            null
        } ?: LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(0, 0, 5))

        val triggerMillis = nextTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, ScheduleAutoRefreshReceiver::class.java).apply {
            action = ACTION_AUTO_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_AUTO_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    /**
     * Cancels any pending auto-refresh alarm.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleAutoRefreshReceiver::class.java).apply {
            action = ACTION_AUTO_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_AUTO_REFRESH,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
