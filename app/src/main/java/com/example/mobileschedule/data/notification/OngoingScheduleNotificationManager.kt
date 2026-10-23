package com.example.mobileschedule.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mobileschedule.MainActivity
import com.example.mobileschedule.R
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.CourseStatus
import com.example.mobileschedule.data.model.TodaySchedule
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import com.example.mobileschedule.data.rules.ScheduleRules
import com.example.mobileschedule.ui.widget.ScheduleWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.example.mobileschedule.ui.widget.ScheduleAutoRefreshScheduler

/**
 * Pure data model representing the display information of the ongoing capsule notification.
 */
data class CapsuleNotificationData(
    val shouldShow: Boolean,
    val title: String,
    val contentText: String,
    val subText: String? = null,
    val targetCourseId: Long? = null,
    val chronometerTargetMillis: Long? = null,
    val isCountDown: Boolean = true,
)

/**
 * Manages the ongoing schedule notification capsule in the status bar.
 * Updates quietly (IMPORTANCE_LOW) when classes are ongoing or upcoming today,
 * and automatically dismisses when the day ends or when there are no classes.
 */
object OngoingScheduleNotificationManager {
    const val CHANNEL_ID = "schedule_ongoing_capsule"
    private const val CHANNEL_NAME = "常驻上课胶囊卡片"
    private const val CHANNEL_DESC = "当天有课时在通知栏常驻显示当前与下一节课程信息"
    const val NOTIFICATION_ID = 20261019

    @Volatile
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context)
        notifyDataChanged()
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * Pure function that derives notification presentation data from [TodaySchedule] and current time.
     */
    fun deriveCapsuleNotificationData(
        schedule: TodaySchedule,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now(),
    ): CapsuleNotificationData {
        if (schedule !is TodaySchedule.Ready) {
            return CapsuleNotificationData(
                shouldShow = false,
                title = "",
                contentText = "",
            )
        }

        if (schedule.courses.isEmpty()) {
            return CapsuleNotificationData(
                shouldShow = false,
                title = "",
                contentText = "",
            )
        }

        // Check if all courses have concluded today
        if (schedule.courses.all { it.status == CourseStatus.PAST }) {
            return CapsuleNotificationData(
                shouldShow = false,
                title = "",
                contentText = "",
            )
        }

        // Case 1: In progress course
        val currentCourse = schedule.courses.firstOrNull { it.status == CourseStatus.CURRENT }
        if (currentCourse != null) {
            val loc = currentCourse.arrangement.location?.takeIf { it.isNotBlank() } ?: "待定地点"
            val title = "正在上课：${currentCourse.arrangement.name} - $loc"

            val nextCourse = schedule.courses.firstOrNull { it != currentCourse && (it.isNext || it.status == CourseStatus.UPCOMING) }
            val contentText = if (nextCourse != null) {
                val nextLoc = nextCourse.arrangement.location?.takeIf { it.isNotBlank() } ?: "待定地点"
                "下节课：${nextCourse.arrangement.name} - $nextLoc"
            } else {
                "今日最后一节课"
            }

            val targetMillis = currentCourse.endTime?.let { endTime ->
                LocalDateTime.of(today, endTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            return CapsuleNotificationData(
                shouldShow = true,
                title = title,
                contentText = contentText,
                subText = "正在上课",
                targetCourseId = currentCourse.arrangement.id,
                chronometerTargetMillis = targetMillis,
                isCountDown = true,
            )
        }

        // Case 2: Inter-class break or pre-class upcoming course
        val nextCourse = schedule.courses.firstOrNull { it.isNext || it.status == CourseStatus.UPCOMING }
        if (nextCourse != null) {
            val loc = nextCourse.arrangement.location?.takeIf { it.isNotBlank() } ?: "待定地点"
            val minutesUntil = if (nextCourse.startTime != null && now.isBefore(nextCourse.startTime)) {
                ChronoUnit.MINUTES.between(now, nextCourse.startTime)
            } else {
                0L
            }

            val timeHint = if (minutesUntil > 0) "还有 $minutesUntil 分钟" else "即将开始"
            val title = "下一节课（$timeHint）：${nextCourse.arrangement.name} - $loc"

            val timeRange = if (nextCourse.startTime != null && nextCourse.endTime != null) {
                "${nextCourse.startTime} - ${nextCourse.endTime}"
            } else ""

            val contentText = buildString {
                if (timeRange.isNotEmpty()) append("时间：$timeRange")
                if (isNotEmpty()) append(" · ")
                append("第${nextCourse.arrangement.startSection}~${nextCourse.arrangement.endSection}节")
                if (!nextCourse.arrangement.teacher.isNullOrBlank()) {
                    append(" · ").append(nextCourse.arrangement.teacher)
                }
            }

            val targetMillis = nextCourse.startTime?.let { startTime ->
                LocalDateTime.of(today, startTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            return CapsuleNotificationData(
                shouldShow = true,
                title = title,
                contentText = contentText,
                subText = "课表胶囊",
                targetCourseId = nextCourse.arrangement.id,
                chronometerTargetMillis = targetMillis,
                isCountDown = true,
            )
        }

        return CapsuleNotificationData(
            shouldShow = false,
            title = "",
            contentText = "",
        )
    }

    /**
     * Updates the status bar notification using precomputed data.
     */
    fun updateNotification(context: Context, data: CapsuleNotificationData) {
        val notificationManager = NotificationManagerCompat.from(context)

        if (!data.shouldShow) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = ScheduleWidgetUpdater.ACTION_OPEN_TODAY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (data.targetCourseId != null) {
                putExtra(ScheduleWidgetUpdater.EXTRA_COURSE_ID, data.targetCourseId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle(data.title)
            .setContentText(data.contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(pendingIntent)

        if (!data.subText.isNullOrBlank()) {
            builder.setSubText(data.subText)
        }

        if (data.chronometerTargetMillis != null && data.chronometerTargetMillis > System.currentTimeMillis()) {
            builder.setWhen(data.chronometerTargetMillis)
            builder.setShowWhen(true)
            builder.setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(data.isCountDown)
            }
        } else {
            builder.setShowWhen(false)
            builder.setUsesChronometer(false)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Cancels the capsule notification.
     */
    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Triggered when schedule data, time, or preferences change.
     */
    fun notifyDataChanged() {
        val context = appContext ?: return
        scope.launch {
            try {
                updateFromDatabase(context)
                ScheduleAutoRefreshScheduler.scheduleNextRefresh(context)
            } catch (_: Exception) {
                // Ignore transient errors
            }
        }
    }

    /**
     * Queries database to calculate today's schedule and refresh the ongoing notification.
     */
    suspend fun updateFromDatabase(
        context: Context,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now(),
    ) {
        val prefs = AppPreferencesStore(context)
        if (!prefs.isOngoingNotificationEnabled()) {
            cancelNotification(context)
            return
        }

        val db = AppDatabase.getDatabase(context)
        val activeId = db.semesterDao().getActiveSemesterId()
        if (activeId == null) {
            cancelNotification(context)
            return
        }

        val semesterRow = db.semesterDao().getSemester(activeId)
        if (semesterRow == null) {
            cancelNotification(context)
            return
        }

        val semester = semesterRow.toModel()
        val courses = db.courseDao().getSemesterCourses(activeId).map { it.toArrangement() }

        val todaySchedule = ScheduleRules.todaySchedule(semester, courses, today, now)
        val capsuleData = deriveCapsuleNotificationData(todaySchedule, now, today)
        updateNotification(context, capsuleData)
    }
}
