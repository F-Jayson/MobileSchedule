package com.example.mobileschedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.ui.settings.ScheduleManagementScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SemesterDeletionUiTest {
    @get:Rule val compose = createComposeRule()
    private val semester = Semester(7, "待删除合成学期", emptySet(), null)

    @Test fun deleteSemesterEntryOpensConfirmationForSelectedSemester() {
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(SettingsHomeUiState.Ready(listOf(semester), semester.id))
            }
        }

        compose.onNodeWithTag("management_delete_7").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("management_delete_dialog").assertIsDisplayed()
        compose.onNodeWithTag("management_delete_message").assertTextContains("待删除合成学期", substring = true)
        compose.onNodeWithTag("management_delete_message").assertTextContains("学期日历", substring = true)
        compose.onNodeWithTag("management_confirm_delete").assertIsDisplayed()
        compose.onNodeWithTag("management_cancel_delete").assertIsDisplayed()
        saveSyntheticScreenshot("semester-deletion-confirmation.png",
            compose.onNodeWithTag("management_delete_dialog").captureToImage().asAndroidBitmap())
    }

    @Test fun cancellingSemesterDeletionClosesConfirmation() {
        val deleted = mutableListOf<Long>()
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(SettingsHomeUiState.Ready(listOf(semester), semester.id),
                    onDelete = { deleted += it })
            }
        }

        compose.onNodeWithTag("management_delete_7").performScrollTo().performClick()
        compose.onNodeWithTag("management_cancel_delete").performClick()
        compose.onNodeWithTag("management_delete_dialog").assertDoesNotExist()
        compose.onNodeWithTag("management_delete_7").assertIsDisplayed()
        assertTrue(deleted.isEmpty())
        saveSyntheticScreenshot("semester-deletion-management.png",
            compose.onNodeWithTag("schedule_management_page").captureToImage().asAndroidBitmap())
    }

    @Test fun confirmingDeletionCallsSelectedSemesterOnceAndClosesDialog() {
        val deleted = mutableListOf<Long>()
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(SettingsHomeUiState.Ready(listOf(semester), semester.id),
                    onDelete = { deleted += it })
            }
        }

        compose.onNodeWithTag("management_delete_7").performScrollTo().performClick()
        assertTrue(deleted.isEmpty())
        compose.onNodeWithTag("management_confirm_delete").performClick()
        assertEquals(listOf(7L), deleted)
        compose.onNodeWithTag("management_delete_dialog").assertDoesNotExist()
    }

    @Test fun managingScheduleDisablesSemesterDeletionEntry() {
        val deleted = mutableListOf<Long>()
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(SettingsHomeUiState.Ready(listOf(semester), semester.id),
                    busy = true, onDelete = { deleted += it })
            }
        }

        compose.onNodeWithTag("management_delete_7").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("management_delete_dialog").assertDoesNotExist()
        assertTrue(deleted.isEmpty())
    }

    @Test fun managingScheduleDisablesAnOpenDeletionConfirmation() {
        val deleted = mutableListOf<Long>()
        lateinit var setBusy: (Boolean) -> Unit
        compose.setContent {
            var busy by remember { mutableStateOf(false) }
            setBusy = { busy = it }
            MobileScheduleTheme {
                ScheduleManagementScreen(SettingsHomeUiState.Ready(listOf(semester), semester.id),
                    busy = busy, onDelete = { deleted += it })
            }
        }

        compose.onNodeWithTag("management_delete_7").performScrollTo().performClick()
        compose.runOnIdle { setBusy(true) }
        compose.onNodeWithTag("management_confirm_delete").assertIsNotEnabled()
        assertTrue(deleted.isEmpty())
        compose.onNodeWithTag("management_cancel_delete").performClick()
        compose.onNodeWithTag("management_delete_dialog").assertDoesNotExist()
    }
}
