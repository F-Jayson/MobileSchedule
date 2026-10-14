package com.example.mobileschedule

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.ArrangementBackupItem
import com.example.mobileschedule.data.model.CourseOriginDraft
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.SemesterImportPreview
import com.example.mobileschedule.ui.settings.ScheduleManagementScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleImportUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun dummyPreview(
        displayName: String = "2026-2027学年第一学期",
        existingSemesterId: Long? = null,
        arrangementCount: Int = 12
    ): SemesterImportPreview {
        return SemesterImportPreview(
            displayName = displayName,
            firstWeekMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 20,
            totalSections = 8,
            sectionTimes = listOf(
                SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
                SectionTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
            ),
            arrangementCount = arrangementCount,
            existingSemesterId = existingSemesterId,
            existingSemesterName = if (existingSemesterId != null) displayName else null,
            arrangements = listOf(
                ArrangementBackupItem(
                    name = "高数",
                    teacher = "张老师",
                    location = "101",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    weeks = setOf(1, 2),
                    origin = CourseOriginDraft.Manual
                )
            )
        )
    }

    @Test
    fun importBackupButtonIsDisplayedAndTriggersCallback() {
        var importClicked = false
        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    onImportJson = { importClicked = true }
                )
            }
        }

        compose.onNodeWithTag("management_import_backup")
            .assertIsDisplayed()
            .performClick()

        assertTrue(importClicked)
    }

    @Test
    fun confirmationDialogDisplaysCorrectInfoForNewSemester() {
        val preview = dummyPreview(existingSemesterId = null)
        var confirmedPreview: SemesterImportPreview? = null
        var confirmedOverwrite: Boolean? = null

        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    importPreview = preview,
                    onConfirmImport = { p, o ->
                        confirmedPreview = p
                        confirmedOverwrite = o
                    }
                )
            }
        }

        compose.onNodeWithTag("backup_import_dialog").assertIsDisplayed()
        compose.onNodeWithText("学期名称：2026-2027学年第一学期", substring = true).assertIsDisplayed()
        compose.onNodeWithText("20周 · 8节", substring = true).assertIsDisplayed()
        compose.onNodeWithText("12 条课程安排", substring = true).assertIsDisplayed()

        // For new semester, overwrite option should not exist
        compose.onNodeWithTag("backup_import_overwrite_option").assertDoesNotExist()

        saveSyntheticScreenshot(
            "backup-import-dialog-new.png",
            compose.onNodeWithTag("backup_import_dialog").captureToImage().asAndroidBitmap()
        )

        // Click confirm
        compose.onNodeWithTag("backup_import_confirm").performClick()
        assertEquals(preview, confirmedPreview)
        assertEquals(false, confirmedOverwrite)
    }

    @Test
    fun confirmationDialogShowsOverwriteWarningAndAllowsChoice() {
        val preview = dummyPreview(existingSemesterId = 42L)
        var confirmedOverwrite: Boolean? = null
        var dismissed = false

        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    importPreview = preview,
                    onConfirmImport = { _, o -> confirmedOverwrite = o },
                    onDismissImport = { dismissed = true }
                )
            }
        }

        compose.onNodeWithTag("backup_import_dialog").assertIsDisplayed()
        compose.onNodeWithText("⚠️ 检测到已存在同名学期", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("backup_import_overwrite_option").assertIsDisplayed()
        compose.onNodeWithTag("backup_import_new_option").assertIsDisplayed()

        saveSyntheticScreenshot(
            "backup-import-dialog-conflict.png",
            compose.onNodeWithTag("backup_import_dialog").captureToImage().asAndroidBitmap()
        )

        // Select "作为新学期导入"
        compose.onNodeWithTag("backup_import_new_option").performClick()
        compose.onNodeWithTag("backup_import_confirm").performClick()
        assertEquals(false, confirmedOverwrite)
    }

    @Test
    fun confirmationDialogDismissTriggersCallback() {
        val preview = dummyPreview()
        var dismissed = false

        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    importPreview = preview,
                    onDismissImport = { dismissed = true }
                )
            }
        }

        compose.onNodeWithTag("backup_import_dismiss").performClick()
        assertTrue(dismissed)
    }
}
