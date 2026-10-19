package com.example.mobileschedule

import android.Manifest
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.calendar.CalendarEventItem
import com.example.mobileschedule.data.calendar.SystemCalendarSyncManager
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.ui.settings.ScheduleManagementScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class CalendarExportSyncUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val testSemester = Semester(
        id = 1L,
        displayName = "2026-2027学年第一学期",
        sourceBindings = emptySet(),
        config = SemesterConfig(
            firstWeekMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 18,
            totalSections = 12,
            sectionTimes = emptyList(),
            revision = 1L,
        ),
    )

    @Test
    fun calendarManagementCardAndButtonsDisplayed() {
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(listOf(testSemester), activeId = testSemester.id),
                )
            }
        }

        // Verify top calendar card
        compose.onNodeWithTag("management_calendar_card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("management_clear_system_calendar").assertIsDisplayed()

        // Verify semester card ICS and Sync buttons
        compose.onNodeWithTag("management_export_ics_1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("management_sync_calendar_1").assertIsDisplayed()

        saveSyntheticScreenshot(
            "calendar-sync-management-page.png",
            compose.onNodeWithTag("schedule_management_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun clickingExportIcsTriggersCallback() {
        var exportedSemesterId: Long? = null
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(listOf(testSemester), activeId = testSemester.id),
                    onExportIcs = { exportedSemesterId = it },
                )
            }
        }

        compose.onNodeWithTag("management_export_ics_1").performScrollTo().performClick()
        assertEquals(1L, exportedSemesterId)
    }

    @Test
    fun clickingSyncCalendarTriggersCallback() {
        var syncedSemesterId: Long? = null
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(listOf(testSemester), activeId = testSemester.id),
                    onSyncCalendar = { syncedSemesterId = it },
                )
            }
        }

        compose.onNodeWithTag("management_sync_calendar_1").performScrollTo().performClick()
        assertEquals(1L, syncedSemesterId)
    }

    @Test
    fun clickingClearSystemCalendarTriggersCallback() {
        var clearCalled = false
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(listOf(testSemester), activeId = testSemester.id),
                    onClearCalendar = { clearCalled = true },
                )
            }
        }

        compose.onNodeWithTag("management_clear_system_calendar").performScrollTo().performClick()
        assertTrue(clearCalled)
    }

    @Test
    fun systemCalendarSyncManager_deviceIntegrationTest() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val packageName = context.packageName

        // Grant calendar permissions to the test runner / app context
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.READ_CALENDAR)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.WRITE_CALENDAR)

        val syncManager = SystemCalendarSyncManager(context)
        assertTrue("Calendar permissions should be granted", syncManager.hasPermissions())

        val testEvents = listOf(
            CalendarEventItem(
                uid = "test-course-101-w1-d1-s1",
                summary = "编译原理与技术",
                location = "旗山笃行楼201",
                description = "任课教师：张教授\n来源：福建师范大学课表",
                startDateTime = LocalDateTime.of(2026, 9, 7, 8, 20),
                endDateTime = LocalDateTime.of(2026, 9, 7, 10, 0),
            ),
            CalendarEventItem(
                uid = "test-course-102-w1-d2-s3",
                summary = "算法分析与设计",
                location = "旗山理工楼405",
                description = "任课教师：李副教授\n来源：福建师范大学课表",
                startDateTime = LocalDateTime.of(2026, 9, 8, 10, 20),
                endDateTime = LocalDateTime.of(2026, 9, 8, 12, 0),
            ),
        )

        // 1. Sync events into system calendar
        val syncResult = syncManager.syncEvents(testEvents)
        assertTrue(syncResult.message, syncResult.success)
        assertEquals(2, syncResult.insertedCount)

        val calendarId = syncManager.findCalendarId()
        assertNotNull(calendarId)

        // 2. Clear calendar from system calendar
        val clearResult = syncManager.clearCalendar()
        assertTrue(clearResult.message, clearResult.success)
    }
}
