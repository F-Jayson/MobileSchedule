package com.example.mobileschedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import com.example.mobileschedule.ui.navigation.MobileScheduleApp
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import com.example.mobileschedule.ui.widget.ScheduleWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var targetCourseId by mutableStateOf<Long?>(null)
    private var targetTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = AppPreferencesStore(this)
        handleWidgetIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by preferences.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = preferences.getThemeMode(),
            )
            MobileScheduleTheme(themeMode = themeMode) {
                MobileScheduleApp(
                    initialCourseId = targetCourseId,
                    onClearInitialCourseId = { targetCourseId = null },
                    initialTab = targetTab,
                    onClearInitialTab = { targetTab = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val courseId = intent?.getLongExtra(ScheduleWidgetUpdater.EXTRA_COURSE_ID, -1L) ?: -1L
        if (courseId > 0) {
            targetCourseId = courseId
        }
        when (intent?.action) {
            ScheduleWidgetUpdater.ACTION_OPEN_TODAY -> {
                targetTab = "today"
            }
            ScheduleWidgetUpdater.ACTION_OPEN_SCHEDULE -> {
                targetTab = "schedule"
            }
        }
    }
}
