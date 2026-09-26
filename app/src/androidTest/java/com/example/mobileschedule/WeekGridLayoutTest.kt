package com.example.mobileschedule

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.ui.schedule.WeekScheduleGrid
import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekGridLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun crossSectionCardUsesRowHeightAndForwardsDetailId() {
        var selectedId = -1L
        compose.setContent { MaterialTheme { WeekScheduleGrid(fixture(), onCourseClick = { selectedId = it }) } }
        val bounds = compose.onNodeWithTag("course_2").fetchSemanticsNode().boundsInRoot
        val single = compose.onNodeWithTag("course_1").fetchSemanticsNode().boundsInRoot
        assertTrue("a two-section card must be taller than a one-section card", bounds.height > single.height * 1.8f)
        assertTrue("Tuesday must be right of Monday", bounds.left > single.left)
        assertTrue("section 3 must be below section 1", bounds.top > single.top)
        compose.onNodeWithTag("course_2").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(2L, selectedId) }
    }

    @Test fun allSevenDaysFitScreenAndWeekendCardAlignsWithoutHorizontalScroll() {
        compose.setContent { MaterialTheme { WeekScheduleGrid(fixture()) } }
        compose.onNodeWithTag("day_7").assertIsDisplayed()
        compose.onNodeWithTag("course_7").assertIsDisplayed()
        val grid = compose.onNodeWithTag("week_grid").fetchSemanticsNode().boundsInRoot
        val day = compose.onNodeWithTag("day_7").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("course_7").fetchSemanticsNode().boundsInRoot
        assertTrue("Sunday must fit on screen without horizontal scrolling", day.right <= grid.right + 2f)
        assertTrue("Sunday card must align with its date header", kotlin.math.abs(day.left - card.left) < 12f)
    }

    @Test fun rulerShowsEightUserProvidedDefaultIntervalsWhenNoTimesConfigured() {
        compose.setContent { MaterialTheme { WeekScheduleGrid(fixture(configuredTimes = false)) } }
        val expected = listOf(
            "08:20" to "09:05", "09:15" to "10:00", "10:20" to "11:05", "11:15" to "12:00",
            "14:00" to "14:45", "14:55" to "15:40", "15:50" to "16:35", "16:45" to "17:30",
        )
        expected.forEachIndexed { index, (start, end) ->
            compose.onNodeWithTag("section_time_${index + 1}").assertTextContains(start, substring = true)
            compose.onNodeWithTag("section_time_${index + 1}").assertTextContains(end, substring = true)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "week-grid-default-times.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileScheduleTest")
        }) ?: error("Cannot create layout evidence")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Cannot write layout evidence")
    }

    @Test fun hidingOptionalCardFieldsKeepsCourseCardAccessible() {
        lateinit var changeOptions: (ScheduleDisplayOptions) -> Unit
        compose.setContent { MaterialTheme {
            var options by remember { mutableStateOf(ScheduleDisplayOptions()) }
            changeOptions = { options = it }
            WeekScheduleGrid(fixture(), displayOptions = options)
        } }
        assertTrue(compose.onAllNodesWithText("实验楼 301", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("测试教师", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { changeOptions(ScheduleDisplayOptions(showLocation = false, showTeacher = false)) }
        assertTrue(compose.onAllNodesWithText("实验楼 301", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("测试教师", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("course_2").assertIsDisplayed()
    }

    private fun fixture(configuredTimes: Boolean = true): ActiveWeek {
        val monday = LocalDate.of(2026, 9, 21)
        val courses = listOf(
            course(1, 1, 1, 1, "高等数学", "A101"),
            course(2, 2, 3, 4, "软件工程与课程设计", "实验楼 301"),
            course(7, 7, 1, 2, "周末实践", "校外教学点"),
        )
        val times = (1..10).map {
            SectionTime(it, LocalTime.of(8 + (it - 1) / 2, if (it % 2 == 1) 0 else 30),
                LocalTime.of(8 + (it - 1) / 2, if (it % 2 == 1) 25 else 55))
        }
        val config = SemesterConfig(monday, 20, 10, if (configuredTimes) times else emptyList(), 1)
        return ActiveWeek(Semester(1, "设备布局测试学期", emptySet(), config),
            WeekSchedule(1, 1, monday, monday.plusDays(6), courses))
    }

    private fun course(id: Long, day: Int, start: Int, end: Int, name: String, place: String) =
        CourseArrangement(id, 1, name, "测试教师", place, day, start, end, setOf(1), CourseOrigin.Manual)
}
