package com.example.mobileschedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.model.WidgetCourseItem
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.data.preferences.WidgetClickAction
import com.example.mobileschedule.data.preferences.WidgetCornerRadius
import com.example.mobileschedule.data.preferences.WidgetCustomizationOptions
import com.example.mobileschedule.data.preferences.WidgetSettingsStore
import com.example.mobileschedule.data.preferences.WidgetTextColorMode
import com.example.mobileschedule.ui.widget.ScheduleWidgetUpdater
import com.example.mobileschedule.ui.widget.WidgetSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WidgetSettingsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun widgetSettings_storePersistence_savesAndRestoresOptions() {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefName = "widget-test-${UUID.randomUUID()}"
        try {
            val store = WidgetSettingsStore(testContext, prefName)
            val initial = store.read()
            assertTrue(initial.isFollowTheme)
            assertTrue(initial.showAllCourses)
            assertEquals(1.0f, initial.opacity, 0.001f)
            assertEquals(2, initial.maxCourseRows)

            val custom = WidgetCustomizationOptions(
                isFollowTheme = false,
                customColorHex = "#1E293B",
                opacity = 0.75f,
                textColorMode = WidgetTextColorMode.LIGHT,
                cornerRadius = WidgetCornerRadius.LARGE,
                showLocation = false,
                showTeacher = true,
                showLunar = true,
                showTomorrowPreview = false,
                showAllCourses = false,
                maxCourseRows = 3,
                clickAction = WidgetClickAction.OPEN_TODAY,
            )

            assertTrue(store.save(custom))

            val reopened = WidgetSettingsStore(testContext, prefName)
            val loaded = reopened.read()
            assertFalse(loaded.isFollowTheme)
            assertEquals("#1E293B", loaded.customColorHex)
            assertEquals(0.75f, loaded.opacity, 0.001f)
            assertEquals(WidgetTextColorMode.LIGHT, loaded.textColorMode)
            assertEquals(WidgetCornerRadius.LARGE, loaded.cornerRadius)
            assertFalse(loaded.showLocation)
            assertTrue(loaded.showTeacher)
            assertTrue(loaded.showLunar)
            assertFalse(loaded.showTomorrowPreview)
            assertFalse(loaded.showAllCourses)
            assertEquals(3, loaded.maxCourseRows)
            assertEquals(WidgetClickAction.OPEN_TODAY, loaded.clickAction)
        } finally {
            testContext.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun widgetSettings_uiInteraction_updatesOptionsAndRendersPreview() {
        var latestOptions = WidgetCustomizationOptions()
        var savedCalled = false

        compose.setContent {
            MaterialTheme {
                var options by remember { mutableStateOf(WidgetCustomizationOptions()) }
                WidgetSettingsScreen(
                    options = options,
                    onOptionsChange = {
                        options = it
                        latestOptions = it
                    },
                    onSave = { savedCalled = true },
                    onReset = { options = WidgetCustomizationOptions() },
                    onBack = {},
                )
            }
        }

        // Preview is visible
        compose.onNodeWithTag("widget_settings_screen").assertIsDisplayed()
        compose.onNodeWithTag("widget_preview_card").assertIsDisplayed()

        // Toggle follow theme
        compose.onNodeWithTag("widget_toggle_follow_theme").performClick()
        assertFalse(latestOptions.isFollowTheme)

        // Select preset color
        compose.onNodeWithTag("widget_preset_#2563EB").performClick()
        assertEquals("#2563EB", latestOptions.customColorHex)

        // Select corner radius
        compose.onNodeWithTag("widget_settings_list").performScrollToNode(hasTestTag("widget_corner_LARGE"))
        compose.onNodeWithTag("widget_corner_LARGE").performClick()
        assertEquals(WidgetCornerRadius.LARGE, latestOptions.cornerRadius)

        // Toggle showAllCourses off to reveal maxCourseRows selector
        compose.onNodeWithTag("widget_settings_list").performScrollToNode(hasTestTag("widget_toggle_show_all_courses"))
        compose.onNodeWithTag("widget_toggle_show_all_courses").performClick()
        assertFalse(latestOptions.showAllCourses)

        // Select 3 course rows
        compose.onNodeWithTag("widget_settings_list").performScrollToNode(hasTestTag("widget_rows_3"))
        compose.onNodeWithTag("widget_rows_3").performClick()
        assertEquals(3, latestOptions.maxCourseRows)

        // Select click action today
        compose.onNodeWithTag("widget_settings_list").performScrollToNode(hasTestTag("widget_action_today"))
        compose.onNodeWithTag("widget_action_today").performClick()
        assertEquals(WidgetClickAction.OPEN_TODAY, latestOptions.clickAction)

        // Save
        compose.onNodeWithTag("widget_settings_list").performScrollToNode(hasTestTag("widget_btn_save"))
        compose.onNodeWithTag("widget_btn_save").performClick()
        assertTrue(savedCalled)

        saveSyntheticScreenshot(
            "widget-settings-customized.png",
            compose.onNodeWithTag("widget_settings_screen").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun widgetRender_customOptions_renders3RowsAndDynamicStyles() {
        val c1 = WidgetCourseItem(101L, "高等数学", "第1-2节", "08:00 - 09:35", "知明 1-101", "李老师", isOngoing = false, isTomorrow = false)
        val c2 = WidgetCourseItem(102L, "大学物理", "第3-4节", "10:00 - 11:35", "知明 2-203", "王老师", isOngoing = false, isTomorrow = false)
        val c3 = WidgetCourseItem(103L, "软件工程导论", "第5-6节", "14:00 - 15:35", "笃行 3-301", "张老师", isOngoing = false, isTomorrow = false)

        val data = WidgetScheduleData.TodayCourses(
            dateText = "10月15日 周四",
            weekText = "第 7 周",
            subtitle = "今日剩余 3 节课",
            courses = listOf(c1, c2, c3),
            totalRemainingCount = 3,
        )

        val customOptions = WidgetCustomizationOptions(
            isFollowTheme = false,
            customColorHex = "#1E293B",
            opacity = 0.85f,
            textColorMode = WidgetTextColorMode.LIGHT,
            cornerRadius = WidgetCornerRadius.LARGE,
            showLocation = true,
            showTeacher = true,
            showAllCourses = false,
            maxCourseRows = 3,
            clickAction = WidgetClickAction.OPEN_TODAY,
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews(context, data, customOptions)
        assertNotNull(views)

        val container = FrameLayout(context)
        val inflated = views.apply(context, container)
        assertNotNull(inflated)

        // Row 3 should be visible because maxCourseRows is 3
        val row3 = inflated.findViewById<View>(R.id.widget_course_row_3)
        assertNotNull(row3)
        assertEquals(View.VISIBLE, row3.visibility)

        val row3Title = inflated.findViewById<TextView>(R.id.widget_course_title_3)
        assertEquals("软件工程导论", row3Title.text.toString())

        val row3Info = inflated.findViewById<TextView>(R.id.widget_course_info_3)
        assertTrue(row3Info.text.toString().contains("张老师"))
        assertTrue(row3Info.text.toString().contains("笃行 3-301"))

        saveWidgetSnapshot(inflated, "widget-schedule-3rows-custom.png")
    }

    private fun saveWidgetSnapshot(view: View, filename: String) {
        val width = 720
        val height = 400
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        saveSyntheticScreenshot(filename, bitmap)
    }
}
