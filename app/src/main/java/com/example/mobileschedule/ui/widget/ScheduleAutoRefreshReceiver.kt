package com.example.mobileschedule.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mobileschedule.data.notification.OngoingScheduleNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver responsible for waking up widgets and notifications when an exact
 * schedule transition alarm fires or when the system undergoes date/time/boot state changes.
 */
class ScheduleAutoRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                when (action) {
                    ScheduleAutoRefreshScheduler.ACTION_AUTO_REFRESH,
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                        ScheduleWidgetUpdater.updateAllWidgets(context)
                        OngoingScheduleNotificationManager.updateFromDatabase(context)
                        ScheduleAutoRefreshScheduler.scheduleNextRefreshInternal(context)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        if (shouldHandleUserPresent()) {
                            ScheduleWidgetUpdater.updateAllWidgets(context)
                            OngoingScheduleNotificationManager.updateFromDatabase(context)
                            ScheduleAutoRefreshScheduler.scheduleNextRefreshInternal(context)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        @Volatile
        private var lastUserPresentRefreshMillis = 0L
        private const val USER_PRESENT_MIN_INTERVAL_MILLIS = 30_000L // 30s debounce

        fun shouldHandleUserPresent(now: Long = System.currentTimeMillis()): Boolean {
            if (now - lastUserPresentRefreshMillis > USER_PRESENT_MIN_INTERVAL_MILLIS) {
                lastUserPresentRefreshMillis = now
                return true
            }
            return false
        }

        fun resetUserPresentDebounce() {
            lastUserPresentRefreshMillis = 0L
        }
    }
}
