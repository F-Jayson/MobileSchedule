package com.example.mobileschedule

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.notification.CapsuleNotificationData
import com.example.mobileschedule.data.notification.OngoingScheduleNotificationManager
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OngoingScheduleNotificationUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    private fun screenshot(name: String, tag: String) {
        compose.waitForIdle()
        saveSyntheticScreenshot(name, compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap())
    }

    @Test
    fun ongoingNotificationToggle_togglesStateInSettings() {
        var ongoingEnabled by mutableStateOf(false)

        compose.setContent {
            MobileScheduleTheme {
                SettingsHomeScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    ongoingNotificationEnabled = ongoingEnabled,
                    onOngoingNotificationEnabledChange = { ongoingEnabled = it },
                )
            }
        }

        // Initially disabled
        compose.onNodeWithTag("settings_ongoing_notification_enabled").assertIsDisplayed()
        screenshot("settings-ongoing-notification-disabled.png", "settings_page")

        // Click to toggle on
        compose.onNodeWithTag("settings_ongoing_notification_enabled").performClick()
        compose.waitForIdle()
        assertTrue(ongoingEnabled)
        screenshot("settings-ongoing-notification-enabled.png", "settings_page")
    }

    @Test
    fun ongoingNotificationManager_postAndCancelNotification_lifecycleIntegration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // Clean previous notifications if any
        OngoingScheduleNotificationManager.cancelNotification(context)

        val capsuleData = CapsuleNotificationData(
            shouldShow = true,
            title = "正在上课：高等数学 - 知明1-101",
            contentText = "下节课：大学英语 - 知明2-202",
            subText = "正在上课",
            targetCourseId = 101L,
        )

        // Post notification
        OngoingScheduleNotificationManager.updateNotification(context, capsuleData)

        // Verify active notification with polling to account for async IPC
        var capsuleNotification: Notification? = null
        var isOngoing = false
        val postDeadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < postDeadline) {
            val notification = notificationManager.activeNotifications.firstOrNull {
                it.id == OngoingScheduleNotificationManager.NOTIFICATION_ID
            }
            if (notification != null) {
                capsuleNotification = notification.notification
                isOngoing = (notification.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
                break
            }
            Thread.sleep(50)
        }
        assertNotNull("Ongoing notification should be present in status bar", capsuleNotification)
        assertEquals("正在上课：高等数学 - 知明1-101", capsuleNotification?.extras?.getString(Notification.EXTRA_TITLE))
        assertEquals("下节课：大学英语 - 知明2-202", capsuleNotification?.extras?.getString(Notification.EXTRA_TEXT))
        assertTrue("Notification must be ongoing", isOngoing)

        // Now test cancellation (e.g. all courses concluded)
        val dismissData = CapsuleNotificationData(
            shouldShow = false,
            title = "",
            contentText = "",
        )
        OngoingScheduleNotificationManager.updateNotification(context, dismissData)

        var dismissed = false
        val dismissDeadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < dismissDeadline) {
            val notification = notificationManager.activeNotifications.firstOrNull {
                it.id == OngoingScheduleNotificationManager.NOTIFICATION_ID
            }
            if (notification == null) {
                dismissed = true
                break
            }
            Thread.sleep(50)
        }
        assertTrue("Ongoing notification should be dismissed when shouldShow=false", dismissed)
    }

    @Test
    fun captureNotificationShadeScreenshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

        val capsuleData = CapsuleNotificationData(
            shouldShow = true,
            title = "正在上课：高等数学 - 知明1-101",
            contentText = "下节课：大学英语 - 知明2-202",
            subText = "正在上课",
            targetCourseId = 101L,
        )
        OngoingScheduleNotificationManager.updateNotification(context, capsuleData)

        try {
            uiAutomation.executeShellCommand("cmd statusbar expand-notifications")
            Thread.sleep(1500)
            val fullBitmap = uiAutomation.takeScreenshot()
            if (fullBitmap != null) {
                saveSyntheticScreenshot("notification-shade-ongoing-capsule.png", fullBitmap)
            }
        } finally {
            uiAutomation.executeShellCommand("cmd statusbar collapse")
            OngoingScheduleNotificationManager.cancelNotification(context)
        }
    }
}

