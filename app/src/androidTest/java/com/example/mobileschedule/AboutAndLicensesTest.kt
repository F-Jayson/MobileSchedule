package com.example.mobileschedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.preferences.AppThemeMode
import com.example.mobileschedule.ui.about.AboutScreen
import com.example.mobileschedule.ui.about.LicensesScreen
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AboutAndLicensesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun testAboutScreen_rendersBasicInformationAndEnvironment() {
        var backClicked = false
        var licensesClicked = false

        compose.setContent {
            MobileScheduleTheme {
                AboutScreen(
                    onBack = { backClicked = true },
                    onOpenLicenses = { licensesClicked = true },
                )
            }
        }

        // 1. Verify Page & App Brand Header
        compose.onNodeWithTag("about_page").assertIsDisplayed()
        compose.onNodeWithText("福师大课程表").assertIsDisplayed()
        compose.onNodeWithText("MobileSchedule for FNUBBS / FJNU").assertIsDisplayed()

        // 2. Verify Version Tag
        compose.onNodeWithTag("about_version").assertIsDisplayed()
        compose.onNodeWithTag("about_version").assertTextContains("版本 v", substring = true)
        compose.onNodeWithTag("about_version").assertTextContains("构建号", substring = true)

        // 3. Verify Environment Information Card
        compose.onNodeWithTag("about_environment_info").assertIsDisplayed()
        compose.onNodeWithText("当前运行环境").assertIsDisplayed()
        compose.onNodeWithTag("env_android_version").assertIsDisplayed()
        compose.onNodeWithTag("env_device_model").assertIsDisplayed()
        compose.onNodeWithTag("env_abi").assertIsDisplayed()

        // 4. Verify Action Entries
        compose.onNodeWithTag("about_licenses_entry").assertIsDisplayed()
        compose.onNodeWithText("开源许可证（Open Source Licenses）").assertIsDisplayed()
        compose.onNodeWithTag("about_privacy_entry").assertIsDisplayed()
        compose.onNodeWithText("免责声明与隐私保护").assertIsDisplayed()

        // Save evidence screenshot
        saveSyntheticScreenshot("about-page.png", compose.onNodeWithTag("about_page").captureToImage().asAndroidBitmap())

        // 5. Test Navigation Callbacks
        compose.onNodeWithTag("about_back").performClick()
        assertTrue("Back button callback should be triggered", backClicked)

        compose.onNodeWithTag("about_licenses_entry").performClick()
        assertTrue("Licenses entry callback should be triggered", licensesClicked)
    }

    @Test
    fun testPrivacyDisclaimerDialog_opensAndCloses() {
        compose.setContent {
            MobileScheduleTheme {
                AboutScreen(
                    onBack = {},
                    onOpenLicenses = {},
                )
            }
        }

        // Initially dialog is not present
        compose.onNodeWithTag("privacy_dialog").assertDoesNotExist()

        // Click to open privacy dialog
        compose.onNodeWithTag("about_privacy_entry").performClick()

        // Verify dialog is displayed
        compose.onNodeWithTag("privacy_dialog").assertIsDisplayed()
        compose.onNodeWithText("1. 纯本地离线存储").assertIsDisplayed()
        compose.onNodeWithText("2. 账号与登录凭证安全").assertIsDisplayed()
        compose.onNodeWithText("3. 无广告与无追踪").assertIsDisplayed()
        compose.onNodeWithText("4. 非官方开源产品免责").assertIsDisplayed()

        // Save screenshot of privacy dialog
        saveSyntheticScreenshot("about-privacy-dialog.png", compose.onNodeWithTag("privacy_dialog").captureToImage().asAndroidBitmap())

        // Click dismiss button
        compose.onNodeWithTag("privacy_dialog_confirm").performClick()

        // Verify dialog closed
        compose.onNodeWithTag("privacy_dialog").assertDoesNotExist()
    }

    @Test
    fun testLicensesScreen_displaysListFiltersAndShowsLicenseContent() {
        var backClicked = false

        compose.setContent {
            MobileScheduleTheme {
                LicensesScreen(onBack = { backClicked = true })
            }
        }

        // 1. Verify Page & Components
        compose.onNodeWithTag("licenses_page").assertIsDisplayed()
        compose.onNodeWithTag("licenses_search_bar").assertIsDisplayed()
        compose.onNodeWithTag("licenses_list").assertIsDisplayed()

        // Verify some core libraries exist in list
        compose.onNodeWithTag("license_item_room").assertIsDisplayed()
        compose.onNodeWithText("AndroidX Room").assertIsDisplayed()
        compose.onNodeWithText("Google LLC / The Android Open Source Project · 2.8.4").assertIsDisplayed()

        // Save licenses list screenshot
        saveSyntheticScreenshot("licenses-page.png", compose.onNodeWithTag("licenses_page").captureToImage().asAndroidBitmap())

        // 2. Test Search Filtering
        compose.onNodeWithTag("licenses_search_bar").performTextInput("Room")
        compose.onNodeWithTag("license_item_room").assertIsDisplayed()
        compose.onNodeWithTag("license_item_kotlinx-coroutines").assertDoesNotExist()

        // 3. Test Detail Dialog
        compose.onNodeWithTag("license_item_room").performClick()
        compose.onNodeWithTag("license_dialog").assertIsDisplayed()
        compose.onNodeWithTag("license_content_text").assertIsDisplayed()
        compose.onNodeWithTag("license_content_text").assertTextContains("Apache License", substring = true)

        // Save license detail dialog screenshot
        saveSyntheticScreenshot("license-detail-dialog.png", compose.onNodeWithTag("license_dialog").captureToImage().asAndroidBitmap())

        // Close dialog
        compose.onNodeWithTag("license_dialog_close").performClick()
        compose.onNodeWithTag("license_dialog").assertDoesNotExist()

        // 4. Test Search Clear
        compose.onNodeWithTag("licenses_search_clear").performClick()
        compose.onNodeWithTag("license_item_room").assertIsDisplayed()
        compose.onNodeWithTag("license_item_jetpack-compose").assertExists()

        // 5. Test Back
        compose.onNodeWithTag("licenses_back").performClick()
        assertTrue("Back button in licenses screen should be triggered", backClicked)
    }

    @Test
    fun testSettingsScreen_containsAboutEntryAndTriggersCallback() {
        var aboutClicked = false
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
                    onAbout = { aboutClicked = true },
                    onThemeModeChange = {},
                    onSelectMirror = {},
                    onCheckUpdate = {},
                )
            }
        }

        // Scroll to About entry in Settings page if needed
        compose.onNodeWithTag("settings_page").performScrollToNode(hasTestTag("settings_about_entry"))
        compose.onNodeWithTag("settings_about_entry").assertIsDisplayed()
        compose.onNodeWithText("关于与帮助").assertIsDisplayed()
        compose.onNodeWithText("版本、环境信息、开源许可与隐私说明").assertIsDisplayed()

        // Save settings page screenshot
        saveSyntheticScreenshot("settings-about-entry.png", compose.onNodeWithTag("settings_page").captureToImage().asAndroidBitmap())

        // Click entry
        compose.onNodeWithTag("settings_about_entry").performClick()
        assertTrue("About entry click must trigger onAbout callback", aboutClicked)
    }
}
