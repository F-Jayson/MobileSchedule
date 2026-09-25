package com.example.mobileschedule

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.ui.schedule.CourseDetailUiState
import com.example.mobileschedule.ui.schedule.ScheduleScreen
import com.example.mobileschedule.ui.schedule.ScheduleUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 9, 21)

    @Test fun buttonsAndWeekPickerRespectBoundsAndReturnToRealWeek() {
        compose.setContent {
            var browsing by remember { mutableIntStateOf(2) }
            TestScreenHost {
                ScheduleScreen(
                    state = state(browsing, WeekPosition.InSemester(2)),
                    onPreviousWeek = { browsing-- },
                    onNextWeek = { browsing++ },
                    onSelectWeek = { browsing = it },
                    onReturnToCurrentWeek = { browsing = 2 },
                )
            }
        }
        compose.onNodeWithTag("week_title").assertTextContains("第2周", substring = true)
        compose.onNodeWithTag("previous_week").performClick()
        compose.onNodeWithTag("week_title").assertTextContains("第1周", substring = true)
        compose.onNodeWithTag("previous_week").assertIsNotEnabled()
        compose.onNodeWithTag("return_to_current").performClick()
        compose.onNodeWithTag("week_title").assertTextContains("第2周", substring = true)
        compose.onNodeWithTag("week_title").performClick()
        compose.onNodeWithTag("week_picker").assertIsDisplayed()
        compose.onNodeWithTag("week_option_3").performClick()
        compose.onNodeWithTag("week_title").assertTextContains("第3周", substring = true)
        compose.onNodeWithTag("next_week").assertIsNotEnabled()
    }

    @Test fun onlyTopBarSwipeChangesWeekAndOutsideSemesterHasNoFalseCurrentWeek() {
        compose.setContent {
            var browsing by remember { mutableIntStateOf(1) }
            TestScreenHost {
                ScheduleScreen(
                    state = state(browsing, WeekPosition.BeforeSemester),
                    onPreviousWeek = { browsing-- },
                    onNextWeek = { browsing++ },
                    onSelectWeek = { browsing = it },
                    onReturnToCurrentWeek = { browsing = 1 },
                )
            }
        }
        compose.onNodeWithTag("semester_boundary").assertTextContains("学期尚未开始", substring = true)
        assertTrue(compose.onAllNodesWithTag("return_to_current").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("week_body_scroll").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("week_title").assertTextContains("第1周", substring = true)
        compose.onNodeWithTag("week_switch_bar").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("week_title").assertTextContains("第2周", substring = true)
        compose.onNodeWithTag("return_to_boundary").performClick()
        compose.onNodeWithTag("week_title").assertTextContains("第1周", substring = true)
    }

    @Test fun conflictListOpensEveryFullDetailAndMissingCourseIsExplained() {
        val source = SourceScope("fjnu", "zhengfang", "2026-2027-1")
        val first = course(7, "软件工程甲", source)
        val second = course(8, "软件工程乙", source)
        compose.setContent {
            var selected by remember { mutableLongStateOf(0) }
            val chosen = listOf(first, second).firstOrNull { it.id == selected }
            TestScreenHost {
                ScheduleScreen(
                    state = state(1, WeekPosition.InSemester(1), listOf(first, second), configuredTimes = true),
                    detailState = if (chosen == null) CourseDetailUiState.Closed else CourseDetailUiState.Ready(
                        CourseDetail(chosen, "本地秋季学期", LocalTime.of(8, 0), LocalTime.of(9, 35)),
                        "2026—2027学年第一学期",
                    ),
                    onCourseClick = { selected = it },
                    onDismissDetail = { selected = 0 },
                )
            }
        }
        compose.onNodeWithTag("overlap_2_3").performClick()
        compose.onNodeWithTag("conflict_course_7").performClick()
        compose.onNodeWithTag("course_detail").assertIsDisplayed()
        compose.onNodeWithTag("detail_name").assertTextContains("软件工程甲", substring = true)
        compose.onNodeWithTag("detail_teacher").assertTextContains("测试教师", substring = true)
        compose.onNodeWithTag("detail_location").assertTextContains("实验楼301", substring = true)
        compose.onNodeWithTag("detail_time").assertTextContains("08:00–09:35", substring = true)
        compose.onNodeWithTag("detail_weeks").assertTextContains("1、3、5", substring = true)
        compose.onNodeWithTag("detail_source_term").assertTextContains("2026—2027学年第一学期", substring = true)
        compose.onNodeWithTag("detail_close").performClick()
        compose.onNodeWithTag("overlap_2_3").performClick()
        compose.onNodeWithTag("conflict_course_8").performClick()
        compose.onNodeWithTag("detail_name").assertTextContains("软件工程乙", substring = true)
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onNodeWithTag("detail_close").assertIsDisplayed() }.isSuccess
        }
        compose.mainClock.advanceTimeBy(500)
        saveScreenshot("week-detail-fixture.png")
    }

    @Test fun replacedCourseAndUnconfiguredTimesHaveHonestDetailStates() {
        val manual = CourseArrangement(9, 1, "手动课程", null, null, 1, 1, 1,
            setOf(1, 3), CourseOrigin.Manual)
        compose.setContent {
            var detail by remember { mutableStateOf<CourseDetailUiState>(CourseDetailUiState.Missing) }
            TestScreenHost {
                ScheduleScreen(
                    state = state(1, WeekPosition.InSemester(1), listOf(manual)),
                    detailState = detail,
                    onCourseClick = { detail = CourseDetailUiState.Ready(
                        CourseDetail(manual, "本地秋季学期", null, null), null) },
                    onDismissDetail = { detail = CourseDetailUiState.Closed },
                )
            }
        }
        compose.onNodeWithTag("detail_missing").assertTextContains("已不存在", substring = true)
        compose.onNodeWithTag("detail_close").performClick()
        compose.onNodeWithTag("course_9").performClick()
        compose.onNodeWithTag("detail_time").assertTextContains("未配置节次时间", substring = true)
        compose.onNodeWithTag("detail_source").assertTextContains("手动课程", substring = true)
        compose.onNodeWithTag("detail_teacher").assertTextContains("未提供", substring = true)
    }

    @Composable
    private fun TestScreenHost(content: @Composable () -> Unit) {
        MaterialTheme {
            Scaffold { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
        }
    }

    private fun state(week: Int, current: WeekPosition, courses: List<CourseArrangement> = emptyList(),
        configuredTimes: Boolean = false): ScheduleUiState.Ready {
        val times = if (configuredTimes) listOf(
            SectionTime(1, LocalTime.of(6, 30), LocalTime.of(7, 15)),
            SectionTime(2, LocalTime.of(7, 15), LocalTime.of(8, 0)),
            SectionTime(3, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            SectionTime(4, LocalTime.of(8, 45), LocalTime.of(9, 35)),
            SectionTime(5, LocalTime.of(9, 45), LocalTime.of(10, 30)),
            SectionTime(6, LocalTime.of(10, 30), LocalTime.of(11, 15)),
            SectionTime(7, LocalTime.of(11, 15), LocalTime.of(12, 0)),
            SectionTime(8, LocalTime.of(12, 0), LocalTime.of(12, 45)),
        ) else emptyList()
        val config = SemesterConfig(monday, 3, 8, times, 1)
        val start = monday.plusWeeks((week - 1).toLong())
        return ScheduleUiState.Ready(
            ActiveWeek(Semester(1, "本地秋季学期", emptySet(), config), WeekSchedule(1, week, start, start.plusDays(6), courses)),
            current,
            if (current is WeekPosition.BeforeSemester) monday.minusDays(1) else monday.plusDays(8),
        )
    }

    private fun course(id: Long, name: String, scope: SourceScope) = CourseArrangement(
        id, 1, name, "测试教师", "实验楼301", 2, 3, 4, setOf(1, 3, 5),
        CourseOrigin.SchoolImport(scope, "batch-1", "entry-$id"),
    )

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileScheduleTest")
        }) ?: error("Cannot create screenshot")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            (InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("Cannot capture screen")).compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Cannot write screenshot")
    }
}
