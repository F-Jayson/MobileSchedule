package com.example.mobileschedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        compose.onNodeWithTag("tab_schedule").performClick()
        compose.onNodeWithTag("schedule_empty").assertIsDisplayed()
    }
}
