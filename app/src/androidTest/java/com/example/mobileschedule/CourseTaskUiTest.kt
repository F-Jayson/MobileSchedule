package com.example.mobileschedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.ui.schedule.CourseDetailSheet
import com.example.mobileschedule.ui.schedule.CourseDetailUiState
import com.example.mobileschedule.ui.schedule.WeekScheduleGrid
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import com.example.mobileschedule.ui.today.TodayScreen
import com.example.mobileschedule.ui.today.TodayUiState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseTaskUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(
        firstWeekMonday = monday,
        totalWeeks = 18,
        totalSections = 12,
        sectionTimes = emptyList(),
        revision = 1L,
    )
    private val semester = Semester(1L, "福建师范大学 2026秋季学期", emptySet(), config)

    private val testCourse = CourseArrangement(
        id = 1L,
        semesterId = 1L,
        name = "高等数学",
        teacher = "张教授",
        location = "知明1-101",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.Manual,
    )

    private val testDetail = CourseDetail(
        arrangement = testCourse,
        semesterDisplayName = "福建师范大学 2026秋季学期",
        startTime = LocalTime.of(8, 20),
        endTime = LocalTime.of(10, 0),
    )

    @Test
    fun courseDetailSheet_taskLifecycle_add_toggle_delete() {
        var createdTaskTitle: String? = null
        var createdTaskContent: String? = null
        var createdTaskType: CourseTaskType? = null
        var toggledTaskId: Long? = null
        var toggledCompleted: Boolean? = null
        var deletedTaskId: Long? = null

        var currentTasks by mutableStateOf<List<CourseTask>>(emptyList())

        compose.setContent {
            MobileScheduleTheme {
                CourseDetailSheet(
                    state = CourseDetailUiState.Ready(
                        detail = testDetail,
                        sourceTermLabel = null,
                        tasks = currentTasks,
                    ),
                    onDismiss = {},
                    onCreateTask = { title, content, _, type ->
                        createdTaskTitle = title
                        createdTaskContent = content
                        createdTaskType = type
                    },
                    onToggleTaskCompletion = { taskId, isCompleted ->
                        toggledTaskId = taskId
                        toggledCompleted = isCompleted
                    },
                    onDeleteTask = { taskId ->
                        deletedTaskId = taskId
                    },
                )
            }
        }

        // 1. Initial empty state verification with scroll
        compose.onNodeWithTag("detail_tasks_section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail_tasks_empty").performScrollTo().assertIsDisplayed()

        // 2. Open Add Task dialog
        compose.onNodeWithTag("detail_add_task").performScrollTo().performClick()
        compose.onNodeWithTag("course_task_dialog").assertIsDisplayed()

        // 3. Fill in task form
        compose.onNodeWithTag("task_title_input").performTextInput("第3章 习题集")
        compose.onNodeWithTag("task_type_homework").performClick()
        compose.onNodeWithTag("task_content_input").performTextInput("完成奇数题")

        // 4. Confirm creation
        compose.onNodeWithTag("task_dialog_confirm").performClick()
        assertEquals("第3章 习题集", createdTaskTitle)
        assertEquals("完成奇数题", createdTaskContent)
        assertEquals(CourseTaskType.HOMEWORK, createdTaskType)

        // 5. Simulate data layer update: task is added to list
        val deadlineMillis = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(20, 0))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val newTask = CourseTask(
            id = 101L,
            courseId = testCourse.id,
            title = "第3章 习题集",
            content = "完成奇数题",
            deadlineEpochMillis = deadlineMillis,
            type = CourseTaskType.HOMEWORK,
            isCompleted = false,
        )
        currentTasks = listOf(newTask)
        compose.waitForIdle()

        // 6. Verify task item renders
        compose.onNodeWithTag("task_item_101").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("task_title_101", useUnmergedTree = true).assertTextContains("第3章 习题集")

        // 7. Toggle completion checkbox
        compose.onNodeWithTag("task_checkbox_101", useUnmergedTree = true).performClick()
        assertEquals(101L, toggledTaskId)
        assertEquals(true, toggledCompleted)

        // 8. Capture screenshot of task item in detail sheet
        saveSyntheticScreenshot(
            "course-detail-task-section.png",
            compose.onNodeWithTag("detail_tasks_section").captureToImage().asAndroidBitmap(),
        )

        // 9. Delete task
        compose.onNodeWithTag("task_delete_101", useUnmergedTree = true).performClick()
        assertEquals(101L, deletedTaskId)
    }

    @Test
    fun scheduleWeekGrid_rendersTaskBadgeForCourseWithTasks() {
        val weekSchedule = WeekSchedule(
            semesterId = 1L,
            week = 1,
            monday = monday,
            sunday = monday.plusDays(6),
            arrangements = listOf(testCourse),
            allArrangements = listOf(testCourse),
        )
        val activeWeek = ActiveWeek(semester, weekSchedule)

        compose.setContent {
            MobileScheduleTheme {
                WeekScheduleGrid(
                    week = activeWeek,
                    today = monday,
                    unfinishedTaskCounts = mapOf(testCourse.id to 2),
                )
            }
        }

        // Verify task badge is displayed with correct count
        compose.onNodeWithTag("task_badge_${testCourse.id}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("task_badge_text_${testCourse.id}", useUnmergedTree = true).assertTextContains("2")

        saveSyntheticScreenshot(
            "schedule-week-task-badge.png",
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun todayScreen_rendersTaskBadgeWithUpcomingDeadline() {
        val todayItem = TodayCourseItem(
            arrangement = testCourse,
            startTime = LocalTime.of(8, 20),
            endTime = LocalTime.of(10, 0),
            status = CourseStatus.CURRENT,
            isNext = false,
        )
        val schedule = TodaySchedule.Ready(
            semester = semester,
            currentWeek = 1,
            date = monday,
            dayOfWeek = 1,
            courses = listOf(todayItem),
        )

        val tomorrowMillis = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(20, 0))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val urgentTask = CourseTask(
            id = 201L,
            courseId = testCourse.id,
            title = "期中复习测验",
            deadlineEpochMillis = tomorrowMillis,
            type = CourseTaskType.EXAM,
            isCompleted = false,
        )

        compose.setContent {
            MobileScheduleTheme {
                TodayScreen(
                    state = TodayUiState.Ready(
                        schedule = schedule,
                        currentTime = LocalTime.of(9, 0),
                        unfinishedTasksByCourse = mapOf(testCourse.id to listOf(urgentTask)),
                    ),
                )
            }
        }

        // Verify today screen task badge
        compose.onNodeWithTag("today_course_task_badge_${testCourse.id}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("today_course_task_text_${testCourse.id}", useUnmergedTree = true).assertTextContains("期中复习测验", substring = true)
        compose.onNodeWithTag("today_course_task_text_${testCourse.id}", useUnmergedTree = true).assertTextContains("明天截止", substring = true)

        saveSyntheticScreenshot(
            "today-course-task-badge.png",
            compose.onNodeWithTag("today_page").captureToImage().asAndroidBitmap(),
        )
    }
}
