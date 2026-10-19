package com.example.mobileschedule

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.dnd.ClassDndManager
import com.example.mobileschedule.data.dnd.ClassDndProfileState
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClassDndUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun screenshot(name: String, tag: String) {
        compose.waitForIdle()
        saveSyntheticScreenshot(name, compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap())
    }

    @Test
    fun classDndToggle_withoutPermission_showsPermissionDialog() {
        var dndEnabled by mutableStateOf(false)

        compose.setContent {
            MobileScheduleTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    classDndEnabled = dndEnabled,
                    onClassDndEnabledChange = { dndEnabled = it },
                )
            }
        }

        // Toggle initially displayed in Settings
        compose.onNodeWithTag("settings_class_dnd_enabled").assertIsDisplayed()
        screenshot("settings-class-dnd-disabled.png", "settings_page")

        // Clicking toggle when policy access is not yet granted triggers permission guidance dialog
        // (On emulator standard test user, notification policy access is by default not granted to test app)
        compose.onNodeWithTag("settings_class_dnd_enabled").performClick()
        compose.waitForIdle()

        if (!ClassDndManager.isNotificationPolicyAccessGranted(InstrumentationRegistry.getInstrumentation().targetContext)) {
            compose.onNodeWithTag("dnd_permission_dialog").assertIsDisplayed()
            compose.onNodeWithText("需要免打扰访问权限").assertIsDisplayed()
            compose.onNodeWithTag("dnd_permission_confirm").assertIsDisplayed()
            compose.onNodeWithTag("dnd_permission_dismiss").assertIsDisplayed()
            screenshot("settings-dnd-permission-dialog.png", "dnd_permission_dialog")

            // Dismiss dialog
            compose.onNodeWithTag("dnd_permission_dismiss").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("dnd_permission_dialog").assertDoesNotExist()
        }
    }

    @Test
    fun classDndToggle_withEnabledState_reflectsCorrectly() {
        var dndEnabled by mutableStateOf(true)

        compose.setContent {
            MobileScheduleTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    classDndEnabled = dndEnabled,
                    onClassDndEnabledChange = { dndEnabled = it },
                )
            }
        }

        compose.onNodeWithTag("settings_class_dnd_enabled").assertIsDisplayed()
        screenshot("settings-class-dnd-enabled.png", "settings_page")

        // Toggling off directly calls onClassDndEnabledChange(false)
        compose.onNodeWithTag("settings_class_dnd_enabled").performClick()
        compose.waitForIdle()
        assertFalse(dndEnabled)
    }

    @Test
    fun classDndManager_profilePersistenceAndSettingDisabledReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Save active DND state with initial NORMAL ringer mode
        val activeState = ClassDndProfileState(
            isDndActive = true,
            originalRingerMode = AudioManager.RINGER_MODE_NORMAL,
            originalFilter = 1,
        )
        ClassDndManager.saveProfileState(context, activeState)

        val loaded = ClassDndManager.getProfileState(context)
        assertTrue(loaded.isDndActive)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, loaded.originalRingerMode)

        // When user disables DND setting in app settings, onSettingDisabled is triggered
        ClassDndManager.onSettingDisabled(context)

        val restoredState = ClassDndManager.getProfileState(context)
        assertFalse("isDndActive must be reset to false after onSettingDisabled", restoredState.isDndActive)
    }
}
