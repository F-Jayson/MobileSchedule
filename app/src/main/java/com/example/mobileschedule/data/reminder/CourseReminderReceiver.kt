package com.example.mobileschedule.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mobileschedule.MainActivity
import com.example.mobileschedule.R
import com.example.mobileschedule.ui.widget.ScheduleWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CourseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CourseReminderScheduler.ACTION_SHOW_REMINDER -> {
                showNotification(context, intent)
            }
            CourseReminderScheduler.ACTION_SHOW_TASK_REMINDER -> {
                showTaskNotification(context, intent)
            }
            Intent.ACTION_BOOT_COMPLETED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        CourseReminderNotifier.rescheduleFromDatabase(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun showNotification(context: Context, intent: Intent) {
        ensureChannel(context)

        val courseName = intent.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_NAME) ?: "课程"
        val location = intent.getStringExtra(CourseReminderScheduler.EXTRA_LOCATION) ?: ""
        val teacher = intent.getStringExtra(CourseReminderScheduler.EXTRA_TEACHER) ?: ""
        val startTime = intent.getStringExtra(CourseReminderScheduler.EXTRA_START_TIME) ?: ""
        val endTime = intent.getStringExtra(CourseReminderScheduler.EXTRA_END_TIME) ?: ""
        val arrangementId = intent.getLongExtra(CourseReminderScheduler.EXTRA_ARRANGEMENT_ID, 0L)

        val contentText = buildString {
            if (startTime.isNotEmpty()) {
                append(startTime)
                if (endTime.isNotEmpty()) append(" - $endTime")
            }
            if (location.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(location)
            }
            if (teacher.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(teacher)
            }
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = ScheduleWidgetUpdater.ACTION_OPEN_TODAY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ScheduleWidgetUpdater.EXTRA_COURSE_ID, arrangementId)
        }
        val tapPending = PendingIntent.getActivity(
            context, arrangementId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle("即将上课：$courseName")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // Check notification permission for API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notificationId = (arrangementId % Int.MAX_VALUE).toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun showTaskNotification(context: Context, intent: Intent) {
        ensureTaskChannel(context)

        val courseName = intent.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_NAME) ?: "课程"
        val courseId = intent.getLongExtra(CourseReminderScheduler.EXTRA_COURSE_ID, 0L)
        val taskId = intent.getLongExtra(CourseReminderScheduler.EXTRA_TASK_ID, 0L)
        val taskTitle = intent.getStringExtra(CourseReminderScheduler.EXTRA_TASK_TITLE) ?: "待办任务"
        val taskTypeStr = intent.getStringExtra(CourseReminderScheduler.EXTRA_TASK_TYPE) ?: "HOMEWORK"
        val taskContent = intent.getStringExtra(CourseReminderScheduler.EXTRA_TASK_CONTENT) ?: ""
        val deadlineMillis = intent.getLongExtra(CourseReminderScheduler.EXTRA_TASK_DEADLINE, 0L)

        val typeLabel = when (taskTypeStr) {
            "EXAM" -> "考试"
            "EXPERIMENT" -> "实验"
            "CUSTOM" -> "待办"
            else -> "作业"
        }

        val now = System.currentTimeMillis()
        val diff = deadlineMillis - now
        val hoursLeft = if (diff <= 0) {
            "现在"
        } else {
            val hours = (diff + 30 * 60 * 1000L) / (3600 * 1000L)
            if (hours <= 0) {
                val minutes = maxOf(1L, diff / (60 * 1000L))
                "$minutes 分钟"
            } else {
                "$hours 小时"
            }
        }

        val title = "【${typeLabel}提醒】《$courseName》"
        val actionPrompt = if (taskTypeStr == "EXAM") "请及时复习！" else "请及时提交！"
        val contentText = "【$taskTitle】即将于 $hoursLeft 后截止，$actionPrompt"
        val bigText = if (taskContent.isNotBlank()) "$contentText\n备注：$taskContent" else contentText

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = ScheduleWidgetUpdater.ACTION_OPEN_TODAY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ScheduleWidgetUpdater.EXTRA_COURSE_ID, courseId)
        }
        val tapPending = PendingIntent.getActivity(
            context,
            (700000 + (taskId % 100000)).toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, TASK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notificationId = (700000 + (taskId % 100000)).toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "course_reminder_channel"
        private const val CHANNEL_NAME = "课前提醒"
        private const val CHANNEL_DESC = "在课程开始前发送提醒通知"

        const val TASK_CHANNEL_ID = "task_reminder_channel"
        private const val TASK_CHANNEL_NAME = "待办与考试提醒"
        private const val TASK_CHANNEL_DESC = "在课程待办、作业与考试到期前发送提醒通知"

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        fun ensureTaskChannel(context: Context) {
            val channel = NotificationChannel(
                TASK_CHANNEL_ID, TASK_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = TASK_CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
