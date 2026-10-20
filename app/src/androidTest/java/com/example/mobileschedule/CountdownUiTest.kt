package com.example.mobileschedule

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.TodaySchedule
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import com.example.mobileschedule.ui.today.TodayScreen
import com.example.mobileschedule.ui.today.TodayUiState
import com.example.mobileschedule.ui.today.countdown.CountdownBottomSheet
import com.example.mobileschedule.ui.today.countdown.CountdownCategory
import com.example.mobileschedule.ui.today.countdown.CountdownTarget
import com.example.mobileschedule.ui.today.countdown.TodayCountdownEncouragementCard
import com.example.mobileschedule.ui.today.countdown.TodayCountdownPill
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountdownUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(monday, 16, 12, emptyList(), 1L)
    private val semester = Semester(1L, "福建师范大学 2026秋季学期", emptySet(), config)

    private val samplePostgradTarget = CountdownTarget(
        id = "preset_postgraduate_exam",
        title = "全国硕士研究生招生考试 (初试)",
        shortTitle = "考研初试",
        category = CountdownCategory.EXAM,
        targetDate = LocalDate.of(2026, 12, 19),
        daysRemaining = 60,
        motto = "一研为定，定峰顶相见！",
        isPinned = true,
        isCustom = false,
    )

    private val sampleVacationTarget = CountdownTarget(
        id = "preset_vacation",
        title = "寒假放假",
        shortTitle = "寒假放假",
        category = CountdownCategory.HOLIDAY,
        targetDate = LocalDate.of(2027, 1, 18),
        daysRemaining = 90,
        motto = "假期倒计时，满载收获迎接全新假期！",
        isPinned = false,
        isCustom = false,
    )

    private val sampleCustomTarget = CountdownTarget(
        id = "custom_1",
        title = "软件工程大作业答辩",
        shortTitle = "软件工程大作业答辩",
        category = CountdownCategory.CUSTOM,
        targetDate = LocalDate.of(2026, 11, 20),
        daysRemaining = 31,
        motto = "全力冲刺！",
        isPinned = false,
        isCustom = true,
    )

    @Test
    fun countdownPillRendersOnHeaderAndTriggersClick() {
        var clicked = false

        val schedule = TodaySchedule.Ready(
            semester = semester,
            currentWeek = 1,
            date = monday,
            dayOfWeek = 1,
            courses = emptyList(),
        )

        val readyState = TodayUiState.Ready(
            schedule = schedule,
            currentTime = LocalTime.of(9, 0),
            countdowns = listOf(samplePostgradTarget, sampleVacationTarget),
            primaryCountdown = samplePostgradTarget,
        )

        compose.setContent {
            MobileScheduleTheme {
                TodayScreen(
                    state = readyState,
                    onCountdownClick = { clicked = true },
                )
            }
        }

        // Pill should be visible with short title and days
        compose.onNodeWithTag("today_countdown_pill").assertIsDisplayed()
        compose.onNodeWithText("考研初试 · 60天", substring = true).assertIsDisplayed()

        // Click on the pill
        compose.onNodeWithTag("today_countdown_pill").performClick()
        assertTrue("onCountdownClick should have been called", clicked)
    }

    @Test
    fun todayCountdownEncouragementCardRendersInEmptyStateAndSwitchesChips() {
        var manageClicked = false
        val countdowns = listOf(samplePostgradTarget, sampleVacationTarget, sampleCustomTarget)

        val schedule = TodaySchedule.Ready(
            semester = semester,
            currentWeek = 1,
            date = monday.plusDays(6), // Sunday
            dayOfWeek = 7,
            courses = emptyList(),
        )

        val readyState = TodayUiState.Ready(
            schedule = schedule,
            currentTime = LocalTime.of(10, 0),
            countdowns = countdowns,
            primaryCountdown = samplePostgradTarget,
        )

        compose.setContent {
            MobileScheduleTheme {
                TodayScreen(
                    state = readyState,
                    onCountdownClick = { manageClicked = true },
                )
            }
        }

        // Verify empty view card & encouragement card
        compose.onNodeWithTag("today_empty").assertIsDisplayed()
        compose.onNodeWithTag("today_countdown_card").assertIsDisplayed()
        compose.onNodeWithText("全国硕士研究生招生考试 (初试)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("60").assertIsDisplayed()
        compose.onNodeWithText("一研为定，定峰顶相见！", substring = true).assertIsDisplayed()

        // Verify chips for switcher
        compose.onNodeWithTag("countdown_chip_preset_vacation").assertIsDisplayed()
        compose.onNodeWithTag("countdown_chip_preset_vacation").performClick()

        // After switching chip, vacation target info is shown
        compose.onAllNodesWithText("寒假放假", substring = true).assertCountEquals(2)
        compose.onNodeWithText("90").assertIsDisplayed()

        // Click manage button
        compose.onNodeWithTag("countdown_manage_button").performClick()
        assertTrue("manageClicked should be triggered", manageClicked)

        // Save screenshot evidence
        saveSyntheticScreenshot(
            "today-countdown-encouragement.png",
            compose.onNodeWithTag("today_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun countdownBottomSheetRendersAndInteracts() {
        var pinnedId: String? = null
        var deletedId: String? = null
        var addedTitle: String? = null
        var addedDate: LocalDate? = null

        val items = listOf(samplePostgradTarget, sampleCustomTarget)

        compose.setContent {
            MobileScheduleTheme {
                CountdownBottomSheet(
                    countdowns = items,
                    onDismiss = {},
                    onPinTarget = { pinnedId = it },
                    onDeleteCustom = { deletedId = it },
                    onAddCustom = { title, date, _ ->
                        addedTitle = title
                        addedDate = date
                    },
                )
            }
        }

        // Bottom sheet content displayed
        compose.onNodeWithTag("countdown_bottom_sheet").assertIsDisplayed()
        compose.onNodeWithText("目标倒计时").assertIsDisplayed()
        compose.onNodeWithText("已置顶").assertIsDisplayed()

        saveSyntheticScreenshot(
            "today-countdown-sheet.png",
            compose.onNodeWithTag("countdown_bottom_sheet").captureToImage().asAndroidBitmap(),
        )

        // Test unpinning postgrad target
        compose.onNodeWithTag("pin_preset_postgraduate_exam").performClick()
        assertEquals(null, pinnedId) // Clicking pinned item toggles to null

        // Test deleting custom target
        compose.onNodeWithTag("delete_custom_1").performClick()
        assertEquals("custom_1", deletedId)

        // Test opening Add Dialog
        compose.onNodeWithTag("countdown_add_button").performClick()
        compose.onNodeWithText("添加自定义目标").assertIsDisplayed()

        // Fill in title
        compose.onNodeWithTag("add_countdown_title").performTextInput("计算机二级考试")
        compose.onNodeWithTag("add_countdown_confirm").performClick()

        assertEquals("计算机二级考试", addedTitle)
    }
}
