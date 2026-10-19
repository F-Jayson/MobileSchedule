package com.example.mobileschedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseDetail
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.ui.schedule.CourseDetailSheet
import com.example.mobileschedule.ui.schedule.CourseDetailUiState
import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsScreen
import com.example.mobileschedule.ui.schedule.WeekScheduleGrid
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NonCurrentWeekCourseUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun nonCurrentWeekCourseMountingAndVisibilityControlledByDisplayOptions() {
        var clickedCourseId = -1L
        lateinit var changeOptions: (ScheduleDisplayOptions) -> Unit

        val fixtureWeek = createFixtureActiveWeek()

        compose.setContent {
            MaterialTheme {
                var options by remember { mutableStateOf(ScheduleDisplayOptions(showNonCurrentWeekCourses = false)) }
                changeOptions = { options = it }
                WeekScheduleGrid(
                    week = fixtureWeek,
                    displayOptions = options,
                    onCourseClick = { clickedCourseId = it },
                )
            }
        }

        // 1. 当开关关闭（showNonCurrentWeekCourses = false）时：
        // 本周课程（id = 101）正常挂载且可见
        compose.onNodeWithTag("course_101").assertIsDisplayed()
        // 非本周课程（id = 102）不得挂载于节点树中
        compose.onNodeWithTag("course_102").assertDoesNotExist()

        // 截取关闭状态证据图
        saveSyntheticScreenshot(
            "non-current-week-courses-disabled.png",
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
        )

        // 2. 切换开启开关（showNonCurrentWeekCourses = true）：
        compose.runOnIdle {
            changeOptions(ScheduleDisplayOptions(showNonCurrentWeekCourses = true))
        }

        // 本周课程仍然正常展示
        compose.onNodeWithTag("course_101").assertIsDisplayed()
        // 非本周课程成功挂载并可见
        compose.onNodeWithTag("course_102").assertIsDisplayed()
        // 验证非本周差异化标记（inactive badge）挂载
        compose.onNodeWithTag("inactive_badge_102", useUnmergedTree = true).assertIsDisplayed()

        // 3. 点击非本周课程卡片触发点击交互
        compose.onNodeWithTag("course_102").performClick()
        compose.runOnIdle {
            assertEquals(102L, clickedCourseId)
        }

        // 截取开启状态证据图
        saveSyntheticScreenshot(
            "non-current-week-courses-enabled.png",
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
        )

        // 4. 再次关闭开关：
        compose.runOnIdle {
            changeOptions(ScheduleDisplayOptions(showNonCurrentWeekCourses = false))
        }

        // 非本周课程再次从节点树中移除
        compose.onNodeWithTag("course_101").assertIsDisplayed()
        compose.onNodeWithTag("course_102").assertDoesNotExist()
    }

    @Test
    fun scheduleDisplaySettingsSwitchTogglesNonCurrentWeekOption() {
        var currentOptions = ScheduleDisplayOptions(showNonCurrentWeekCourses = false)

        compose.setContent {
            MaterialTheme {
                var options by remember { mutableStateOf(currentOptions) }
                ScheduleDisplaySettingsScreen(
                    options = options,
                    onOptionsChange = {
                        options = it
                        currentOptions = it
                    },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("display_settings_page").assertIsDisplayed()
        val switchNode = compose.onNodeWithTag("display_non_current_week_switch")
        switchNode.assertIsDisplayed()
        switchNode.assertIsOff()

        // 点击开启
        switchNode.performClick()
        assertTrue(currentOptions.showNonCurrentWeekCourses)

        // 再次点击关闭
        switchNode.performClick()
        assertFalse(currentOptions.showNonCurrentWeekCourses)

        saveSyntheticScreenshot(
            "schedule-display-settings-non-current-switch.png",
            compose.onNodeWithTag("display_settings_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun courseDetailSheetDisplaysNonCurrentWeekNoticeForInactiveCourse() {
        val course = CourseArrangement(
            id = 201,
            semesterId = 1,
            name = "计算机网络",
            teacher = "张教授",
            location = "致远楼 201",
            dayOfWeek = 2,
            startSection = 3,
            endSection = 4,
            weeks = setOf(3, 4, 5), // 第 3~5 周
            origin = CourseOrigin.Manual,
        )
        val detail = CourseDetail(
            arrangement = course,
            semesterDisplayName = "2026秋季学期",
            startTime = LocalTime.of(10, 20),
            endTime = LocalTime.of(12, 0),
        )

        compose.setContent {
            MaterialTheme {
                CourseDetailSheet(
                    state = CourseDetailUiState.Ready(detail, null),
                    currentWeek = 1, // 当前为第 1 周，课程为第 3~5 周，属于非本周课程
                    onDismiss = {},
                )
            }
        }

        // 验证详情页弹出了非本周明确提示横幅与标签
        compose.onNodeWithTag("detail_non_current_week_notice").assertIsDisplayed()
        compose.onNodeWithTag("detail_non_current_week_text").assertTextContains("非本周", substring = true)
        compose.onNodeWithTag("detail_non_current_week_text").assertTextContains("第3~5周", substring = true)

        saveSyntheticScreenshot(
            "course-detail-non-current-week-notice.png",
            compose.onNodeWithTag("detail_non_current_week_notice").captureToImage().asAndroidBitmap(),
        )
    }

    private fun createFixtureActiveWeek(): ActiveWeek {
        val monday = LocalDate.of(2026, 9, 21)
        val activeCourse = CourseArrangement(
            id = 101,
            semesterId = 1,
            name = "线性代数",
            teacher = "李老师",
            location = "理科楼 101",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = setOf(1, 2), // 第 1 周活跃
            origin = CourseOrigin.Manual,
        )
        val nonCurrentCourse = CourseArrangement(
            id = 102,
            semesterId = 1,
            name = "数据结构",
            teacher = "王老师",
            location = "实验楼 202",
            dayOfWeek = 2,
            startSection = 3,
            endSection = 4,
            weeks = setOf(3, 4), // 第 1 周不上，第 3~4 周上（非本周）
            origin = CourseOrigin.Manual,
        )

        val times = (1..12).map {
            SectionTime(
                it,
                LocalTime.of(8 + (it - 1) / 2, if (it % 2 == 1) 0 else 30),
                LocalTime.of(8 + (it - 1) / 2, if (it % 2 == 1) 25 else 55),
            )
        }
        val config = SemesterConfig(monday, 20, 12, times, 1)
        val semester = Semester(1, "2026-2027第一学期", emptySet(), config)

        // 第 1 周：本周课为 activeCourse，全量课为 activeCourse + nonCurrentCourse
        val weekSchedule = WeekSchedule(
            semesterId = 1,
            week = 1,
            monday = monday,
            sunday = monday.plusDays(6),
            arrangements = listOf(activeCourse),
            allArrangements = listOf(activeCourse, nonCurrentCourse),
        )

        return ActiveWeek(semester, weekSchedule)
    }
}
