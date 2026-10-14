package com.example.mobileschedule

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.preferences.AppPreferencesStore
import com.example.mobileschedule.data.preferences.AppThemeMode
import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import com.example.mobileschedule.ui.schedule.WeekScheduleGrid
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.LocalIsDarkTheme
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ScheduleAdvancedViewThemeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun testFiveColumnsLayout_hidesWeekendAndShowsNoticeBadgeWhenWeekendCoursesExist() {
        val week = createFixture()
        compose.setContent {
            MaterialTheme {
                WeekScheduleGrid(
                    week = week,
                    displayOptions = ScheduleDisplayOptions(
                        showWeekend = false,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                )
            }
        }

        // 1. 周一至周五表头存在，周六周日表头不存在
        compose.onNodeWithTag("day_1").assertIsDisplayed()
        compose.onNodeWithTag("day_2").assertIsDisplayed()
        compose.onNodeWithTag("day_3").assertIsDisplayed()
        compose.onNodeWithTag("day_4").assertIsDisplayed()
        compose.onNodeWithTag("day_5").assertIsDisplayed()
        compose.onNodeWithTag("day_6").assertDoesNotExist()
        compose.onNodeWithTag("day_7").assertDoesNotExist()

        // 2. 工作日课程卡片正常显示，周末课程卡片被隐藏
        compose.onNodeWithTag("course_1").assertIsDisplayed()
        compose.onNodeWithTag("course_7").assertDoesNotExist()

        // 3. 周末课程提示角标显示
        compose.onNodeWithTag("weekend_course_badge").assertIsDisplayed()

        saveSyntheticScreenshot(
            "schedule-view-5col-no-weekend.png",
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun testSevenColumnsLayout_mondayFirst_displaysMondayToSunday() {
        val week = createFixture()
        compose.setContent {
            MaterialTheme {
                WeekScheduleGrid(
                    week = week,
                    displayOptions = ScheduleDisplayOptions(
                        showWeekend = true,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                )
            }
        }

        // 1. 全周 7 天表头均显示
        for (day in 1..7) {
            compose.onNodeWithTag("day_$day").assertIsDisplayed()
        }

        // 2. 周一在最左侧，周日在最右侧
        val day1Bounds = compose.onNodeWithTag("day_1").fetchSemanticsNode().boundsInRoot
        val day2Bounds = compose.onNodeWithTag("day_2").fetchSemanticsNode().boundsInRoot
        val day7Bounds = compose.onNodeWithTag("day_7").fetchSemanticsNode().boundsInRoot

        assertTrue("周二应在周一右侧", day2Bounds.left > day1Bounds.left)
        assertTrue("周日应在周二右侧", day7Bounds.left > day2Bounds.left)

        // 3. 工作日与周末课程均显示
        compose.onNodeWithTag("course_1").assertIsDisplayed()
        compose.onNodeWithTag("course_7").assertIsDisplayed()

        // 4. 周末卡片与周日表头水平对齐
        val card7Bounds = compose.onNodeWithTag("course_7").fetchSemanticsNode().boundsInRoot
        assertTrue("周日课程卡片需与周日表头对齐", kotlin.math.abs(card7Bounds.left - day7Bounds.left) < 16f)

        saveSyntheticScreenshot(
            "schedule-view-7col-monday-first.png",
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun testSevenColumnsLayout_sundayFirst_displaysSundayToSaturday() {
        val week = createFixture()
        compose.setContent {
            MaterialTheme {
                WeekScheduleGrid(
                    week = week,
                    displayOptions = ScheduleDisplayOptions(
                        showWeekend = true,
                        firstDayOfWeek = DayOfWeek.SUNDAY,
                    ),
                )
            }
        }

        // 1. 全周 7 天均显示
        for (day in 1..7) {
            compose.onNodeWithTag("day_$day").assertIsDisplayed()
        }

        // 2. 周日起始：周日 (day_7) 在第 0 列（最左侧），周一 (day_1) 在第 1 列
        val day7Bounds = compose.onNodeWithTag("day_7").fetchSemanticsNode().boundsInRoot
        val day1Bounds = compose.onNodeWithTag("day_1").fetchSemanticsNode().boundsInRoot
        val day6Bounds = compose.onNodeWithTag("day_6").fetchSemanticsNode().boundsInRoot

        assertTrue("周日应在最左侧（周一左边）", day7Bounds.left < day1Bounds.left)
        assertTrue("周六应在周一右侧", day6Bounds.left > day1Bounds.left)

        // 3. 周日课程卡片也应渲染在第 0 列，与周日表头对齐
        compose.onNodeWithTag("course_7").assertIsDisplayed()
        val card7Bounds = compose.onNodeWithTag("course_7").fetchSemanticsNode().boundsInRoot
        assertTrue("周日课程卡片需与周日表头水平对齐", kotlin.math.abs(card7Bounds.left - day7Bounds.left) < 16f)

        saveSyntheticScreenshot(
            "schedule-view-7col-sunday-first.png",
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun testThemeModePersistenceAndDialogSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppPreferencesStore(context, "test_preferences_theme")

        // 默认跟随系统
        assertEquals(AppThemeMode.SYSTEM, preferences.getThemeMode())

        // 保存深色模式
        assertTrue(preferences.saveThemeMode(AppThemeMode.DARK))
        assertEquals(AppThemeMode.DARK, preferences.getThemeMode())

        // 保存浅色模式
        assertTrue(preferences.saveThemeMode(AppThemeMode.LIGHT))
        assertEquals(AppThemeMode.LIGHT, preferences.getThemeMode())

        // UI 对话框选择验证
        var selectedMode by mutableStateOf(AppThemeMode.SYSTEM)
        compose.setContent {
            MaterialTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    themeMode = selectedMode,
                    onThemeModeChange = { selectedMode = it },
                )
            }
        }

        // 点击外观主题项打开对话框
        compose.onNodeWithTag("settings_theme_entry").assertIsDisplayed().performClick()

        // 验证三种模式单选选项可见
        compose.onNodeWithTag("theme_radio_system").assertIsDisplayed()
        compose.onNodeWithTag("theme_radio_light").assertIsDisplayed()
        compose.onNodeWithTag("theme_radio_dark").assertIsDisplayed()

        saveSyntheticScreenshot(
            "settings-theme-selection-dialog.png",
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap(),
        )

        // 点击深色模式
        compose.onNodeWithTag("theme_radio_dark").performClick()
        compose.runOnIdle {
            assertEquals(AppThemeMode.DARK, selectedMode)
        }
    }

    @Test
    fun testMobileScheduleTheme_appliesThemeModeInstantly() {
        var currentThemeMode by mutableStateOf(AppThemeMode.DARK)
        var observedDark = false

        compose.setContent {
            MobileScheduleTheme(themeMode = currentThemeMode) {
                observedDark = LocalIsDarkTheme.current
                Text("Theme Test: ${LocalIsDarkTheme.current}")
            }
        }
        compose.runOnIdle {
            assertTrue("MobileScheduleTheme with DARK mode must provide LocalIsDarkTheme as true", observedDark)
        }

        currentThemeMode = AppThemeMode.LIGHT
        compose.runOnIdle {
            assertFalse("MobileScheduleTheme with LIGHT mode must provide LocalIsDarkTheme as false", observedDark)
        }
    }

    private fun createFixture(): ActiveWeek {
        val monday = LocalDate.of(2026, 3, 2)
        val courses = listOf(
            CourseArrangement(
                id = 1L, semesterId = 1L, name = "面向对象程序设计",
                teacher = "王教授", location = "软件楼 102",
                dayOfWeek = 1, startSection = 1, endSection = 2,
                weeks = setOf(1), origin = CourseOrigin.Manual,
            ),
            CourseArrangement(
                id = 7L, semesterId = 1L, name = "周末创新创业实践",
                teacher = "陈导师", location = "创客空间",
                dayOfWeek = 7, startSection = 3, endSection = 4,
                weeks = setOf(1), origin = CourseOrigin.Manual,
            ),
        )
        val times = (1..8).map {
            SectionTime(
                section = it,
                start = LocalTime.of(8 + it, 0),
                end = LocalTime.of(8 + it, 45),
            )
        }
        val config = SemesterConfig(
            firstWeekMonday = monday,
            totalWeeks = 18,
            totalSections = 8,
            sectionTimes = times,
            revision = 1L,
        )
        return ActiveWeek(
            semester = Semester(1L, "2025-2026学年第二学期", emptySet(), config),
            schedule = WeekSchedule(1L, 1, monday, monday.plusDays(6), courses),
        )
    }
}
