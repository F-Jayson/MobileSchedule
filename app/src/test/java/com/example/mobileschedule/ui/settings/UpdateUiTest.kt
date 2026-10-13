package com.example.mobileschedule.ui.settings

import org.junit.Assert.assertEquals
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
}
