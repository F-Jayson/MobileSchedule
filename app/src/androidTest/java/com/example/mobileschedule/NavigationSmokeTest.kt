package com.example.mobileschedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun compactVerticalNavigationKeepsLabelsWithinTheBarAndOpensSettings() {
        compose.waitUntil(timeoutMillis = 30_000) { hasTag("tab_schedule") }
        val bar = compose.onNodeWithTag("main_bottom_navigation").getUnclippedBoundsInRoot()
        listOf("课表", "设置").forEach { label ->
            val bounds = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("$label must fit vertically inside the navigation bar: $bounds / $bar",
                bounds.top >= bar.top && bounds.bottom <= bar.bottom)
            assertTrue("$label must sit below the navigation icon",
                bounds.top >= (bar.top + bar.bottom) / 2)
        }
        saveScreenshot("vertical-navigation-schedule.png")
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        saveScreenshot("vertical-navigation-settings.png")
    }

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
        if (hasTag("schedule_import")) {
            compose.onNodeWithTag("schedule_import").performClick()
        } else {
            compose.onNodeWithTag("tab_settings").performClick()
            compose.onNodeWithTag("settings_manage_schedule").performClick()
            compose.onNodeWithTag("settings_import").performClick()
        }
        compose.onNodeWithTag("import_intro").assertIsDisplayed()
        compose.onNodeWithTag("import_read_only").assertIsDisplayed()
        compose.onNodeWithTag("tab_schedule").assertDoesNotExist()
        if (firstRun) saveScreenshot("import-intro.png")
        compose.onNodeWithTag("import_back").performClick()
        if (initialTag == "week_grid" && !hasTag("schedule_import")) {
            compose.onNodeWithTag("schedule_management_page").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("tab_schedule").performClick()
        }
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
        compose.waitUntil(timeoutMillis = 30_000) { hasTag("settings_manage_schedule") }
        compose.onNodeWithTag("settings_page").performScrollToNode(hasTestTag("settings_display_settings"))
        compose.onNodeWithTag("settings_display_settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("display_settings_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertDoesNotExist()
        compose.onNodeWithTag("display_settings_back").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.onNodeWithTag("settings_manage_schedule").performClick()
        compose.onNodeWithTag("schedule_management_page").assertIsDisplayed()
        compose.onNodeWithTag("settings_import").performClick()
        compose.onNodeWithTag("import_intro").assertIsDisplayed()
        compose.onNodeWithTag("import_back").performClick()
        compose.onNodeWithTag("schedule_management_page").assertIsDisplayed()
        compose.onNodeWithTag("settings_create").performClick()
        compose.onNodeWithTag("config_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertDoesNotExist()
        saveScreenshot("settings-app-entry.png")
        compose.onNodeWithTag("config_back").performClick()
        compose.onNodeWithTag("schedule_management_page").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_schedule").performClick()
        compose.onNodeWithTag(initialTag).assertIsDisplayed()
    }

    private fun hasTag(tag: String): Boolean =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        saveSyntheticScreenshot(name, instrumentation.uiAutomation.takeScreenshot()
            ?: error("Cannot capture screen"))
    }
}
