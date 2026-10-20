package com.example.mobileschedule.ui.schedule.wallpaper

import androidx.compose.ui.graphics.Color
import com.example.mobileschedule.ui.theme.CourseColorPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class CardTextColors(
    val title: Color,
    val secondary: Color,
)

/**
 * WCAG 2.1 Contrast & Luminance calculation utilities for dynamic wallpaper adaptation.
 */
object ScheduleContrastHelper {

    /**
     * Calculates WCAG relative luminance of a color.
     * Normalized between 0.0 (darkest black) and 1.0 (lightest white).
     */
    fun calculateRelativeLuminance(color: Color): Float {
        val r = sRgbToLinear(color.red)
        val g = sRgbToLinear(color.green)
        val b = sRgbToLinear(color.blue)
        return (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
    }

    private fun sRgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    /**
     * Calculates WCAG contrast ratio between two colors (range: 1.0 to 21.0).
     */
    fun calculateContrastRatio(c1: Color, c2: Color): Float {
        val l1 = calculateRelativeLuminance(c1)
        val l2 = calculateRelativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Alpha-blends a foreground color over a background color.
     */
    fun blendColor(foreground: Color, background: Color, foregroundAlpha: Float): Color {
        val alpha = foregroundAlpha.coerceIn(0f, 1f)
        val r = foreground.red * alpha + background.red * (1f - alpha)
        val g = foreground.green * alpha + background.green * (1f - alpha)
        val b = foreground.blue * alpha + background.blue * (1f - alpha)
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
    }

    /**
     * Automatically derives optimal title and secondary text colors according to WCAG contrast standards,
     * considering card opacity and wallpaper darkness.
     */
    fun resolveCardTextColors(
        basePalette: CourseColorPalette,
        cardOpacity: Float,
        hasWallpaper: Boolean,
        isWallpaperDark: Boolean,
    ): CardTextColors {
        if (!hasWallpaper || cardOpacity >= 0.96f) {
            return CardTextColors(
                title = basePalette.onContainer,
                secondary = basePalette.secondaryText,
            )
        }

        val wallpaperBacking = if (isWallpaperDark) Color(0xFF18181B) else Color(0xFFF9FAFB)
        val blendedContainer = blendColor(basePalette.container, wallpaperBacking, cardOpacity)
        val blendedLuminance = calculateRelativeLuminance(blendedContainer)

        return if (blendedLuminance < 0.42f) {
            // Blended background is dark -> Use high-contrast light text
            CardTextColors(
                title = Color.White.copy(alpha = 0.95f),
                secondary = Color(0xFFE5E7EB).copy(alpha = 0.85f),
            )
        } else {
            // Blended background is light -> Use high-contrast dark text
            CardTextColors(
                title = Color(0xFF111827).copy(alpha = 0.95f),
                secondary = Color(0xFF374151).copy(alpha = 0.82f),
            )
        }
    }
}
