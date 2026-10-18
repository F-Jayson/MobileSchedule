package com.example.mobileschedule.ui.widget

import com.example.mobileschedule.R
import com.example.mobileschedule.data.preferences.WidgetCornerRadius
import com.example.mobileschedule.data.preferences.WidgetCustomizationOptions
import com.example.mobileschedule.data.preferences.WidgetTextColorMode
import kotlin.math.pow

data class WidgetResolvedColors(
    val effectiveBgColor: Int,
    val alphaInt: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textAccent: Int,
    val dividerColor: Int,
    val pillBgColor: Int,
    val badgeNormalBg: Int,
    val badgeNormalText: Int,
    val isLightContent: Boolean,
    val cornerDrawableRes: Int,
)

object WidgetThemeHelper {

    fun parseHexColor(hex: String, defaultColor: Int = 0xFFFFFFFF.toInt()): Int {
        return try {
            val clean = hex.trim().removePrefix("#")
            when (clean.length) {
                6 -> (0xFF000000L or clean.toLong(16)).toInt()
                8 -> clean.toLong(16).toInt()
                3 -> {
                    val r = clean[0]
                    val g = clean[1]
                    val b = clean[2]
                    val expanded = "$r$r$g$g$b$b"
                    (0xFF000000L or expanded.toLong(16)).toInt()
                }
                else -> defaultColor
            }
        } catch (_: Exception) {
            defaultColor
        }
    }

    fun calculateLuminance(colorInt: Int): Double {
        val r = ((colorInt shr 16) and 0xFF) / 255.0
        val g = ((colorInt shr 8) and 0xFF) / 255.0
        val b = (colorInt and 0xFF) / 255.0

        val rLinear = if (r <= 0.04045) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gLinear = if (g <= 0.04045) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bLinear = if (b <= 0.04045) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
    }

    fun resolveColors(
        options: WidgetCustomizationOptions,
        isSystemDark: Boolean,
    ): WidgetResolvedColors {
        val effectiveBgColor = if (options.isFollowTheme) {
            if (isSystemDark) 0xFF18181B.toInt() else 0xFFFFFFFF.toInt()
        } else {
            parseHexColor(options.customColorHex, 0xFFFFFFFF.toInt())
        }

        val alphaInt = (options.opacity.coerceIn(0f, 1f) * 255).toInt()

        val isLightContent = when (options.textColorMode) {
            WidgetTextColorMode.LIGHT -> true
            WidgetTextColorMode.DARK -> false
            WidgetTextColorMode.AUTO -> {
                if (options.isFollowTheme) {
                    isSystemDark
                } else if (options.opacity < 0.35f) {
                    true
                } else {
                    val lum = calculateLuminance(effectiveBgColor)
                    lum < 0.5
                }
            }
        }

        val cornerDrawable = when (options.cornerRadius) {
            WidgetCornerRadius.SQUARE -> R.drawable.bg_widget_corner_0
            WidgetCornerRadius.SMALL -> R.drawable.bg_widget_corner_10
            WidgetCornerRadius.STANDARD -> R.drawable.bg_widget_corner_18
            WidgetCornerRadius.LARGE -> R.drawable.bg_widget_corner_26
        }

        return if (isLightContent) {
            WidgetResolvedColors(
                effectiveBgColor = effectiveBgColor,
                alphaInt = alphaInt,
                textPrimary = 0xFFFFFFFF.toInt(),
                textSecondary = 0xFFCBD5E1.toInt(),
                textAccent = 0xFF93C5FD.toInt(),
                dividerColor = 0x2EFFFFFF.toInt(),
                pillBgColor = 0x2AFFFFFF.toInt(),
                badgeNormalBg = 0x2AFFFFFF.toInt(),
                badgeNormalText = 0xFFF1F5F9.toInt(),
                isLightContent = true,
                cornerDrawableRes = cornerDrawable,
            )
        } else {
            WidgetResolvedColors(
                effectiveBgColor = effectiveBgColor,
                alphaInt = alphaInt,
                textPrimary = 0xFF0F172A.toInt(),
                textSecondary = 0xFF475569.toInt(),
                textAccent = 0xFF2563EB.toInt(),
                dividerColor = 0xFFE2E8F0.toInt(),
                pillBgColor = 0xFFF1F5F9.toInt(),
                badgeNormalBg = 0xFFE2E8F0.toInt(),
                badgeNormalText = 0xFF475569.toInt(),
                isLightContent = false,
                cornerDrawableRes = cornerDrawable,
            )
        }
    }

    fun formatCourseInfo(
        timeText: String,
        location: String,
        teacher: String?,
        showLocation: Boolean,
        showTeacher: Boolean,
    ): String {
        val parts = ArrayList<String>()
        parts.add(timeText)
        if (showTeacher && !teacher.isNullOrBlank()) {
            parts.add(teacher)
        }
        if (showLocation && location.isNotBlank()) {
            parts.add(location)
        }
        return parts.joinToString(" · ")
    }

    fun formatHeaderDate(
        dateText: String,
        lunarString: String,
        showLunar: Boolean,
    ): String {
        if (!showLunar || lunarString.isBlank()) return dateText
        val lunarShort = lunarString.removePrefix("农历")
        return "$dateText · $lunarShort"
    }
}
