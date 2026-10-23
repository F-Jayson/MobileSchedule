package com.example.mobileschedule.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Standard AppWidgetProvider for the 4x2 schedule desktop widget.
 * Listens to system date/time change broadcasts and application schedule updates.
 */
class ScheduleAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleWidgetUpdater.updateAllWidgets(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleWidgetWorker.enqueuePeriodicWork(context)
        ScheduleAutoRefreshScheduler.scheduleNextRefresh(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleWidgetUpdater.updateAllWidgets(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ScheduleWidgetWorker.cancelWork(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            ACTION_SCHEDULE_CHANGED,
            ACTION_REFRESH_WIDGET -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ScheduleWidgetUpdater.updateAllWidgets(context)
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE_CHANGED = "com.example.mobileschedule.action.SCHEDULE_CHANGED"
        const val ACTION_REFRESH_WIDGET = "com.example.mobileschedule.action.REFRESH_WIDGET"

        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(context, ScheduleAppWidgetProvider::class.java).apply {
                action = ACTION_SCHEDULE_CHANGED
            }
            context.sendBroadcast(intent)
        }
    }
}
