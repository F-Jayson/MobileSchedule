package com.example.mobileschedule.ui.settings

import com.example.mobileschedule.data.model.AppUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUiTest {

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("0 B", formatFileSize(-10))
        assertEquals("500 B", formatFileSize(500))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("10.0 MB", formatFileSize(10 * 1024 * 1024))
        assertEquals("1.50 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testAppUpdateInfoForceUpdateDefaultAndExplicit() {
        val normalUpdate = AppUpdateInfo(
            currentVersionName = "1.0.0",
            latestVersionName = "1.1.0",
            releaseTitle = "v1.1.0",
            releaseNotes = "普通优化",
            downloadUrl = "https://example.com/app.apk",
            browserUrl = "https://example.com",
            hasUpdate = true,
        )
        assertFalse(normalUpdate.forceUpdate)

        val forceUpdate = normalUpdate.copy(forceUpdate = true)
        assertTrue(forceUpdate.forceUpdate)
    }
}
