package com.example.mobileschedule.data.dnd

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import com.example.mobileschedule.data.rules.ScheduleRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure state representation of Class DND profile.
 */
data class ClassDndProfileState(
    val isDndActive: Boolean,
    val originalRingerMode: Int,
    val originalFilter: Int,
)

/**
 * Events triggering transitions in the class DND state machine.
 */
sealed class ClassDndEvent {
    data class EnterClass(val currentRingerMode: Int, val currentFilter: Int) : ClassDndEvent()
    data object ExitClass : ClassDndEvent()
    data object ManualDisable : ClassDndEvent()
}

/**
 * Effects/actions produced by state machine transitions.
 */
sealed class ClassDndEffect {
    data class ApplySilence(val savedRingerMode: Int, val savedFilter: Int) : ClassDndEffect()
    data class RestoreProfile(val targetRingerMode: Int, val targetFilter: Int) : ClassDndEffect()
    data object None : ClassDndEffect()
}

/**
 * Pure state machine managing class DND transitions and preventing accidental overwriting
 * of the user's initial ringer mode.
 */
object ClassDndStateMachine {
    fun transition(
        currentState: ClassDndProfileState,
        event: ClassDndEvent,
    ): Pair<ClassDndProfileState, ClassDndEffect> {
        return when (event) {
            is ClassDndEvent.EnterClass -> {
                if (currentState.isDndActive) {
                    // Already in DND (e.g. back-to-back classes or re-trigger)
                    // Keep original mode! Do NOT overwrite with SILENT!
                    currentState to ClassDndEffect.None
                } else {
                    val nextState = ClassDndProfileState(
                        isDndActive = true,
                        originalRingerMode = event.currentRingerMode,
                        originalFilter = event.currentFilter,
                    )
                    nextState to ClassDndEffect.ApplySilence(
                        savedRingerMode = event.currentRingerMode,
                        savedFilter = event.currentFilter,
                    )
                }
            }
            is ClassDndEvent.ExitClass -> {
                if (currentState.isDndActive) {
                    val nextState = ClassDndProfileState(
                        isDndActive = false,
                        originalRingerMode = currentState.originalRingerMode,
                        originalFilter = currentState.originalFilter,
                    )
                    nextState to ClassDndEffect.RestoreProfile(
                        targetRingerMode = currentState.originalRingerMode,
                        targetFilter = currentState.originalFilter,
                    )
                } else {
                    currentState to ClassDndEffect.None
                }
            }
            is ClassDndEvent.ManualDisable -> {
                if (currentState.isDndActive) {
                    val nextState = ClassDndProfileState(
                        isDndActive = false,
                        originalRingerMode = currentState.originalRingerMode,
                        originalFilter = currentState.originalFilter,
                    )
                    nextState to ClassDndEffect.RestoreProfile(
                        targetRingerMode = currentState.originalRingerMode,
                        targetFilter = currentState.originalFilter,
                    )
                } else {
                    currentState to ClassDndEffect.None
                }
            }
        }
    }
}

enum class DndAlarmType { ENTER, EXIT }

data class DndScheduledAlarm(
    val type: DndAlarmType,
    val triggerDateTime: LocalDateTime,
    val courseId: Long,
    val courseName: String,
    val requestCode: Int,
)

/**
 * Pure schedule deriver calculating class DND alarm trigger timestamps and session checks.
 */
object ClassDndScheduleDeriver {
    const val REQUEST_CODE_BASE = 300000

