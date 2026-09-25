package com.example.mobileschedule

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseDetail
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.ui.schedule.CourseDetailUiState
import com.example.mobileschedule.ui.schedule.ScheduleScreen
import com.example.mobileschedule.ui.schedule.ScheduleUiState
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiAdaptationTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 9, 21)
    private val longName = "人工智能导论与跨学科项目实践课程设计（合成测试）"

    @Test fun narrowDarkLargeTextKeepsImportConflictAndFullDetailReachable() {
        val courses = listOf(course(1, "短课程"), course(2, longName))
        val semester = Semester(1, "合成超长学期显示名称用于适配检查", emptySet(),
            SemesterConfig(monday, 2, 4, emptyList(), 1))
        val ready = ScheduleUiState.Ready(ActiveWeek(semester,
            WeekSchedule(1, 1, monday, monday.plusDays(6), courses)),
            WeekPosition.InSemester(1), monday)
        compose.setContent {
            val density = LocalDensity.current
            var selected by remember { mutableLongStateOf(0) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Scaffold { padding ->
                        Surface(Modifier.padding(padding).width(320.dp).height(640.dp).testTag("adaptation_frame")) {
                            ScheduleScreen(ready,
                                detailState = courses.firstOrNull { it.id == selected }?.let {
                                    CourseDetailUiState.Ready(CourseDetail(it, semester.displayName, null, null), null)
                                } ?: CourseDetailUiState.Closed,
                                onCourseClick = { selected = it }, onDismissDetail = { selected = 0 })
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("schedule_import_top").assertIsDisplayed()
        compose.onNodeWithTag("week_title").assertIsDisplayed()
        compose.onNodeWithTag("overlap_1_1").assertIsDisplayed()
            .assertContentDescriptionContains("同一时段2门课程", substring = true)
        saveScreenshot("ui-narrow-dark-font-fixture.png", frameOnly = true)
        compose.onNodeWithTag("overlap_1_1").performClick()
        compose.onNodeWithTag("conflict_course_2").assertIsDisplayed().performClick()
        compose.onNodeWithTag("detail_name").assertTextContains(longName, substring = true)
        compose.onNodeWithTag("detail_close").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        saveScreenshot("ui-narrow-dark-detail.png")
    }

    @Test fun narrowDarkLargeTextKeepsSettingsEditAndImportReachable() {
        val semester = Semester(1, "合成超长学期显示名称用于适配检查", emptySet(),
            SemesterConfig(monday, 2, 4, emptyList(), 1))
        var edits = 0
        var imports = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Scaffold { padding ->
                        Surface(Modifier.padding(padding).width(320.dp).height(640.dp).testTag("adaptation_frame")) {
                            SettingsHomeScreen(SettingsHomeUiState.Ready(listOf(semester), 1),
                                onCreate = {}, onEdit = { edits++ }, onActivate = {},
                                onImport = { imports++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("settings_page").performScrollToIndex(2)
        compose.onNodeWithTag("settings_edit_1").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings_page").performScrollToIndex(3)
        compose.onNodeWithTag("settings_import").assertIsDisplayed().performClick()
        assertEquals(1, edits)
        assertEquals(1, imports)
        saveScreenshot("ui-narrow-dark-settings.png", frameOnly = true)
    }

    private fun course(id: Long, name: String) = CourseArrangement(
        id, 1, name, "合成教师", "合成地点A101", 1, 1, 2, setOf(1), CourseOrigin.Manual)

    private fun saveScreenshot(name: String, frameOnly: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileScheduleTest")
        }) ?: error("Cannot create screenshot")
        resolver.openOutputStream(uri)?.use { output ->
            (if (frameOnly) compose.onNodeWithTag("adaptation_frame").captureToImage().asAndroidBitmap()
                else instrumentation.uiAutomation.takeScreenshot() ?: error("Cannot capture screen"))
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Cannot write screenshot")
    }
}
