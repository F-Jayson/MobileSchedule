package com.example.mobileschedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.ui.importentry.ImportIntroScreen
import com.example.mobileschedule.ui.schedule.ScheduleScreen
import com.example.mobileschedule.ui.schedule.ScheduleUiState
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleEntryStatesTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 9, 21)

    @Test fun firstRunOffersConfigurationAndAnHonestImportEntry() {
        var configure = 0
        var import = 0
        var settings = 0
        lateinit var update: (ScheduleUiState) -> Unit
        compose.setContent { MaterialTheme {
            var state by remember { mutableStateOf<ScheduleUiState>(ScheduleUiState.NoSemester) }
            update = { state = it }
            ScheduleScreen(state,
                onConfigure = { configure++ }, onSettings = { settings++ }, onImport = { import++ })
        } }
        compose.onNodeWithTag("schedule_empty").assertIsDisplayed()
        compose.onNodeWithTag("schedule_configure").performClick()
        compose.onNodeWithTag("schedule_import").performClick()
        assertEquals(1, configure)
        assertEquals(1, import)
        compose.runOnIdle { update(ScheduleUiState.ConfigRequired) }
        compose.onNodeWithTag("schedule_config_required").assertIsDisplayed()
        compose.onNodeWithTag("schedule_settings").performClick()
        assertEquals(1, settings)
    }

    @Test fun loadingRealEmptyWeekAndReadErrorStayDistinct() {
        var retry = 0
        var import = 0
        lateinit var update: (ScheduleUiState) -> Unit
        compose.setContent { MaterialTheme {
            var state by remember { mutableStateOf<ScheduleUiState>(ScheduleUiState.Loading) }
            update = { state = it }
            ScheduleScreen(state, onRetry = { retry++ }, onImport = { import++ })
        } }
        compose.onNodeWithTag("schedule_loading").assertIsDisplayed()
        compose.runOnIdle { update(ScheduleUiState.Error) }
        compose.onNodeWithTag("schedule_error").assertIsDisplayed()
        compose.onNodeWithTag("schedule_retry").performClick()
        assertEquals(1, retry)
        compose.runOnIdle { update(ready()) }
        compose.onNodeWithTag("schedule_week_empty").assertTextContains("本周没有课程", substring = true)
        compose.onNodeWithTag("schedule_import").performClick()
        assertEquals(1, import)
    }

    @Test fun importIntroExplainsUnavailableOnlineStepAndReturnsToCaller() {
        var back = 0
        var configure = 0
        lateinit var update: (SettingsHomeUiState) -> Unit
        compose.setContent { MaterialTheme {
            var state by remember { mutableStateOf<SettingsHomeUiState>(SettingsHomeUiState.Ready(emptyList(), null)) }
            update = { state = it }
            ImportIntroScreen(state,
                onBack = { back++ }, onConfigure = { configure++ })
        } }
        compose.onNodeWithTag("import_intro").assertIsDisplayed()
        compose.onNodeWithTag("import_unavailable").assertTextContains("尚未接入", substring = true)
        compose.onNodeWithTag("import_configure").performClick()
        val semester = Semester(1, "合成学期", emptySet(), SemesterConfig(monday, 2, 2, emptyList(), 1))
        compose.runOnIdle { update(SettingsHomeUiState.Ready(listOf(semester), 1)) }
        compose.onNodeWithTag("import_target").assertTextContains("本地目标学期：合成学期", substring = true)
        compose.onNodeWithTag("import_back").performClick()
        assertEquals(1, configure)
        assertEquals(1, back)
    }

    private fun ready(): ScheduleUiState.Ready {
        val semester = Semester(1, "合成学期", emptySet(), SemesterConfig(monday, 2, 2, emptyList(), 1))
        return ScheduleUiState.Ready(
            ActiveWeek(semester, WeekSchedule(1, 1, monday, monday.plusDays(6), emptyList())),
            WeekPosition.InSemester(1), monday)
    }
}
