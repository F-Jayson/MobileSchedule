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
    fun availableTabsAndImportRoutesReturnToCaller() {
        compose.waitUntil(timeoutMillis = 30_000) {
            hasTag("schedule_empty") || hasTag("schedule_config_required") || hasTag("week_grid")
        }
        val firstRun = hasTag("schedule_empty")
        val initialTag = when {
            firstRun -> "schedule_empty"
            hasTag("schedule_config_required") -> "schedule_config_required"
            else -> "week_grid"
        }
        compose.onNodeWithTag(initialTag).assertIsDisplayed()
        compose.onNodeWithTag("tab_schedule").assertIsSelected()
        compose.onNodeWithTag("tab_today").assertDoesNotExist()
        if (firstRun) saveScreenshot("schedule-first-run.png")
        compose.onNodeWithTag(if (initialTag == "week_grid") "schedule_import_top" else "schedule_import")
            .performClick()
        compose.onNodeWithTag("import_intro").assertIsDisplayed()
        compose.onNodeWithTag("import_unavailable").assertIsDisplayed()
        compose.onNodeWithTag("tab_schedule").assertDoesNotExist()
        if (firstRun) saveScreenshot("import-intro.png")
        compose.onNodeWithTag("import_back").performClick()
        compose.onNodeWithTag(initialTag).assertIsDisplayed()
        if (firstRun) {
            compose.onNodeWithTag("schedule_configure").performClick()
            compose.onNodeWithTag("config_page").assertIsDisplayed()
            compose.onNodeWithTag("config_back").performClick()
            compose.onNodeWithTag("schedule_empty").assertIsDisplayed()
        }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.waitUntil(timeoutMillis = 30_000) { hasTag("settings_import") }
        compose.onNodeWithTag("settings_import").performClick()
        compose.onNodeWithTag("import_intro").assertIsDisplayed()
        compose.onNodeWithTag("import_back").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("settings_create").performClick()
        compose.onNodeWithTag("config_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertDoesNotExist()
        saveScreenshot("settings-app-entry.png")
        compose.onNodeWithTag("config_back").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_schedule").performClick()
        compose.onNodeWithTag(initialTag).assertIsDisplayed()
    }

    private fun hasTag(tag: String): Boolean =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

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
