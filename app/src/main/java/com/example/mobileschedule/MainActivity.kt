package com.example.mobileschedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.mobileschedule.ui.navigation.MobileScheduleApp
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import com.example.mobileschedule.ui.widget.ScheduleWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var targetCourseId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)
        enableEdgeToEdge()
        setContent {
            MobileScheduleTheme {
                MobileScheduleApp(
                    initialCourseId = targetCourseId,
                    onClearInitialCourseId = { targetCourseId = null },
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
    }
}
