package com.example.mobileschedule

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import com.example.mobileschedule.ui.schedule.ScheduleScreen
import com.example.mobileschedule.ui.schedule.ScheduleUiState
import com.example.mobileschedule.ui.settings.*
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePreferencesUiTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 10, 12)
    private val semester = Semester(1, "2026–2027 第一学期", emptySet(), SemesterConfig(monday, 20, 8, emptyList(), 1))

    private fun screenshot(name: String, tag: String) {
        compose.waitForIdle()
        saveSyntheticScreenshot(name, compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap())
    }

    @Test fun emptyWeekHeaderOwnsTheOnlyImportEntryAndPopulatedWeekHidesIt() {
        val course = CourseArrangement(1, 1, "软件工程", "合成教师", "合成教室", 2, 1, 2, setOf(1), CourseOrigin.Manual)
        var imports = 0
        lateinit var setCourses: (List<CourseArrangement>) -> Unit
        compose.setContent { MobileScheduleTheme {
            var courses by remember { mutableStateOf(emptyList<CourseArrangement>()) }
            setCourses = { courses = it }
            Scaffold { padding -> androidx.compose.foundation.layout.Box(Modifier.padding(padding).testTag("schedule_fixture")) {
                ScheduleScreen(ScheduleUiState.Ready(ActiveWeek(semester,
                    WeekSchedule(1, 1, monday, monday.plusDays(6), courses)), WeekPosition.InSemester(1), monday),
                    onImport = { imports++ })
            } }
        } }
        compose.onAllNodesWithTag("schedule_import").assertCountEquals(1)
        val header = compose.onNodeWithTag("schedule_semester_header").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithTag("schedule_week_empty").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("schedule_import").fetchSemanticsNode().boundsInRoot
        assertTrue(header.contains(hint.center))
        assertTrue(header.contains(button.center))
        screenshot("preferences-empty-week.png", "schedule_fixture")
        compose.onNodeWithTag("schedule_import").performClick()
        assertEquals(1, imports)
        compose.runOnIdle { setCourses(listOf(course)) }
        compose.onNodeWithTag("schedule_import").assertDoesNotExist()
        compose.onNodeWithTag("schedule_week_empty").assertDoesNotExist()
        screenshot("preferences-course-week.png", "schedule_fixture")
    }

    @Test fun settingsGroupsOfferManagementAndBothPreferences() {
        var manages = 0
        var quick = false
        var auto = true
        compose.setContent { MobileScheduleTheme {
            var quickValue by remember { mutableStateOf(false) }
            var autoValue by remember { mutableStateOf(true) }
            Scaffold { padding -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                SettingsHomeScreen(SettingsHomeUiState.Ready(listOf(semester), 1),
                    onManageSchedule = { manages++ }, quickImport = quickValue,
                    onQuickImportChange = { quick = it; quickValue = it }, autoUpdate = autoValue,
                    onAutoUpdateChange = { auto = it; autoValue = it })
            } }
        } }
        compose.onNodeWithTag("settings_manage_schedule").performClick()
        assertEquals(1, manages)
        compose.onNodeWithTag("settings_quick_import").performClick().assertIsOn()
        assertTrue(quick)
        screenshot("preferences-settings-home.png", "settings_page")
        compose.onNodeWithTag("settings_page").performScrollToNode(hasTestTag("settings_auto_update"))
        compose.onNodeWithTag("settings_auto_update").performClick().assertIsOff()
        assertFalse(auto)
        compose.onNodeWithText("个人博客源").assertIsDisplayed()
        screenshot("preferences-settings.png", "settings_page")
    }

    @Test fun managementClearsOnlyAfterConfirmationAndProvidesExport() {
        var cleared: Long? = null
        var exported: Long? = null
        compose.setContent { MobileScheduleTheme { ScheduleManagementScreen(
            SettingsHomeUiState.Ready(listOf(semester), 1), onClear = { cleared = it }, onExport = { exported = it }) } }
        compose.onNodeWithTag("management_export_1").performClick()
        assertEquals(1L, exported)
        compose.onNodeWithTag("management_clear_1").performClick()
        assertNull(cleared)
        compose.onNodeWithTag("management_cancel_clear").performClick()
        assertNull(cleared)
        screenshot("preferences-management.png", "schedule_management_page")
        compose.onNodeWithTag("management_clear_1").performClick()
        compose.onNodeWithTag("management_confirm_clear").performClick()
        assertEquals(1L, cleared)
    }

    @Test fun preferencesSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "preferences-isolated-test"
        context.getSharedPreferences(name, 0).edit().clear().commit()
        try {
            val first = AppPreferencesStore(context, name)
            assertFalse(first.isQuickImportEnabled())
            assertTrue(first.isAutoUpdateEnabled())
            assertTrue(first.saveQuickImport(true))
            assertTrue(first.saveAutoUpdate(false))
            val reopened = AppPreferencesStore(context, name)
            assertTrue(reopened.isQuickImportEnabled())
            assertFalse(reopened.isAutoUpdateEnabled())
        } finally { context.getSharedPreferences(name, 0).edit().clear().commit() }
    }
}
