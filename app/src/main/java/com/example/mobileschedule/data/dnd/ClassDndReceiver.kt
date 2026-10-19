package com.example.mobileschedule.data.dnd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager alarms for class start/end times,
 * and system boot / exact alarm permission changes to reschedule alarms.
 */
class ClassDndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ClassDndManager.ACTION_ENTER_CLASS_DND -> {
                ClassDndManager.handleEnterClass(context)
            }
            ClassDndManager.ACTION_EXIT_CLASS_DND -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        ClassDndManager.handleExitClass(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        ClassDndManager.rescheduleFromDatabase(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
