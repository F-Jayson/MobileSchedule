package com.example.mobileschedule.ui.schedule.wallpaper

import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ScheduleWallpaperFlowTest {

    @Test
    fun calculateSampleSize_downsamplesOversizedImagesToPreventOom() {
        // 1. Regular images within 2560 max dimension require no downsampling (sampleSize = 1)
        assertEquals(1, ScheduleWallpaperManager.calculateSampleSize(1080, 2400))
        assertEquals(1, ScheduleWallpaperManager.calculateSampleSize(2560, 1440))
        assertEquals(1, ScheduleWallpaperManager.calculateSampleSize(1200, 1600))

        // 2. High-resolution phone camera photos (e.g. 12MP: 4000x3000) downsampled to 2000x1500 (sampleSize = 2)
        assertEquals(2, ScheduleWallpaperManager.calculateSampleSize(4000, 3000))
        assertEquals(2, ScheduleWallpaperManager.calculateSampleSize(3000, 4000))

        // 3. Ultra-high resolution photos (e.g. 48MP/50MP: 8000x6000) downsampled by 4 (sampleSize = 4)
        assertEquals(4, ScheduleWallpaperManager.calculateSampleSize(8000, 6000))
        assertEquals(4, ScheduleWallpaperManager.calculateSampleSize(6000, 8000))

        // 4. Edge cases
        assertEquals(1, ScheduleWallpaperManager.calculateSampleSize(0, 0))
        assertEquals(1, ScheduleWallpaperManager.calculateSampleSize(-100, 2000))
    }

    @Test
    fun scheduleDisplayOptions_equalityDifferentiatesWallpaperVersion() {
        val initial = ScheduleDisplayOptions(
            wallpaperPath = "/data/user/0/app/files/wallpaper/schedule_bg.png",
            cardOpacity = 0.85f,
            isWallpaperDark = false,
            wallpaperUpdatedAt = 1000L,
        )

        // Same path and visual properties, but different timestamp (user replaced wallpaper with another image)
        val replaced = initial.copy(
            wallpaperUpdatedAt = 2000L,
        )

        // Must NOT be equal, ensuring Compose mutableStateOf triggers recomposition
        assertNotEquals(
            "Expected options with different wallpaperUpdatedAt to be non-equal",
            initial,
            replaced,
        )
    }

    @Test
    fun scheduleDisplaySettingsStore_optionsFlowEmitsUpdates() {
        val testOptions = ScheduleDisplayOptions(
            showLocation = true,
            showTeacher = false,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
            wallpaperPath = "/data/user/0/app/files/wallpaper/schedule_bg.png",
            cardOpacity = 0.65f,
            isWallpaperDark = true,
            wallpaperUpdatedAt = 123456789L,
        )

        ScheduleDisplaySettingsStore.notifyOptionsChanged(testOptions)
        val emitted = ScheduleDisplaySettingsStore.optionsFlow.value

        assertEquals(testOptions, emitted)
        assertEquals(123456789L, emitted?.wallpaperUpdatedAt)
        assertEquals(0.65f, emitted?.cardOpacity ?: 0f, 0.001f)
        assertTrue(emitted?.isWallpaperDark == true)
    }
}
