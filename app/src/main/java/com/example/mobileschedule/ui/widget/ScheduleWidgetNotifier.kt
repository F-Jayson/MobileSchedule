package com.example.mobileschedule.ui.widget

import android.content.Context

/**
 * Global notifier for sending update broadcasts to the App Widget whenever schedule data changes.
 */
object ScheduleWidgetNotifier {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun notifyDataChanged() {
        appContext?.let { context ->
            ScheduleAppWidgetProvider.sendRefreshBroadcast(context)
        }
    }
}
