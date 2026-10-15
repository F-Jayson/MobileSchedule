package com.example.mobileschedule

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import com.example.mobileschedule.ui.today.TodayScreen
import com.example.mobileschedule.ui.today.TodayUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayUiTest {
    @get:Rule val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(monday, 16, 12, emptyList(), 1L)
    private val semester = Semester(1L, "福建师范大学 2026秋季学期", emptySet(), config)

    private val currentCourse = CourseArrangement(
        id = 1L,
        semesterId = 1L,
        name = "高等数学",
        teacher = "张教授",
        location = "知明1-101",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.SchoolImport(SourceScope("fjnu", "dom", "2026-1"), "batch-1", "entry-1"),
    )

    private val upcomingCourse = CourseArrangement(
        id = 2L,
        semesterId = 1L,
        name = "创新实践课",
        teacher = "李老师",
        location = "实验楼A301",
        dayOfWeek = 1,
        startSection = 3,
        endSection = 4,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.Manual,
    )

    @Test
    fun todayScreenRendersReadyScheduleWithCardsAndSummaryBanner() {
        var clickedId: Long? = null
        val items = listOf(
            TodayCourseItem(
                arrangement = currentCourse,
                startTime = LocalTime.of(8, 20),
                endTime = LocalTime.of(10, 0),
                status = CourseStatus.CURRENT,
                isNext = false,
            ),
            TodayCourseItem(
                arrangement = upcomingCourse,
                startTime = LocalTime.of(10, 20),
                endTime = LocalTime.of(12, 0),
                status = CourseStatus.UPCOMING,
                isNext = true,
            ),
        )

        val schedule = TodaySchedule.Ready(
            semester = semester,
            currentWeek = 1,
            date = monday,
            dayOfWeek = 1,
            courses = items,
        )

        compose.setContent {
            MobileScheduleTheme {
                TodayScreen(
                    state = TodayUiState.Ready(schedule, LocalTime.of(9, 0)),
                    onCourseClick = { clickedId = it },
                )
            }
        }

        // Header
        compose.onNodeWithTag("today_header").assertIsDisplayed()
        compose.onNodeWithTag("today_date").assertIsDisplayed()
        compose.onNodeWithTag("today_week_badge").assertIsDisplayed()

        // Summary Banner (Ongoing class)
        compose.onNodeWithTag("today_summary_banner").assertIsDisplayed()
        compose.onNodeWithText("正在上课：高等数学", substring = true).assertIsDisplayed()

        // Course Cards
        compose.onNodeWithTag("today_course_card_1").assertIsDisplayed()
        compose.onNodeWithTag("today_course_name_1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("today_course_status_1", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("正在上课", substring = true).assertCountEquals(2)

        compose.onNodeWithTag("today_course_card_2").assertIsDisplayed()
        compose.onNodeWithTag("today_course_name_2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("today_course_status_2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("下一节", substring = true, useUnmergedTree = true).assertIsDisplayed()

        saveSyntheticScreenshot(
            "today-schedule-ready.png",
            compose.onNodeWithTag("today_page").captureToImage().asAndroidBitmap(),
        )

        // Click card
        compose.onNodeWithTag("today_course_card_1").performClick()
        assertEquals(1L, clickedId)
    }

    @Test
    fun testPastCourseCardRendering() {
        val pastCourse = CourseArrangement(
            id = 1L,
            semesterId = 1L,
            name = "高级数据库开发",
            teacher = "倪友聪",
            location = "笃行1-509",
            dayOfWeek = 4,
            startSection = 1,
            endSection = 2,
            weeks = (1..16).toSet(),
            origin = CourseOrigin.SchoolImport(SourceScope("fjnu", "dom", "2026-1"), "batch-1", "entry-1"),
        )
        val items = listOf(
            TodayCourseItem(
                arrangement = pastCourse,
                startTime = LocalTime.of(8, 20),
                endTime = LocalTime.of(10, 0),
                status = CourseStatus.PAST,
                isNext = false,
            ),
        )
        val schedule = TodaySchedule.Ready(
            semester = semester,
            currentWeek = 7,
            date = LocalDate.of(2026, 10, 15),
            dayOfWeek = 4,
            courses = items,
        )

        compose.setContent {
            MobileScheduleTheme {
                TodayScreen(
                    state = TodayUiState.Ready(schedule, LocalTime.of(11, 0)),
                )
            }
        }

        saveSyntheticScreenshot(
            "today-schedule-past.png",
            compose.onNodeWithTag("today_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun todayScreenRendersEmptyStateWhenNoCourses() {
        val schedule = TodaySchedule.Ready(
            semester = semester,
            currentWeek = 1,
            date = monday.plusDays(6), // Sunday
            dayOfWeek = 7,
            courses = emptyList(),
        )

        compose.setContent {
            MobileScheduleTheme {
                TodayScreen(
                    state = TodayUiState.Ready(schedule, LocalTime.of(12, 0)),
                )
            }
        }

        compose.onNodeWithTag("today_empty").assertIsDisplayed()
        compose.onNodeWithTag("today_empty_title").assertIsDisplayed()
        compose.onNodeWithText("今天没有课").assertIsDisplayed()

        saveSyntheticScreenshot(
            "today-schedule-empty.png",
            compose.onNodeWithTag("today_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun todayScreenRendersBeforeSemesterCountdown() {
        val schedule = TodaySchedule.BeforeSemester(
            semester = semester,
            today = LocalDate.of(2026, 8, 28),
            daysUntilStart = 10,
        )

        compose.setContent {
            MobileScheduleTheme {
                TodayScreen(
                    state = TodayUiState.Ready(schedule, LocalTime.of(12, 0)),
                )
            }
        }

        compose.onNodeWithTag("today_empty").assertIsDisplayed()
        compose.onNodeWithText("学期尚未开始").assertIsDisplayed()
        compose.onNodeWithText("开学倒计时 10 天", substring = true).assertIsDisplayed()

        saveSyntheticScreenshot(
            "today-schedule-before.png",
            compose.onNodeWithTag("today_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun testLunarDateCalculationForOctober15() {
        val lunar = com.example.mobileschedule.ui.today.LunarCalendarHelper.getLunarDateString(LocalDate.of(2026, 10, 15))
        assertEquals("农历九月初六", lunar)
    }
}
