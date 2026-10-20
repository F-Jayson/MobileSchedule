package com.example.mobileschedule.ui.schedule.wallpaper

import androidx.compose.ui.graphics.Color
import com.example.mobileschedule.ui.theme.CourseColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleContrastHelperTest {

    @Test
    fun calculateRelativeLuminance_blackAndWhiteBoundaries() {
        val blackLuminance = ScheduleContrastHelper.calculateRelativeLuminance(Color.Black)
        val whiteLuminance = ScheduleContrastHelper.calculateRelativeLuminance(Color.White)

        assertEquals(0f, blackLuminance, 0.001f)
        assertEquals(1f, whiteLuminance, 0.001f)
    }

    @Test
    fun calculateContrastRatio_standardWcagRatios() {
        val maxContrast = ScheduleContrastHelper.calculateContrastRatio(Color.Black, Color.White)
        assertEquals(21f, maxContrast, 0.01f)

        val minContrast = ScheduleContrastHelper.calculateContrastRatio(Color.Red, Color.Red)
        assertEquals(1f, minContrast, 0.001f)

        // Dark text on white background has high contrast
        val textOnWhite = ScheduleContrastHelper.calculateContrastRatio(Color(0xFF111827), Color.White)
        assertTrue("Expected contrast > 10:1 for dark text on white, got $textOnWhite", textOnWhite >= 10.0f)

        // White text on dark background has high contrast
        val textOnDark = ScheduleContrastHelper.calculateContrastRatio(Color.White, Color(0xFF18181B))
        assertTrue("Expected contrast > 10:1 for white text on dark bg, got $textOnDark", textOnDark >= 10.0f)
    }

    @Test
    fun blendColor_correctlyInterpolatesRgb() {
        val blendedHalf = ScheduleContrastHelper.blendColor(
            foreground = Color.White,
            background = Color.Black,
            foregroundAlpha = 0.5f,
        )
        assertEquals(0.5f, blendedHalf.red, 0.01f)
        assertEquals(0.5f, blendedHalf.green, 0.01f)
        assertEquals(0.5f, blendedHalf.blue, 0.01f)
        assertEquals(1f, blendedHalf.alpha, 0.01f)
    }

    @Test
    fun resolveCardTextColors_adaptsToWallpaperDarknessAndOpacity() {
        val basePalette = CourseColorPalette(
            container = Color(0xFFDBEAFE),      // Soft blue
            onContainer = Color(0xFF1E3A8A),    // Dark blue
            secondaryText = Color(0xFF3B82F6),
            accent = Color(0xFF2563EB),
        )

        // 1. Without wallpaper: returns original palette text colors
        val noWallpaperColors = ScheduleContrastHelper.resolveCardTextColors(
            basePalette = basePalette,
            cardOpacity = 0.5f,
            hasWallpaper = false,
            isWallpaperDark = false,
        )
        assertEquals(basePalette.onContainer, noWallpaperColors.title)
        assertEquals(basePalette.secondaryText, noWallpaperColors.secondary)

        // 2. Full opacity (>= 0.96f): preserves original theme palette
        val fullOpacityColors = ScheduleContrastHelper.resolveCardTextColors(
            basePalette = basePalette,
            cardOpacity = 1.0f,
            hasWallpaper = true,
            isWallpaperDark = true,
        )
        assertEquals(basePalette.onContainer, fullOpacityColors.title)
        assertEquals(basePalette.secondaryText, fullOpacityColors.secondary)

        // 3. Low opacity (0.35f) on dark wallpaper: blended container is dark -> returns crisp white text
        val darkWallpaperColors = ScheduleContrastHelper.resolveCardTextColors(
            basePalette = basePalette,
            cardOpacity = 0.35f,
            hasWallpaper = true,
            isWallpaperDark = true,
        )
        assertEquals(1f, darkWallpaperColors.title.red, 0.05f)
        assertEquals(1f, darkWallpaperColors.title.green, 0.05f)
        assertEquals(1f, darkWallpaperColors.title.blue, 0.05f)

        // 4. Low opacity (0.35f) on light wallpaper: blended container is light -> returns crisp dark text
        val lightWallpaperColors = ScheduleContrastHelper.resolveCardTextColors(
            basePalette = basePalette,
            cardOpacity = 0.35f,
            hasWallpaper = true,
            isWallpaperDark = false,
        )
        assertTrue("Expected dark text on light wallpaper, got red=${lightWallpaperColors.title.red}", lightWallpaperColors.title.red < 0.2f)
    }
}
