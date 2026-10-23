package com.example.mobileschedule

import android.app.Application
import com.example.mobileschedule.data.notification.OngoingScheduleNotificationManager
import com.example.mobileschedule.data.reminder.CourseReminderNotifier
import com.example.mobileschedule.data.reminder.CourseReminderReceiver
import com.example.mobileschedule.ui.theme.CourseColorStore
import com.example.mobileschedule.ui.widget.ScheduleAutoRefreshScheduler
import com.example.mobileschedule.ui.widget.ScheduleWidgetNotifier
import com.example.mobileschedule.ui.widget.ScheduleWidgetWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CourseColorStore.init(this)
        ScheduleWidgetNotifier.init(this)
        ScheduleWidgetWorker.enqueuePeriodicWork(this)
        CourseReminderReceiver.ensureChannel(this)
        CourseReminderNotifier.init(this)
        OngoingScheduleNotificationManager.init(this)
        com.example.mobileschedule.data.dnd.ClassDndManager.init(this)
        ScheduleAutoRefreshScheduler.scheduleNextRefresh(this)
    }
}
