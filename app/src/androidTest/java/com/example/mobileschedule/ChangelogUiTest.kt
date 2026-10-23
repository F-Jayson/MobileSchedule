package com.example.mobileschedule

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.AppUpdateInfo
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.preferences.AppThemeMode
import com.example.mobileschedule.ui.about.*
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ChangelogUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun testChangelogScreen_rendersBundledHistoryAndCurrentBadge() {
        var backClicked = false
        var refreshClicked = false

        val state = ChangelogUiState(
            entries = ChangelogRepository.bundledChangelogs,
            isChecking = false,
            currentVersion = "1.5.1",
        )

        compose.setContent {
            MobileScheduleTheme {
                ChangelogScreen(
                    uiState = state,
                    onBack = { backClicked = true },
                    onRefresh = { refreshClicked = true },
                    onStartUpdate = {},
                )
            }
        }

        // 1. Verify Page & Top Bar
        compose.onNodeWithTag("changelog_page").assertIsDisplayed()
        compose.onNodeWithText("更新日志").assertIsDisplayed()

        // 2. Verify Bundled Versions
        compose.onNodeWithTag("changelog_card_1.5.1").assertIsDisplayed()
        compose.onNodeWithText("v1.5.1").assertIsDisplayed()
        compose.onNodeWithText("课表背景壁纸功能全链路修复与体验优化").assertIsDisplayed()

        // 3. Verify Current Version Badge on v1.5.1
        compose.onNodeWithTag("changelog_badge_current", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("当前版本", useUnmergedTree = true).assertIsDisplayed()

        // 4. Verify No "New Version" Badge initially
        compose.onNodeWithTag("changelog_badge_new", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("changelog_update_button", useUnmergedTree = true).assertDoesNotExist()

        // 5. Verify Historical Versions exist in list
        compose.onNodeWithTag("changelog_card_1.5.0").assertExists()
        compose.onNodeWithTag("changelog_card_1.2.0").assertExists()
        compose.onNodeWithTag("changelog_card_1.1.0").assertExists()
        compose.onNodeWithTag("changelog_card_1.0.0").assertExists()

        // Save evidence screenshot
        saveSyntheticScreenshot(
            "changelog-bundled-history.png",
            compose.onNodeWithTag("changelog_page").captureToImage().asAndroidBitmap(),
        )

        // 6. Test Back and Refresh actions
        compose.onNodeWithTag("changelog_back").performClick()
        assertTrue("Back callback should be triggered", backClicked)

        compose.onNodeWithTag("changelog_refresh").performClick()
        assertTrue("Refresh callback should be triggered", refreshClicked)
    }

    @Test
    fun testChangelogScreen_displaysRemoteNewVersion_showsBadgeAndTriggerUpdate() {
        val dummyRemoteInfo = AppUpdateInfo(
            currentVersionName = "1.5.1",
            latestVersionName = "1.6.0",
            releaseTitle = "v1.6.0 课表升级与日程云同步",
            releaseNotes = """
                • 新增日历双向同步服务
                • 优化今日课表桌面组件动效
                • 提升教务离散周次解析速度
            """.trimIndent(),
            downloadUrl = "https://example.com/mobileschedule-v1.6.0.apk",
            browserUrl = "https://example.com/releases/v1.6.0",
            hasUpdate = true,
            apkSize = 16500000L,
            publishedAt = "2026-10-20",
            targetAbi = "x86_64",
        )

        val mergedEntries = ChangelogRepository.mergeWithRemoteUpdate(
            remoteInfo = dummyRemoteInfo,
            currentVersion = "1.5.1",
        )

        val state = ChangelogUiState(
            entries = mergedEntries,
            isChecking = false,
            currentVersion = "1.5.1",
        )

        var triggeredUpdateInfo: AppUpdateInfo? = null

        compose.setContent {
            MobileScheduleTheme {
                ChangelogScreen(
                    uiState = state,
                    onBack = {},
                    onRefresh = {},
                    onStartUpdate = { triggeredUpdateInfo = it },
                )
            }
        }

        // 1. Verify New Version Card
        compose.onNodeWithTag("changelog_card_1.6.0").assertIsDisplayed()
        compose.onNodeWithText("v1.6.0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("v1.6.0 课表升级与日程云同步", useUnmergedTree = true).assertIsDisplayed()

        // 2. Verify "新版本" Badge
        compose.onNodeWithTag("changelog_badge_new", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("新版本", useUnmergedTree = true).assertIsDisplayed()

        // 3. Verify Highlights
        compose.onNodeWithText("新增日历双向同步服务", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("优化今日课表桌面组件动效", useUnmergedTree = true).assertIsDisplayed()

        // 4. Verify Update Button is present on the new version
        compose.onNodeWithTag("changelog_update_button", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("立即更新至 v1.6.0", useUnmergedTree = true).assertIsDisplayed()

        // Save evidence screenshot
        saveSyntheticScreenshot(
            "changelog-new-version-available.png",
            compose.onNodeWithTag("changelog_page").captureToImage().asAndroidBitmap(),
        )

        // 5. Click Update Button and verify callback is called with info
        compose.onNodeWithTag("changelog_update_button", useUnmergedTree = true).performClick()
        assertEquals(dummyRemoteInfo, triggeredUpdateInfo)
    }

    @Test
    fun testChangelogScreen_clickHistoricalCardTogglesFullNotes() {
        val state = ChangelogUiState(
            entries = ChangelogRepository.bundledChangelogs,
            isChecking = false,
            currentVersion = "1.5.1",
        )

        compose.setContent {
            MobileScheduleTheme {
                ChangelogScreen(
                    uiState = state,
                    onBack = {},
                    onRefresh = {},
                    onStartUpdate = {},
                )
            }
        }

        // Initially v1.0.0 is collapsed (since it's not remote new and not current)
        compose.onNodeWithTag("changelog_card_1.0.0").performScrollTo()
        compose.onNodeWithTag("changelog_card_1.0.0").assertIsDisplayed()
        compose.onNodeWithTag("changelog_full_notes_1.0.0", useUnmergedTree = true).assertDoesNotExist()

        // Click to expand v1.0.0
        compose.onNodeWithTag("changelog_card_1.0.0").performClick()
        compose.waitForIdle()

        // Now full notes of v1.0.0 are visible (scroll to it if pushed beyond viewport)
        compose.onNodeWithTag("changelog_full_notes_1.0.0", useUnmergedTree = true).performScrollTo()
        compose.onNodeWithTag("changelog_full_notes_1.0.0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("changelog_full_notes_1.0.0", useUnmergedTree = true).assertTextContains("正方教务系统适配", substring = true)

        // Historical card must NOT have update button
        compose.onNodeWithTag("changelog_update_button_1.0.0", useUnmergedTree = true).assertDoesNotExist()

        // Save screenshot of expanded historical card
        saveSyntheticScreenshot(
            "changelog-historical-expanded.png",
            compose.onNodeWithTag("changelog_page").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun testAboutScreen_containsChangelogEntryAndNavigates() {
        var changelogClicked = false

        compose.setContent {
            MobileScheduleTheme {
                AboutScreen(
                    onBack = {},
                    onOpenLicenses = {},
                    onOpenChangelog = { changelogClicked = true },
                )
            }
        }

        compose.onNodeWithTag("about_changelog_entry").assertIsDisplayed()
        compose.onNodeWithText("更新日志（Changelog）").assertIsDisplayed()
        compose.onNodeWithText("查看应用版本迭代历史与最新功能特性").assertIsDisplayed()

        // Click changelog entry
        compose.onNodeWithTag("about_changelog_entry").performClick()
        assertTrue("Clicking changelog entry in AboutScreen should trigger callback", changelogClicked)
    }

    @Test
    fun testSettingsScreen_containsChangelogEntryAndNavigates() {
        var changelogClicked = false
        val monday = LocalDate.of(2026, 3, 2)
        val dummySemester = Semester(
            id = 1L,
            displayName = "2025-2026学年第二学期",
            sourceBindings = emptySet(),
            config = SemesterConfig(
                firstWeekMonday = monday,
                totalWeeks = 18,
                totalSections = 8,
                sectionTimes = emptyList(),
                revision = 1L,
            ),
        )

        compose.setContent {
            MobileScheduleTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(
                        semesters = listOf(dummySemester),
                        activeId = 1L,
                    ),
                    themeMode = AppThemeMode.SYSTEM,
                    onManageSchedule = {},
                    onDisplaySettings = {},
                    onAbout = {},
                    onChangelog = { changelogClicked = true },
                    onThemeModeChange = {},
                    onSelectMirror = {},
                    onCheckUpdate = {},
                )
            }
        }

        compose.onNodeWithTag("settings_changelog_entry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("更新日志").assertIsDisplayed()
        compose.onNodeWithText("版本演进历史与新特性说明").assertIsDisplayed()

        // Click changelog entry in settings
        compose.onNodeWithTag("settings_changelog_entry").performClick()
        assertTrue("Clicking changelog entry in SettingsScreen should trigger callback", changelogClicked)
    }
}