    fun deriveAlarms(
        config: SemesterConfig,
        arrangements: List<CourseArrangement>,
        semesterId: Long,
        startDate: LocalDate,
        daysCount: Int = 7,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<DndScheduledAlarm> {
        val results = mutableListOf<DndScheduledAlarm>()
        for (dayOffset in 0 until daysCount) {
            val date = startDate.plusDays(dayOffset.toLong())
            val position = ScheduleRules.weekPosition(config, date)
            if (position !is WeekPosition.InSemester) continue

            val week = position.week
            val dayOfWeek = date.dayOfWeek.value

            val dayCourses = arrangements.filter {
                it.semesterId == semesterId && week in it.weeks && it.dayOfWeek == dayOfWeek
            }
            if (dayCourses.isEmpty()) continue // Weekend or no-class days are excluded

            for (course in dayCourses) {
                val startTime = ScheduleRules.sectionStartTime(config, course.startSection) ?: continue
                val endTime = ScheduleRules.sectionEndTime(config, course.endSection) ?: continue

                val enterDateTime = LocalDateTime.of(date, startTime)
                val exitDateTime = LocalDateTime.of(date, endTime)

                val enterCode = REQUEST_CODE_BASE + (dayOffset * 20000 + ((course.id % 10000) * 2)).toInt()
                val exitCode = REQUEST_CODE_BASE + (dayOffset * 20000 + ((course.id % 10000) * 2) + 1).toInt()

                if (enterDateTime.isAfter(now)) {
                    results.add(
                        DndScheduledAlarm(
                            type = DndAlarmType.ENTER,
                            triggerDateTime = enterDateTime,
                            courseId = course.id,
                            courseName = course.name,
                            requestCode = enterCode,
                        )
                    )
                }
                if (exitDateTime.isAfter(now)) {
                    results.add(
                        DndScheduledAlarm(
                            type = DndAlarmType.EXIT,
                            triggerDateTime = exitDateTime,
                            courseId = course.id,
                            courseName = course.name,
                            requestCode = exitCode,
                        )
                    )
                }
            }
        }
        return results.sortedBy { it.triggerDateTime }
    }

    /**
     * Pure function to determine if any course is active in session at the specified date and time.
     */
    fun isAnyCourseInSession(
        config: SemesterConfig,
        arrangements: List<CourseArrangement>,
        semesterId: Long,
        date: LocalDate,
        time: LocalTime,
    ): Boolean {
        val position = ScheduleRules.weekPosition(config, date)
        if (position !is WeekPosition.InSemester) return false

        val week = position.week
        val dayOfWeek = date.dayOfWeek.value

        return arrangements.any { course ->
            if (course.semesterId != semesterId || week !in course.weeks || course.dayOfWeek != dayOfWeek) {
                false
            } else {
                val startTime = ScheduleRules.sectionStartTime(config, course.startSection) ?: return@any false
                val endTime = ScheduleRules.sectionEndTime(config, course.endSection) ?: return@any false
                !time.isBefore(startTime) && time.isBefore(endTime)
            }
        }
    }
}

/**
 * System DND manager handling alarm scheduling, Android notification policy access,
 * and audio ringer mode manipulation during class hours.
 */
object ClassDndManager {
    const val ACTION_ENTER_CLASS_DND = "com.example.mobileschedule.action.ENTER_CLASS_DND"
    const val ACTION_EXIT_CLASS_DND = "com.example.mobileschedule.action.EXIT_CLASS_DND"

    private const val PREFS_NAME = "class_dnd_prefs"
    private const val KEY_IS_DND_ACTIVE = "is_dnd_active"
    private const val KEY_SAVED_RINGER_MODE = "saved_ringer_mode"
    private const val KEY_SAVED_FILTER = "saved_filter"
    private const val KEY_SCHEDULED_REQUEST_CODES = "scheduled_dnd_request_codes"

    @Volatile
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        appContext = context.applicationContext
        notifyDataChanged()
    }

    fun isNotificationPolicyAccessGranted(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun openPolicyAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getProfileState(context: Context): ClassDndProfileState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ClassDndProfileState(
            isDndActive = prefs.getBoolean(KEY_IS_DND_ACTIVE, false),
            originalRingerMode = prefs.getInt(KEY_SAVED_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL),
            originalFilter = prefs.getInt(KEY_SAVED_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL),
        )
    }

