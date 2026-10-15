package com.example.mobileschedule

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.ui.schedule.*
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualCourseUiTest {
    @get:Rule val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(monday, 16, 12, emptyList(), 1)
    private val semester = Semester(1, "福建师范大学 2026秋季学期", emptySet(), config)

    private fun manualCourse(id: Long = 1) = CourseArrangement(
        id = id,
        semesterId = 1,
        name = "创新实践课",
        teacher = "李教授",
        location = "旗山校区 实验楼202",
        dayOfWeek = 1,
        startSection = 3,
        endSection = 4,
        weeks = setOf(1, 2, 3, 4, 5, 6),
        origin = CourseOrigin.Manual,
    )

    private fun importedCourse(id: Long = 2) = CourseArrangement(
        id = id,
        semesterId = 1,
        name = "高等数学",
        teacher = "张教授",
        location = "知明1-101",
        dayOfWeek = 2,
        startSection = 1,
        endSection = 2,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.SchoolImport(SourceScope("fjnu", "dom", "2026-1"), "batch-1", "entry-1"),
    )

    @Test
    fun addCourseButtonAndFabAreDisplayedOnScheduleScreen() {
        var addClicked = false
        val weekSchedule = WeekSchedule(1, 1, monday, monday.plusDays(6), listOf(manualCourse()))
        val activeWeek = ActiveWeek(semester, weekSchedule)
        val readyState = ScheduleUiState.Ready(activeWeek, WeekPosition.InSemester(1), monday)

        compose.setContent {
            MobileScheduleTheme {
                ScheduleScreen(
                    state = readyState,
                    onAddCourse = { addClicked = true },
                )
            }
        }

        // Verify top-right "+ 添加课程" button in header
        compose.onNodeWithTag("schedule_add_course").assertIsDisplayed()
        compose.onNodeWithTag("schedule_add_course").assertHasClickAction()

        // Verify FAB "+" button on bottom-right of grid has been removed
        compose.onNodeWithTag("schedule_add_course_fab").assertDoesNotExist()

        // Click add course
        compose.onNodeWithTag("schedule_add_course").performClick()
        assertTrue(addClicked)

        saveSyntheticScreenshot(
            "manual-course-schedule-entry.png",
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun courseFormSheetDisplaysAllFieldsAndColorPalette() {
        var savedDraft: ArrangementDraft? = null
        var savedColor: Int? = null

        compose.setContent {
            MobileScheduleTheme {
                CourseFormSheet(
                    formState = CourseFormState.Create(
                        semesterId = 1,
                        config = config,
                        defaultDay = 2,
                        defaultWeek = 3,
                    ),
                    onDismiss = {},
                    onSave = { draft, color ->
                        savedDraft = draft
                        savedColor = color
                    },
                    onCheckConflict = { _, _ -> emptyList() },
                )
            }
        }

        // Verify sheet elements
        compose.onNodeWithTag("course_form_sheet").assertIsDisplayed()
        compose.onNodeWithTag("course_form_title").assertIsDisplayed()
        compose.onNodeWithTag("course_form_name").assertIsDisplayed()
        compose.onNodeWithTag("course_form_location").assertIsDisplayed()
        compose.onNodeWithTag("course_form_teacher").assertIsDisplayed()
        compose.onNodeWithTag("course_form_day_picker").assertIsDisplayed()
        compose.onNodeWithTag("course_form_start_section").assertIsDisplayed()
        compose.onNodeWithTag("course_form_end_section").assertIsDisplayed()
        compose.onNodeWithTag("course_form_weeks_selector").assertIsDisplayed()
        compose.onNodeWithTag("course_form_weeks_all").assertIsDisplayed()
        compose.onNodeWithTag("course_form_weeks_odd").assertIsDisplayed()
        compose.onNodeWithTag("course_form_weeks_even").assertIsDisplayed()
        compose.onNodeWithTag("course_form_weeks_clear").assertIsDisplayed()

        // Verify color swatches
        for (i in 0..7) {
            compose.onNodeWithTag("course_form_color_$i").assertIsDisplayed()
        }

        saveSyntheticScreenshot(
            "manual-course-form-create.png",
            compose.onNodeWithTag("course_form_sheet").captureToImage().asAndroidBitmap(),
        )

        // Fill course name and choose color swatch 2 (浅雾暖紫)
        compose.onNodeWithTag("course_form_name").performTextInput("软件体系架构")
        compose.onNodeWithTag("course_form_color_2").performClick()
        compose.onNodeWithTag("course_form_weeks_odd").performClick()

        // Submit form
        compose.onNodeWithTag("course_form_save").performClick()
        assertEquals("软件体系架构", savedDraft?.name)
        assertEquals(2, savedColor)
        assertEquals(config.totalWeeks / 2, savedDraft?.weeks?.size)
    }

    @Test
    fun courseFormSheetDisplaysConflictWarningWhenOverlapDetected() {
        val existing = manualCourse()

        compose.setContent {
            MobileScheduleTheme {
                CourseFormSheet(
                    formState = CourseFormState.Create(
                        semesterId = 1,
                        config = config,
                        defaultDay = 1,
                        defaultWeek = 1,
                    ),
                    onDismiss = {},
                    onSave = { _, _ -> },
                    onCheckConflict = { _, _ -> listOf(existing) },
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("course_form_conflict_warning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("时段重叠提醒", substring = true).assertIsDisplayed()
        compose.onNodeWithText("创新实践课", substring = true).assertIsDisplayed()

        saveSyntheticScreenshot(
            "manual-course-form-conflict.png",
            compose.onNodeWithTag("course_form_sheet").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun courseDetailSheetManualCourseActionsAndDeleteDialog() {
        val manual = manualCourse()
        val detail = CourseDetail(manual, "2026秋季学期", null, null)
        var editClicked = false
        var deleteConfirmedId: Long? = null

        compose.setContent {
            MobileScheduleTheme {
                CourseDetailSheet(
                    state = CourseDetailUiState.Ready(detail, null),
                    onDismiss = {},
                    onEditCourse = { editClicked = true },
                    onDeleteCourse = { deleteConfirmedId = it },
                )
            }
        }

        // Verify "编辑课程" and "删除课程" buttons are visible for manual course
        compose.onNodeWithTag("detail_edit_course").assertIsDisplayed()
        compose.onNodeWithTag("detail_delete_course").assertIsDisplayed()

        compose.onNodeWithTag("detail_edit_course").performClick()
        assertTrue(editClicked)

        // Click delete course -> secondary confirmation AlertDialog appears
        compose.onNodeWithTag("detail_delete_course").performClick()
        compose.onNodeWithTag("delete_course_dialog").assertIsDisplayed()
        compose.onNodeWithText("确定要删除课程“创新实践课”吗？此操作无法撤销。", substring = true).assertIsDisplayed()

        saveSyntheticScreenshot(
            "manual-course-detail-delete-dialog.png",
            compose.onNodeWithTag("delete_course_dialog").captureToImage().asAndroidBitmap(),
        )

        // Confirm deletion
        compose.onNodeWithTag("delete_course_dialog_confirm").performClick()
        assertEquals(manual.id, deleteConfirmedId)
    }

    @Test
    fun courseDetailSheetSchoolImportCourseAllowsCopyAsManual() {
        val imported = importedCourse()
        val detail = CourseDetail(imported, "2026秋季学期", null, null)
        var copiedCourse: CourseArrangement? = null

        compose.setContent {
            MobileScheduleTheme {
                CourseDetailSheet(
                    state = CourseDetailUiState.Ready(detail, "2026-2027学年第1学期"),
                    onDismiss = {},
                    onCopyCourse = { copiedCourse = it },
                )
            }
        }

        // Verify "复制为手动课程" button is displayed
        compose.onNodeWithTag("detail_copy_course").assertIsDisplayed()
        // Edit and Delete buttons should NOT exist for imported course
        compose.onNodeWithTag("detail_edit_course").assertDoesNotExist()
        compose.onNodeWithTag("detail_delete_course").assertDoesNotExist()

        saveSyntheticScreenshot(
            "school-course-detail-copy.png",
            compose.onNodeWithTag("course_detail").captureToImage().asAndroidBitmap(),
        )

        compose.onNodeWithTag("detail_copy_course").performClick()
        assertEquals(imported, copiedCourse)
    }

    @Test
    fun courseFormSheetCopyModeScrollsToBottomWithoutJitter() {
        val imported = importedCourse()
        var savedDraft: ArrangementDraft? = null

        compose.setContent {
            MobileScheduleTheme {
                CourseFormSheet(
                    formState = CourseFormState.Copy(
                        arrangement = imported,
                        config = config,
                    ),
                    onDismiss = {},
                    onSave = { draft, _ -> savedDraft = draft },
                    onCheckConflict = { _, _ -> listOf(imported) },
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("course_form_sheet").assertIsDisplayed()
        compose.onNodeWithTag("course_form_title").assertTextContains("复制为手动课程", substring = true)

        // Scroll to the bottom where conflict warning is displayed
        compose.onNodeWithTag("course_form_conflict_warning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("时段重叠提醒", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("course_form_save").assertIsDisplayed()

        saveSyntheticScreenshot(
            "manual-course-form-copy-bottom.png",
            compose.onNodeWithTag("course_form_sheet").captureToImage().asAndroidBitmap(),
        )

        // Verify save button works stably
        compose.onNodeWithTag("course_form_save").performClick()
        assertEquals("高等数学", savedDraft?.name)
    }
}
