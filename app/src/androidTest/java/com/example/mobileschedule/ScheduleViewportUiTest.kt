package com.example.mobileschedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.ui.schedule.ScheduleScreen
import com.example.mobileschedule.ui.schedule.ScheduleUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleViewportUiTest {
    @get:Rule val compose = createComposeRule()

    private fun state(sections: Int = 8): ScheduleUiState.Ready {
        val monday = LocalDate.of(2026, 10, 12)
        val semester = Semester(1, "合成测试学期", emptySet(),
            SemesterConfig(monday, 20, sections, emptyList(), 1))
        val courses = listOf(
            CourseArrangement(1, 1, "合成软件工程", "合成教师", "测试楼101", 2, 1, 2, setOf(1), CourseOrigin.Manual),
            CourseArrangement(2, 1, "合成课程实践", "合成教师", "测试楼201", 5, sections - 1, sections, setOf(1), CourseOrigin.Manual),
        )
        return ScheduleUiState.Ready(ActiveWeek(semester,
            WeekSchedule(1, 1, monday, monday.plusDays(6), courses)), WeekPosition.InSemester(1), monday)
    }

    private fun assertFinalSectionFits() {
        val viewport = compose.onNodeWithTag("week_grid").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("course_2").getUnclippedBoundsInRoot()
        val time = compose.onNodeWithTag("section_time_8").getUnclippedBoundsInRoot()
        assertTrue("Last course must fit fully in the grid: $card / $viewport", card.bottom <= viewport.bottom)
        assertTrue("Last section end time must fit fully in the grid: $time / $viewport", time.bottom <= viewport.bottom)
        compose.onNodeWithTag("section_time_8").assertTextContains("17:30", substring = true)
    }

    @Test fun eightSectionsFitFullyWhenTheAvailableHeightChanges() {
        lateinit var resize: () -> Unit
        compose.setContent { MobileScheduleTheme {
            var height by remember { mutableStateOf(640.dp) }
            resize = { height = 560.dp }
            Box(Modifier.width(360.dp).height(height).testTag("viewport_fixture")) { ScheduleScreen(state()) }
        } }
        assertFinalSectionFits()
        saveSyntheticScreenshot("schedule-eight-sections.png",
            compose.onNodeWithTag("viewport_fixture").captureToImage().asAndroidBitmap())
        compose.runOnIdle { resize() }
        assertFinalSectionFits()
        saveSyntheticScreenshot("schedule-compact-height.png",
            compose.onNodeWithTag("viewport_fixture").captureToImage().asAndroidBitmap())
    }

    @Test fun largeFontRetainsVerticalScrollingToTheFinalSection() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                MobileScheduleTheme {
                    Box(Modifier.width(320.dp).height(560.dp).testTag("viewport_fixture")) { ScheduleScreen(state(12)) }
                }
            }
        }
        compose.onNodeWithTag("week_body_scroll").performTouchInput { swipeUp() }
        compose.onNodeWithTag("week_body_scroll").performTouchInput { swipeUp() }
        compose.onNodeWithTag("week_body_scroll").performTouchInput { swipeUp() }
        compose.onNodeWithTag("course_2").assertIsDisplayed()
        compose.onNodeWithTag("course_2").performClick()
        saveSyntheticScreenshot("schedule-large-font-final-section.png",
            compose.onNodeWithTag("viewport_fixture").captureToImage().asAndroidBitmap())
    }
}