    fun saveProfileState(context: Context, state: ClassDndProfileState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_DND_ACTIVE, state.isDndActive)
            .putInt(KEY_SAVED_RINGER_MODE, state.originalRingerMode)
            .putInt(KEY_SAVED_FILTER, state.originalFilter)
            .apply()
    }

    private fun getSavedRequestCodes(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SCHEDULED_REQUEST_CODES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    private fun saveSavedRequestCodes(context: Context, codes: Set<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_REQUEST_CODES, codes.map { it.toString() }.toSet())
            .apply()
    }

    fun notifyDataChanged() {
        val context = appContext ?: return
        scope.launch {
            try {
                rescheduleFromDatabase(context)
            } catch (_: Exception) {
                // Ignore transient errors
            }
        }
    }

    fun handleEnterClass(context: Context) {
        val prefsStore = AppPreferencesStore(context)
        if (!prefsStore.isClassDndEnabled()) return
        if (!isNotificationPolicyAccessGranted(context)) return

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val am = context.getSystemService(AudioManager::class.java) ?: return

        val currentState = getProfileState(context)
        val currentRinger = am.ringerMode
        val currentFilter = nm.currentInterruptionFilter

        val (nextState, effect) = ClassDndStateMachine.transition(
            currentState,
            ClassDndEvent.EnterClass(currentRinger, currentFilter),
        )

        if (effect is ClassDndEffect.ApplySilence) {
            saveProfileState(context, nextState)
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (_: SecurityException) {
            }
        }
    }

    suspend fun handleExitClass(context: Context) {
        val prefsStore = AppPreferencesStore(context)
        if (!prefsStore.isClassDndEnabled()) return

        // Verify if another course is currently in session right now
        val db = AppDatabase.getDatabase(context)
        val activeId = db.semesterDao().getActiveSemesterId()
        if (activeId != null) {
            val semesterRow = db.semesterDao().getSemester(activeId)
            val config = semesterRow?.toModel()?.config
            if (config != null) {
                val courses = db.courseDao().getSemesterCourses(activeId).map { it.toArrangement() }
                val nowTime = LocalTime.now()
                val todayDate = LocalDate.now()
                if (ClassDndScheduleDeriver.isAnyCourseInSession(config, courses, activeId, todayDate, nowTime)) {
                    // Still in session for another course, retain DND
                    return
                }
            }
        }

        if (!isNotificationPolicyAccessGranted(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val am = context.getSystemService(AudioManager::class.java) ?: return

        val currentState = getProfileState(context)
        val (nextState, effect) = ClassDndStateMachine.transition(currentState, ClassDndEvent.ExitClass)

        if (effect is ClassDndEffect.RestoreProfile) {
            saveProfileState(context, nextState)
            try {
                nm.setInterruptionFilter(effect.targetFilter)
                am.ringerMode = effect.targetRingerMode
            } catch (_: SecurityException) {
            }
        }
    }

    fun onSettingDisabled(context: Context) {
        cancelAllAlarms(context)

        val currentState = getProfileState(context)
        if (currentState.isDndActive) {
            val (nextState, effect) = ClassDndStateMachine.transition(currentState, ClassDndEvent.ManualDisable)
            saveProfileState(context, nextState)

            if (effect is ClassDndEffect.RestoreProfile && isNotificationPolicyAccessGranted(context)) {
                val nm = context.getSystemService(NotificationManager::class.java)
                val am = context.getSystemService(AudioManager::class.java)
                try {
                    nm?.setInterruptionFilter(effect.targetFilter)
                    am?.ringerMode = effect.targetRingerMode
                } catch (_: SecurityException) {
                }
            }
        }
    }

    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val codes = getSavedRequestCodes(context)
        for (code in codes) {
            val enterIntent = Intent(context, ClassDndReceiver::class.java).apply { action = ACTION_ENTER_CLASS_DND }
            val enterPi = PendingIntent.getBroadcast(
                context, code, enterIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (enterPi != null) {
                alarmManager.cancel(enterPi)
                enterPi.cancel()
            }

            val exitIntent = Intent(context, ClassDndReceiver::class.java).apply { action = ACTION_EXIT_CLASS_DND }
            val exitPi = PendingIntent.getBroadcast(
                context, code, exitIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (exitPi != null) {
                alarmManager.cancel(exitPi)
                exitPi.cancel()
            }
        }
        saveSavedRequestCodes(context, emptySet())
    }

    suspend fun rescheduleFromDatabase(context: Context) {
        val prefsStore = AppPreferencesStore(context)
        if (!prefsStore.isClassDndEnabled() || !isNotificationPolicyAccessGranted(context)) {
            cancelAllAlarms(context)
            return
        }

        val db = AppDatabase.getDatabase(context)
        val activeId = db.semesterDao().getActiveSemesterId()
        if (activeId == null) {
            cancelAllAlarms(context)
            return
        }

        val semesterRow = db.semesterDao().getSemester(activeId)
        if (semesterRow == null) {
            cancelAllAlarms(context)
            return
        }

        val semester = semesterRow.toModel()
        val config = semester.config
        if (config == null) {
            cancelAllAlarms(context)
            return
        }
        val courses = db.courseDao().getSemesterCourses(activeId).map { it.toArrangement() }

        val alarms = ClassDndScheduleDeriver.deriveAlarms(
            config = config,
            arrangements = courses,
            semesterId = activeId,
            startDate = LocalDate.now(),
            daysCount = 7,
            now = LocalDateTime.now(),
        )

        scheduleAlarms(context, alarms)
    }

    private fun scheduleAlarms(context: Context, alarms: List<DndScheduledAlarm>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancelAllAlarms(context)

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val newRequestCodes = mutableSetOf<Int>()

        for (alarm in alarms) {
            val action = if (alarm.type == DndAlarmType.ENTER) ACTION_ENTER_CLASS_DND else ACTION_EXIT_CLASS_DND
            val intent = Intent(context, ClassDndReceiver::class.java).apply {
                this.action = action
                putExtra("extra_course_id", alarm.courseId)
                putExtra("extra_course_name", alarm.courseName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val triggerAtMillis = alarm.triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
                newRequestCodes.add(alarm.requestCode)
            } catch (_: SecurityException) {
            }
        }

        saveSavedRequestCodes(context, newRequestCodes)
    }
}
