package com.example.mobileschedule

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshInstallReadsEmptyDatabaseAndCanNavigateBetweenTabs() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("schedule_empty").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("schedule_empty").assertIsDisplayed()
        compose.onNodeWithTag("tab_schedule").assertIsSelected()
        compose.onNodeWithTag("tab_today").performClick()
        compose.onNodeWithTag("today_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_today").assertIsSelected()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("settings_create").performClick()
        compose.onNodeWithTag("config_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertDoesNotExist()
        saveScreenshot("settings-app-entry.png")
        compose.onNodeWithTag("config_back").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_schedule").performClick()
        compose.onNodeWithTag("schedule_empty").assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileScheduleTest")
        }) ?: error("Cannot create screenshot")
        resolver.openOutputStream(uri)?.use { output ->
            (instrumentation.uiAutomation.takeScreenshot() ?: error("Cannot capture screen"))
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Cannot write screenshot")
    }
}
