package com.example.mobileschedule

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.data.rules.ScheduleRules
import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsScreen
import com.example.mobileschedule.ui.schedule.ScheduleSemesterHeader
import com.example.mobileschedule.ui.schedule.ScheduleUiState
import com.example.mobileschedule.ui.schedule.export.ExportOptions
import com.example.mobileschedule.ui.schedule.export.ExportResolution
import com.example.mobileschedule.ui.schedule.export.ExportTheme
import com.example.mobileschedule.ui.schedule.export.ScheduleExportSheet
import com.example.mobileschedule.ui.schedule.export.ScheduleImageExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ScheduleImageExportUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renderBitmapDirectly_verifiesDifferentSectionsAndColumns() {
        val fixtureWeek = createFixtureActiveWeek()
        val monday = fixtureWeek.semester.config!!.firstWeekMonday
        val arrangements = fixtureWeek.schedule.allArrangements

        // 1. 测试用例 A：12 节课、7 列（全周）、浅色柔和主题、2x 超清分辨率
        val optionsA = ExportOptions(
            theme = ExportTheme.LIGHT,
            resolution = ExportResolution.SUPER_2X,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showNonCurrentWeekCourses = false,
            showWatermark = true,
        )
        val bitmapA = ScheduleImageExporter.renderBitmap(
            semesterName = fixtureWeek.semester.displayName,
            schoolName = "福建师范大学",
            weekNum = 1,
            firstWeekMonday = monday,
            maxSections = 12,
            sectionTimes = fixtureWeek.semester.config!!.sectionTimes,
            arrangements = arrangements,
            today = monday,
            options = optionsA,
        )
        assertNotNull(bitmapA)
        assertTrue("Bitmap A width should be > 1000px", bitmapA.width >= 1000)
        assertTrue("Bitmap A height should be > 1900px", bitmapA.height >= 1900)
        saveSyntheticScreenshot("schedule-export-12sec-7days-light-2x.png", bitmapA)

        // 2. 测试用例 B：8 节课、5 列（工作日）、暗黑壁纸主题、2x 超清分辨率
        val optionsB = ExportOptions(
            theme = ExportTheme.DARK,
            resolution = ExportResolution.SUPER_2X,
            showWeekend = false,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showNonCurrentWeekCourses = false,
            showWatermark = true,
        )
        val bitmapB = ScheduleImageExporter.renderBitmap(
            semesterName = fixtureWeek.semester.displayName,
            schoolName = "福建师范大学",
            weekNum = 1,
            firstWeekMonday = monday,
            maxSections = 8,
            sectionTimes = fixtureWeek.semester.config!!.sectionTimes.take(8),
            arrangements = arrangements.filter { it.startSection <= 8 },
            today = monday,
            options = optionsB,
        )
        assertNotNull(bitmapB)
        assertTrue("Bitmap B width for 5 days should be > 950px", bitmapB.width >= 950)
        assertTrue("Bitmap B height for 8 sections should be in 1350~1600px", bitmapB.height in 1350..1600)
        saveSyntheticScreenshot("schedule-export-8sec-5days-dark-2x.png", bitmapB)

        // 3. 测试用例 C：12 节课、7 列、纯白打印模式、包含非本周课程（灰显虚线）
        val optionsC = ExportOptions(
            theme = ExportTheme.PRINT,
            resolution = ExportResolution.SUPER_2X,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showNonCurrentWeekCourses = true,
            showWatermark = true,
        )
        val bitmapC = ScheduleImageExporter.renderBitmap(
            semesterName = fixtureWeek.semester.displayName,
            schoolName = "福建师范大学",
            weekNum = 1,
            firstWeekMonday = monday,
            maxSections = 12,
            sectionTimes = fixtureWeek.semester.config!!.sectionTimes,
            arrangements = arrangements,
            today = monday,
            options = optionsC,
        )
        assertNotNull(bitmapC)
        saveSyntheticScreenshot("schedule-export-12sec-print-with-inactive.png", bitmapC)

        // 4. 测试用例 D：12 节课、7 列、暗黑壁纸主题、3x 极清壁纸超采样
        val optionsD = ExportOptions(
            theme = ExportTheme.DARK,
            resolution = ExportResolution.ULTRA_3X,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showNonCurrentWeekCourses = true,
            showWatermark = true,
        )
        val bitmapD = ScheduleImageExporter.renderBitmap(
            semesterName = fixtureWeek.semester.displayName,
            schoolName = "福建师范大学",
            weekNum = 1,
            firstWeekMonday = monday,
            maxSections = 12,
            sectionTimes = fixtureWeek.semester.config!!.sectionTimes,
            arrangements = arrangements,
            today = monday,
            options = optionsD,
        )
        assertNotNull(bitmapD)
        assertTrue("Bitmap D 3x ultra width should exceed 1600px", bitmapD.width > 1600)
        assertTrue("Bitmap D 3x ultra height should exceed 2800px", bitmapD.height > 2800)
        saveSyntheticScreenshot("schedule-export-12sec-dark-3x-wallpaper.png", bitmapD)
    }

    @Test
    fun scheduleExportSheetUiInteraction_togglesThemeAndResolution() {
        val fixtureWeek = createFixtureActiveWeek()

        compose.setContent {
            MaterialTheme {
                ScheduleExportSheet(
                    isOpen = true,
                    activeWeek = fixtureWeek,
                    today = fixtureWeek.semester.config!!.firstWeekMonday,
                    displayOptions = ScheduleDisplayOptions(showWeekend = true, showNonCurrentWeekCourses = false),
                    onDismiss = {},
                )
            }
        }

        // 验证弹窗可见并包含基本结构
        compose.onNodeWithTag("schedule_export_sheet").assertIsDisplayed()
        compose.onNodeWithTag("export_preview_summary").assertIsDisplayed()
        compose.onNodeWithTag("export_dimensions_text").assertIsDisplayed()
        compose.onNodeWithTag("export_dimensions_text").assertTextContains("浅色柔和", substring = true)

        // 切换暗黑壁纸主题
        compose.onNodeWithTag("export_theme_dark").assertIsDisplayed()
        compose.onNodeWithTag("export_theme_dark").performClick()
        compose.onNodeWithTag("export_dimensions_text").assertTextContains("暗黑壁纸", substring = true)

        // 切换纯白打印主题
        compose.onNodeWithTag("export_theme_print").assertIsDisplayed()
        compose.onNodeWithTag("export_theme_print").performClick()
        compose.onNodeWithTag("export_dimensions_text").assertTextContains("纯白打印", substring = true)

        // 切换 3x 极清壁纸分辨率
        compose.onNodeWithTag("export_res_3x").assertIsDisplayed()
        compose.onNodeWithTag("export_res_3x").performClick()

        // 切换开关
        compose.onNodeWithTag("export_switch_non_current_week").assertIsDisplayed()
        compose.onNodeWithTag("export_switch_non_current_week").assertIsOff()
        compose.onNodeWithTag("export_switch_non_current_week").performClick()
        compose.onNodeWithTag("export_switch_non_current_week").assertIsOn()

        // 验证保存与分享按钮挂载
        compose.onNodeWithTag("export_save_to_gallery").assertIsDisplayed()
        compose.onNodeWithTag("export_share_button").assertIsDisplayed()

        saveSyntheticScreenshot(
            "schedule-export-sheet-dialog.png",
            compose.onNodeWithTag("schedule_export_sheet").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun scheduleSemesterHeader_exportButton_triggersCallback() {
        var exportClicked = false
        val fixtureWeek = createFixtureActiveWeek()
        val readyState = ScheduleUiState.Ready(
            week = fixtureWeek,
            currentPosition = WeekPosition.InSemester(1),
            today = fixtureWeek.semester.config!!.firstWeekMonday,
        )

        compose.setContent {
            MaterialTheme {
                ScheduleSemesterHeader(
                    state = readyState,
                    showWeekend = true,
                    onExportImage = { exportClicked = true },
                )
            }
        }

        val exportButton = compose.onNodeWithTag("schedule_export_image")
        exportButton.assertIsDisplayed()
        exportButton.assertTextContains("导出长图")
        exportButton.performClick()
        assertTrue(exportClicked)
    }

    @Test
    fun scheduleDisplaySettings_exportEntry_triggersCallback() {
        var exportClicked = false

        compose.setContent {
            MaterialTheme {
                ScheduleDisplaySettingsScreen(
                    options = ScheduleDisplayOptions(),
                    onOptionsChange = {},
                    onBack = {},
                    onExportImage = { exportClicked = true },
                )
            }
        }

        val exportEntry = compose.onNodeWithTag("display_export_image_entry")
        exportEntry.assertIsDisplayed()
        exportEntry.assertTextContains("导出本周课表长图", substring = true)
        exportEntry.performClick()
        assertTrue(exportClicked)
    }

    private fun createFixtureActiveWeek(): ActiveWeek {
        val monday = LocalDate.of(2026, 9, 21)
        val activeCourse1 = CourseArrangement(
            id = 101,
            semesterId = 1,
            name = "高等数学(上)",
            teacher = "陈教授",
            location = "公教楼 101",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = setOf(1, 2, 3, 4, 5, 6, 7, 8),
            origin = CourseOrigin.SchoolImport(
                scope = com.example.mobileschedule.data.model.SourceScope("school", "1", "2026-1"),
                batchId = "batch1",
                sourceEntryId = "c101",
            ),
        )
        val activeCourse2 = CourseArrangement(
            id = 102,
            semesterId = 1,
            name = "大学英语(III)",
            teacher = "张老师",
            location = "文科楼 205",
            dayOfWeek = 2,
            startSection = 3,
            endSection = 4,
            weeks = setOf(1, 2, 3, 4, 5),
            origin = CourseOrigin.Manual,
        )
        val activeCourse3 = CourseArrangement(
            id = 103,
            semesterId = 1,
            name = "大学物理实验",
            teacher = "李实验师",
            location = "物理馆 302",
            dayOfWeek = 3,
            startSection = 5,
            endSection = 7,
            weeks = setOf(1, 3, 5, 7),
            origin = CourseOrigin.Manual,
        )
        val activeCourse4 = CourseArrangement(
            id = 104,
            semesterId = 1,
            name = "程序设计基础与实验",
            teacher = "林副教授",
            location = "信息楼 机房4",
            dayOfWeek = 4,
            startSection = 1,
            endSection = 3,
            weeks = setOf(1, 2, 3, 4, 5, 6),
            origin = CourseOrigin.Manual,
        )
        val activeCourse5 = CourseArrangement(
            id = 105,
            semesterId = 1,
            name = "体育(网球)",
            teacher = "黄教练",
            location = "东区网球场",
            dayOfWeek = 5,
            startSection = 7,
            endSection = 8,
            weeks = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            origin = CourseOrigin.Manual,
        )
        val activeCourseWeekend = CourseArrangement(
            id = 106,
            semesterId = 1,
            name = "大学生创新创业实践",
            teacher = "周老师",
            location = "创新创业园 A102",
            dayOfWeek = 6,
            startSection = 3,
            endSection = 4,
            weeks = setOf(1, 2, 3, 4),
            origin = CourseOrigin.Manual,
        )
        val nonCurrentCourse = CourseArrangement(
            id = 107,
            semesterId = 1,
            name = "毛泽东思想和中国特色社会主义理论体系概论",
            teacher = "赵教授",
            location = "马克思主义学院 阶梯一",
            dayOfWeek = 2,
            startSection = 7,
            endSection = 8,
            weeks = setOf(3, 4, 5, 6, 7, 8), // 第 1 周不上，第 3~8 周上（非本周）
            origin = CourseOrigin.Manual,
        )

        val times = ScheduleRules.defaultSectionTimes
        val config = SemesterConfig(monday, 20, 12, times, 1)
        val semester = Semester(1, "2026-2027学年 第一学期", emptySet(), config)

        val weekSchedule = WeekSchedule(
            semesterId = 1,
            week = 1,
            monday = monday,
            sunday = monday.plusDays(6),
            arrangements = listOf(activeCourse1, activeCourse2, activeCourse3, activeCourse4, activeCourse5, activeCourseWeekend),
            allArrangements = listOf(activeCourse1, activeCourse2, activeCourse3, activeCourse4, activeCourse5, activeCourseWeekend, nonCurrentCourse),
        )

        return ActiveWeek(semester, weekSchedule)
    }
}
