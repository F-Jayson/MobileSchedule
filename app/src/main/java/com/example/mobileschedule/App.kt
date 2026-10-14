package com.example.mobileschedule

import android.app.Application
import com.example.mobileschedule.ui.theme.CourseColorStore
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
    }
}
