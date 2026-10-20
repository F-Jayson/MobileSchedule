package com.example.mobileschedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.ui.navigation.AdaptiveNavigationScaffold
import com.example.mobileschedule.ui.schedule.AdaptiveScheduleScaffold
import com.example.mobileschedule.ui.schedule.CourseDetailUiState
import com.example.mobileschedule.ui.schedule.ScheduleScreen
import com.example.mobileschedule.ui.schedule.ScheduleUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveScheduleUiTest {

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

    private val mathCourse = CourseArrangement(
        id = 101L,
        semesterId = 1L,
        name = "高等数学",
        teacher = "张教授",
        location = "知明1-101",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        weeks = (1..18).toSet(),
        origin = CourseOrigin.Manual,
    )

    private val physicsCourse = CourseArrangement(
        id = 102L,
        semesterId = 1L,
        name = "大学物理",
        teacher = "李教授",
        location = "物光楼-203",
        dayOfWeek = 1,
        startSection = 3,
        endSection = 4,
        weeks = (1..18).toSet(),
        origin = CourseOrigin.Manual,
    )

    private val csCourse = CourseArrangement(
        id = 103L,
        semesterId = 1L,
        name = "操作系统",
        teacher = "王老师",
        location = "计金楼-402",
        dayOfWeek = 3,
        startSection = 5,
        endSection = 7,
        weeks = (1..18).toSet(),
        origin = CourseOrigin.Manual,
    )

    private fun createSampleReadyState(
        weekNum: Int = 3,
        currentWeekNum: Int = 3,
        todayDate: LocalDate = monday.plusWeeks(2), // Monday of week 3
        arrangements: List<CourseArrangement> = listOf(mathCourse, physicsCourse, csCourse),
        taskCounts: Map<Long, Int> = mapOf(101L to 2, 103L to 1),
    ): ScheduleUiState.Ready {
        val weekMonday = monday.plusWeeks((weekNum - 1).toLong())
        val weekSunday = weekMonday.plusDays(6)
        val schedule = WeekSchedule(
            semesterId = 1L,
            week = weekNum,
            monday = weekMonday,
            sunday = weekSunday,
            arrangements = arrangements,
            allArrangements = arrangements,
        )
        return ScheduleUiState.Ready(
            week = ActiveWeek(semester, schedule),
            currentPosition = WeekPosition.InSemester(currentWeekNum),
            today = todayDate,
            unfinishedTaskCounts = taskCounts,
        )
    }

    @Test
    fun expandedWidth_mountsAdaptiveScheduleScaffold_withDualColumns() {
        val readyState = createSampleReadyState()

        compose.setContent {
            MobileScheduleTheme {
                ScheduleScreen(
                    state = readyState,
                    detailState = CourseDetailUiState.Closed,
                    isExpandedOverride = true,
                )
            }
        }

        // Verify dual-column scaffold and container tags
        compose.onNodeWithTag("schedule_container").assertIsDisplayed()
        compose.onNodeWithTag("adaptive_schedule_scaffold").assertIsDisplayed()
        compose.onNodeWithTag("adaptive_left_panel").assertIsDisplayed()
        compose.onNodeWithTag("adaptive_right_grid").assertIsDisplayed()

        // Verify Left Side Panel elements
        compose.onNodeWithText("福建师范大学 2026秋季学期").assertIsDisplayed()
        compose.onNodeWithTag("adaptive_mini_week_switcher").assertIsDisplayed()
        compose.onNodeWithText("第 3 周").assertIsDisplayed()
        compose.onNodeWithText("当前周").assertIsDisplayed()

        // Verify Today Timeline Header & Badge
        compose.onNodeWithText("今日课程 (2)").assertIsDisplayed()
        compose.onNodeWithText("待办 3 项").assertIsDisplayed()

        // Verify Today Course Cards & Task Badge
        compose.onNodeWithTag("adaptive_today_course_card_101").assertIsDisplayed()
        compose.onAllNodesWithText("高等数学").assertCountEquals(2)
        compose.onNodeWithText("📍 知明1-101", substring = true).assertIsDisplayed()
        compose.onNodeWithText("待办 2").assertIsDisplayed()

        compose.onNodeWithTag("adaptive_today_course_card_102").assertIsDisplayed()
        compose.onAllNodesWithText("大学物理").assertCountEquals(2)
        compose.onNodeWithText("📍 物光楼-203", substring = true).assertIsDisplayed()

        // Verify Quick Buttons on Side Panel
        compose.onNodeWithTag("adaptive_add_course_button").assertIsDisplayed()
        compose.onNodeWithTag("adaptive_export_button").assertIsDisplayed()

        // Verify Right Grid Header
        compose.onNodeWithTag("adaptive_right_header").assertIsDisplayed()
        compose.onNodeWithText("周课表概览 · 第 3 周").assertIsDisplayed()
        compose.onNodeWithText("上一周").assertIsDisplayed()
        compose.onNodeWithText("下一周").assertIsDisplayed()

        // Save screenshot evidence
        saveSyntheticScreenshot(
            "adaptive-schedule-expanded-dual-column.png",
            compose.onNodeWithTag("adaptive_schedule_scaffold").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun expandedWidth_adaptiveScaffold_interactions_weekSwitchingAndCourseClick() {
        var clickedCourseId: Long? = null
        var prevWeekCalled = false
        var nextWeekCalled = false
        var returnCurrentWeekCalled = false
        var addCourseCalled = false
        var exportCalled = false

        // Viewing week 4 while current week is 3
        val browsingState = createSampleReadyState(
            weekNum = 4,
            currentWeekNum = 3,
            todayDate = monday.plusWeeks(2), // Still in week 3
        )

        compose.setContent {
            MobileScheduleTheme {
                Box(Modifier.requiredWidth(960.dp).requiredHeight(650.dp)) {
                    AdaptiveScheduleScaffold(
                        state = browsingState,
                        detailState = CourseDetailUiState.Closed,
                        onCourseClick = { clickedCourseId = it },
                        onPreviousWeek = { prevWeekCalled = true },
                        onNextWeek = { nextWeekCalled = true },
                        onReturnToCurrentWeek = { returnCurrentWeekCalled = true },
                        onAddCourse = { addCourseCalled = true },
                        onExportImage = { exportCalled = true },
                    )
                }
            }
        }

        // Test mini week switcher prev button
        compose.onNodeWithTag("adaptive_prev_week").performClick()
        assertTrue("Previous week callback should be triggered", prevWeekCalled)

        // Test mini week switcher next button
        compose.onNodeWithTag("adaptive_next_week").performClick()
        assertTrue("Next week callback should be triggered", nextWeekCalled)

        // Test non-current week "回到本周" button in right header
        compose.onNodeWithTag("adaptive_return_current_week").assertIsDisplayed()
        compose.onNodeWithTag("adaptive_return_current_week").performClick()
        assertTrue("Return to current week callback should be triggered", returnCurrentWeekCalled)

        // Test clicking course card in today's timeline
        compose.onNodeWithTag("adaptive_today_course_card_101").performClick()
        assertEquals(101L, clickedCourseId)

        // Test clicking quick action buttons
        compose.onNodeWithTag("adaptive_add_course_button").performClick()
        assertTrue("Add course callback should be triggered", addCourseCalled)

        compose.onNodeWithTag("adaptive_export_button").performClick()
        assertTrue("Export image callback should be triggered", exportCalled)
    }

    @Test
    fun compactWidth_fallsBackToSingleColumnLayout() {
        val readyState = createSampleReadyState()

        compose.setContent {
            MobileScheduleTheme {
                ScheduleScreen(
                    state = readyState,
                    detailState = CourseDetailUiState.Closed,
                    isExpandedOverride = false,
                )
            }
        }

        // Container should exist
        compose.onNodeWithTag("schedule_container").assertIsDisplayed()

        // Adaptive dual-column scaffold should NOT exist in compact screen (< 840dp)
        compose.onNodeWithTag("adaptive_schedule_scaffold").assertDoesNotExist()
        compose.onNodeWithTag("adaptive_left_panel").assertDoesNotExist()
        compose.onNodeWithTag("adaptive_right_grid").assertDoesNotExist()

        // Classic week schedule grid should be displayed
        compose.onNodeWithTag("week_grid").assertIsDisplayed()

        // Save screenshot evidence
        saveSyntheticScreenshot(
            "adaptive-schedule-compact-single-column.png",
            compose.onNodeWithTag("schedule_container").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun expandedWidth_navigationScaffold_showsNavigationRail() {
        var navigatedRoute: String? = null

        compose.setContent {
            MobileScheduleTheme {
                AdaptiveNavigationScaffold(
                    currentRoute = "schedule",
                    onNavigate = { navigatedRoute = it },
                    isExpandedOverride = true,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Text("Expanded Content Area")
                    }
                }
            }
        }

        // NavigationRail should be mounted in expanded width (>= 840dp)
        compose.onNodeWithTag("main_navigation_rail").assertIsDisplayed()
        compose.onNodeWithTag("main_bottom_navigation").assertDoesNotExist()

        // Rail items should be visible
        compose.onNodeWithTag("rail_schedule").assertIsDisplayed()
        compose.onNodeWithTag("rail_today").assertIsDisplayed()
        compose.onNodeWithTag("rail_settings").assertIsDisplayed()

        // Click rail item
        compose.onNodeWithTag("rail_today").performClick()
        assertEquals("today", navigatedRoute)

        // Save screenshot evidence
        saveSyntheticScreenshot(
            "adaptive-navigation-rail-expanded.png",
            compose.onNodeWithTag("main_navigation_rail").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun compactWidth_navigationScaffold_showsNavigationBar() {
        var navigatedRoute: String? = null

        compose.setContent {
            MobileScheduleTheme {
                AdaptiveNavigationScaffold(
                    currentRoute = "schedule",
                    onNavigate = { navigatedRoute = it },
                    isExpandedOverride = false,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Text("Compact Content Area")
                    }
                }
            }
        }

        // Bottom NavigationBar should be mounted in compact width (< 840dp)
        compose.onNodeWithTag("main_bottom_navigation").assertIsDisplayed()
        compose.onNodeWithTag("main_navigation_rail").assertDoesNotExist()

        // Bottom bar items should be visible
        compose.onNodeWithTag("tab_schedule").assertIsDisplayed()
        compose.onNodeWithTag("tab_today").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertIsDisplayed()

        // Click tab item
        compose.onNodeWithTag("tab_settings").performClick()
        assertEquals("settings", navigatedRoute)

        // Save screenshot evidence
        saveSyntheticScreenshot(
            "adaptive-navigation-bottom-bar-compact.png",
            compose.onNodeWithTag("main_bottom_navigation").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun expandedWidth_todayEmptyState_displaysEncouragementInSidePanel() {
        // Sunday: No courses
        val emptyState = createSampleReadyState(
            todayDate = monday.plusDays(6), // Sunday
        )

        compose.setContent {
            MobileScheduleTheme {
                AdaptiveScheduleScaffold(
                    state = emptyState,
                    detailState = CourseDetailUiState.Closed,
                )
            }
        }

        compose.onNodeWithText("今日课程 (0)").assertIsDisplayed()
        compose.onNodeWithText("今天全天没有排课，尽情享受自习或休息时光吧~").assertIsDisplayed()
    }
}
