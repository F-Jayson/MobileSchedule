package com.example.mobileschedule.data.reminder

import android.content.Context
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Global notifier that reschedules course reminder alarms whenever schedule data changes.
 * Mirrors [com.example.mobileschedule.ui.widget.ScheduleWidgetNotifier] in pattern.
 */
object CourseReminderNotifier {
    @Volatile
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Called whenever schedule data changes (course CRUD, semester config, import, etc.).
     * Reschedules all reminder alarms on a background thread.
     */
    fun notifyDataChanged() {
        val context = appContext ?: return
        scope.launch {
            rescheduleFromDatabase(context)
        }
        com.example.mobileschedule.data.notification.OngoingScheduleNotificationManager.notifyDataChanged()
        com.example.mobileschedule.data.dnd.ClassDndManager.notifyDataChanged()
    }

    /**
     * Loads the active semester and its courses from the database, then schedules or cancels
     * reminder alarms based on the current preference settings.
     *
     * This method is safe to call from a BroadcastReceiver (boot completed) or from the notifier.
     */
    suspend fun rescheduleFromDatabase(context: Context) {
        val prefs = AppPreferencesStore(context)
        val db = AppDatabase.getDatabase(context)
        try {
            val activeId = db.semesterDao().getActiveSemesterId()
            if (activeId == null) {
                CourseReminderScheduler.cancelAllReminders(context)
                CourseReminderScheduler.cancelAllTaskReminders(context)
                return
            }

            val semesterRow = db.semesterDao().getSemester(activeId) ?: return
            val semester = semesterRow.toModel()
            val config = semester.config
            val courseRows = db.courseDao().getSemesterCourses(activeId)
            val arrangements = courseRows.map { it.toArrangement() }
            val courseNameMap = courseRows.associate { it.course.id to it.course.name }

            if (!prefs.isReminderEnabled() || config == null) {
                CourseReminderScheduler.cancelAllReminders(context)
            } else {
                val advanceMinutes = prefs.getReminderAdvanceMinutes()
                CourseReminderScheduler.scheduleReminders(
                    context = context,
                    config = config,
                    arrangements = arrangements,
                    semesterId = activeId,
                    advanceMinutes = advanceMinutes,
                )
            }

            // Schedule task reminders for active semester
            val unfinishedEntities = db.courseTaskDao().getUnfinishedTasksForSemester(activeId)
            val unfinishedTasks = unfinishedEntities.map { it.toModel() }
            CourseReminderScheduler.scheduleTaskReminders(
                context = context,
                tasks = unfinishedTasks,
                courseNames = courseNameMap
            )
        } catch (_: Exception) {
            // Silently fail — alarms will be rescheduled on next data change
        }
    }
}
