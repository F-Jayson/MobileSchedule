package com.example.mobileschedule

import android.Manifest
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseReminderUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    private fun screenshot(name: String, tag: String) {
        compose.waitForIdle()
        saveSyntheticScreenshot(name, compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap())
    }

    @Test
    fun reminderToggle_controlsVisibilityOfAdvanceSettingsEntry() {
        var reminderEnabled by mutableStateOf(false)

        compose.setContent {
            MobileScheduleTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    reminderEnabled = reminderEnabled,
                    onReminderEnabledChange = { reminderEnabled = it },
                    reminderAdvanceMinutes = 15,
                )
            }
        }

        // When reminder is disabled, advance settings entry should not exist
        compose.onNodeWithTag("settings_reminder_enabled").assertIsDisplayed()
        compose.onNodeWithTag("settings_reminder_advance").assertDoesNotExist()
        screenshot("reminder-toggle-disabled.png", "settings_page")

        // Turn on reminder toggle
        compose.onNodeWithTag("settings_reminder_enabled").performClick()
        compose.waitForIdle()
        assertTrue(reminderEnabled)

        // Now advance settings entry should be displayed
        compose.onNodeWithTag("settings_reminder_advance").assertIsDisplayed()
        screenshot("reminder-toggle-enabled.png", "settings_page")
    }

    @Test
    fun reminderAdvanceDialog_selectPreset_updatesAdvanceMinutes() {
        var reminderMinutes by mutableIntStateOf(15)

        compose.setContent {
            MobileScheduleTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    reminderEnabled = true,
                    reminderAdvanceMinutes = reminderMinutes,
                    onReminderAdvanceMinutesChange = { reminderMinutes = it },
                )
            }
        }

        // Click the advance settings entry to open the dialog
        compose.onNodeWithTag("settings_reminder_advance").performClick()
        compose.waitForIdle()

        screenshot("reminder-dialog-presets.png", "reminder_advance_dialog")

        // Verify presets exist in dialog
        compose.onNodeWithTag("reminder_preset_30").assertIsDisplayed()
        compose.onNodeWithTag("reminder_preset_30").performClick()

        // Confirm
        compose.onNodeWithTag("reminder_confirm_btn").performClick()
        compose.waitForIdle()

        assertEquals(30, reminderMinutes)
    }

    @Test
    fun reminderAdvanceDialog_customMinutes_updatesAdvanceMinutes() {
        var reminderMinutes by mutableIntStateOf(15)

        compose.setContent {
            MobileScheduleTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    reminderEnabled = true,
                    reminderAdvanceMinutes = reminderMinutes,
                    onReminderAdvanceMinutesChange = { reminderMinutes = it },
                )
            }
        }

        // Open dialog
        compose.onNodeWithTag("settings_reminder_advance").performClick()
        compose.waitForIdle()

        // Select custom radio
        compose.onNodeWithTag("reminder_preset_custom").performClick()
        compose.waitForIdle()

        // Input custom minutes
        compose.onNodeWithTag("reminder_custom_input").assertIsDisplayed()
        compose.onNodeWithTag("reminder_custom_input").performTextReplacement("25")
        compose.waitForIdle()

        screenshot("reminder-dialog-custom.png", "reminder_advance_dialog")

        // Confirm
        compose.onNodeWithTag("reminder_confirm_btn").performClick()
        compose.waitForIdle()

        assertEquals(25, reminderMinutes)
    }
}
