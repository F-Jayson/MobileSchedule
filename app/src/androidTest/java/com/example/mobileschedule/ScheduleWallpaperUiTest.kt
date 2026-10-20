package com.example.mobileschedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsScreen
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsStore
import com.example.mobileschedule.ui.schedule.WeekScheduleGrid
import com.example.mobileschedule.ui.schedule.wallpaper.ScheduleWallpaperManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScheduleWallpaperUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testWallpaperStorageAndLuminanceDetection() {
        val testName = "wallpaper-store-test-${UUID.randomUUID()}"
        val store = ScheduleDisplaySettingsStore(context, testName)

        try {
            // 1. Initial state: no wallpaper
            val initial = store.read()
            assertEquals(null, initial.wallpaperPath)
            assertEquals(0.85f, initial.cardOpacity, 0.01f)
            assertFalse(initial.isWallpaperDark)

            // 2. Generate a dark test wallpaper bitmap (deep night blue)
            val darkBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(15, 23, 42)) // Slate 900
            }
            val darkMeta = ScheduleWallpaperManager.saveWallpaperBitmap(context, darkBmp)
            assertTrue("Expected dark bitmap to be detected as dark", darkMeta.isDark)
            assertTrue(File(darkMeta.filePath).exists())

            // 3. Save into store
            val updated = initial.copy(
                wallpaperPath = darkMeta.filePath,
                cardOpacity = 0.60f,
                isWallpaperDark = darkMeta.isDark,
            )
            assertTrue(store.save(updated))

            val reopened = ScheduleDisplaySettingsStore(context, testName).read()
            assertEquals(darkMeta.filePath, reopened.wallpaperPath)
            assertEquals(0.60f, reopened.cardOpacity, 0.01f)
            assertTrue(reopened.isWallpaperDark)

            // 4. Generate a light test wallpaper bitmap (sunny light yellow)
            val lightBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(254, 240, 138)) // Yellow 200
            }
            val lightMeta = ScheduleWallpaperManager.saveWallpaperBitmap(context, lightBmp)
            assertFalse("Expected light bitmap to be detected as light", lightMeta.isDark)

            // 5. Clear wallpaper
            assertTrue(ScheduleWallpaperManager.clearWallpaper(context))
            assertFalse(ScheduleWallpaperManager.hasWallpaper(context))
        } finally {
            ScheduleWallpaperManager.clearWallpaper(context)
            context.getSharedPreferences(testName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun testDisplaySettingsScreen_wallpaperAndOpacityControls() {
        compose.setContent {
            MaterialTheme {
                var options by remember {
                    mutableStateOf(
                        ScheduleDisplayOptions(
                            wallpaperPath = null,
                            cardOpacity = 0.75f,
                            isWallpaperDark = false,
                        )
                    )
                }
                ScheduleDisplaySettingsScreen(
                    options = options,
                    onOptionsChange = { options = it },
                    onBack = {},
                )
            }
        }

        // Verify wallpaper section and opacity slider are displayed
        compose.onNodeWithTag("wallpaper_settings_section").assertIsDisplayed()
        compose.onNodeWithTag("display_pick_wallpaper_button").assertIsDisplayed()
        compose.onNodeWithTag("display_card_opacity_slider").assertIsDisplayed()
        compose.onNodeWithTag("display_card_opacity_text").assertIsDisplayed()

        saveSyntheticScreenshot(
            "schedule-display-settings-wallpaper.png",
            compose.onNodeWithTag("display_settings_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun testWeekGridWithDarkWallpaper_rendersWallpaperAndFrostedCards() {
        val week = createFixture()

        // Create a stylized dark wallpaper with subtle gradient
        val wallpaperBitmap = Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(wallpaperBitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.rgb(15, 23, 42)
        }
        canvas.drawRect(0f, 0f, 480f, 800f, paint)

        // Accent shapes
        paint.color = android.graphics.Color.rgb(30, 41, 59)
        canvas.drawCircle(240f, 200f, 180f, paint)
        paint.color = android.graphics.Color.rgb(51, 65, 85)
        canvas.drawCircle(360f, 600f, 220f, paint)

        val meta = ScheduleWallpaperManager.saveWallpaperBitmap(context, wallpaperBitmap)

        try {
            compose.setContent {
                MaterialTheme {
                    WeekScheduleGrid(
                        week = week,
                        displayOptions = ScheduleDisplayOptions(
                            showWeekend = true,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            wallpaperPath = meta.filePath,
                            cardOpacity = 0.55f,
                            isWallpaperDark = true,
                        ),
                    )
                }
            }

            // Verify wallpaper layer and course cards render
            compose.onNodeWithTag("schedule_wallpaper_image").assertIsDisplayed()
            compose.onNodeWithTag("course_1").assertIsDisplayed()
            compose.onNodeWithTag("course_2").assertIsDisplayed()

            saveSyntheticScreenshot(
                "schedule-wallpaper-dark-frosted-cards.png",
                compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
            )
        } finally {
            ScheduleWallpaperManager.clearWallpaper(context)
        }
    }

    @Test
    fun testWeekGridWithLightWallpaper_rendersWallpaperAndHighContrastCards() {
        val week = createFixture()

        // Create a stylized light wallpaper
        val wallpaperBitmap = Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(wallpaperBitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.rgb(241, 245, 249) // Slate 100
        }
        canvas.drawRect(0f, 0f, 480f, 800f, paint)

        // Light pastel accents
        paint.color = android.graphics.Color.rgb(226, 232, 240)
        canvas.drawCircle(120f, 300f, 200f, paint)
        paint.color = android.graphics.Color.rgb(254, 215, 170) // Soft orange
        canvas.drawCircle(380f, 500f, 160f, paint)

        val meta = ScheduleWallpaperManager.saveWallpaperBitmap(context, wallpaperBitmap)

        try {
            compose.setContent {
                MaterialTheme {
                    WeekScheduleGrid(
                        week = week,
                        displayOptions = ScheduleDisplayOptions(
                            showWeekend = true,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            wallpaperPath = meta.filePath,
                            cardOpacity = 0.60f,
                            isWallpaperDark = false,
                        ),
                    )
                }
            }

            compose.onNodeWithTag("schedule_wallpaper_image").assertIsDisplayed()
            compose.onNodeWithTag("course_1").assertIsDisplayed()
            compose.onNodeWithTag("course_2").assertIsDisplayed()

            saveSyntheticScreenshot(
                "schedule-wallpaper-light-frosted-cards.png",
                compose.onNodeWithTag("week_grid").captureToImage().asAndroidBitmap(),
            )
        } finally {
            ScheduleWallpaperManager.clearWallpaper(context)
        }
    }

    private fun createFixture(): ActiveWeek {
        val monday = LocalDate.of(2026, 3, 2)
        val courses = listOf(
            CourseArrangement(
                id = 1L, semesterId = 1L, name = "算法与数据结构",
                teacher = "李教授", location = "理科楼 201",
                dayOfWeek = 1, startSection = 1, endSection = 2,
                weeks = setOf(1), origin = CourseOrigin.Manual,
            ),
            CourseArrangement(
                id = 2L, semesterId = 1L, name = "计算机网络原理",
                teacher = "张老师", location = "网络楼 405",
                dayOfWeek = 2, startSection = 3, endSection = 4,
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
