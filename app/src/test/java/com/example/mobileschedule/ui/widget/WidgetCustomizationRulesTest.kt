package com.example.mobileschedule.ui.widget

import com.example.mobileschedule.R
import com.example.mobileschedule.data.preferences.WidgetClickAction
import com.example.mobileschedule.data.preferences.WidgetCornerRadius
import com.example.mobileschedule.data.preferences.WidgetCustomizationOptions
import com.example.mobileschedule.data.preferences.WidgetTextColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCustomizationRulesTest {

    @Test
    fun defaultOptions_haveSensibleValues() {
        val options = WidgetCustomizationOptions()
        assertTrue(options.isFollowTheme)
        assertEquals("#FFFFFF", options.customColorHex)
        assertEquals(1.0f, options.opacity, 0.001f)
        assertEquals(WidgetTextColorMode.AUTO, options.textColorMode)
        assertEquals(WidgetCornerRadius.STANDARD, options.cornerRadius)
        assertTrue(options.showLocation)
        assertFalse(options.showTeacher)
        assertFalse(options.showLunar)
        assertTrue(options.showTomorrowPreview)
        assertTrue(options.showAllCourses)
        assertEquals(2, options.maxCourseRows)
        assertEquals(WidgetClickAction.OPEN_SCHEDULE, options.clickAction)
    }

    @Test
    fun presets_areValidHexColors() {
        assertEquals(12, WidgetCustomizationOptions.PRESETS.size)
        val hexRegex = Regex("^#[0-9A-Fa-f]{6}$")
        for (preset in WidgetCustomizationOptions.PRESETS) {
            assertTrue("Preset ${preset.name} hex ${preset.hex} is invalid", hexRegex.matches(preset.hex))
        }
    }

    @Test
    fun parseHexColor_handlesFormatsCorrectly() {
        // 6 digits
        assertEquals(0xFFFFFFFF.toInt(), WidgetThemeHelper.parseHexColor("#FFFFFF"))
        assertEquals(0xFF000000.toInt(), WidgetThemeHelper.parseHexColor("#000000"))
        assertEquals(0xFF2563EB.toInt(), WidgetThemeHelper.parseHexColor("#2563EB"))
        // Without hash
        assertEquals(0xFF2563EB.toInt(), WidgetThemeHelper.parseHexColor("2563EB"))
        // 3 digits
        assertEquals(0xFFFFFFFF.toInt(), WidgetThemeHelper.parseHexColor("#FFF"))
        // Invalid input returns default
        assertEquals(0xFFFFFFFF.toInt(), WidgetThemeHelper.parseHexColor("invalid", 0xFFFFFFFF.toInt()))
    }

    @Test
    fun calculateLuminance_pureWhiteAndBlack() {
        val whiteLum = WidgetThemeHelper.calculateLuminance(0xFFFFFFFF.toInt())
        val blackLum = WidgetThemeHelper.calculateLuminance(0xFF000000.toInt())
        assertEquals(1.0, whiteLum, 0.01)
        assertEquals(0.0, blackLum, 0.01)
    }

    @Test
    fun formatCourseInfo_formatsAccordingToToggles() {
        val time = "08:00 - 09:35"
        val loc = "旗山1号楼102"
        val teacher = "肖老师"

        // Both enabled
        val all = WidgetThemeHelper.formatCourseInfo(time, loc, teacher, showLocation = true, showTeacher = true)
        assertEquals("08:00 - 09:35 · 肖老师 · 旗山1号楼102", all)

        // Only location
        val locOnly = WidgetThemeHelper.formatCourseInfo(time, loc, teacher, showLocation = true, showTeacher = false)
        assertEquals("08:00 - 09:35 · 旗山1号楼102", locOnly)

        // Only teacher
        val teacherOnly = WidgetThemeHelper.formatCourseInfo(time, loc, teacher, showLocation = false, showTeacher = true)
        assertEquals("08:00 - 09:35 · 肖老师", teacherOnly)

        // Neither
        val neither = WidgetThemeHelper.formatCourseInfo(time, loc, teacher, showLocation = false, showTeacher = false)
        assertEquals("08:00 - 09:35", neither)

        // Blank fields are ignored
        val blankTeacher = WidgetThemeHelper.formatCourseInfo(time, loc, "", showLocation = true, showTeacher = true)
        assertEquals("08:00 - 09:35 · 旗山1号楼102", blankTeacher)
    }

    @Test
    fun formatHeaderDate_formatsLunarCorrectly() {
        val dateText = "10月15日 周三"
        val lunar = "农历九月初六"

        // With lunar
        val withLunar = WidgetThemeHelper.formatHeaderDate(dateText, lunar, showLunar = true)
        assertEquals("10月15日 周三 · 九月初六", withLunar)

        // Without lunar
        val withoutLunar = WidgetThemeHelper.formatHeaderDate(dateText, lunar, showLunar = false)
        assertEquals("10月15日 周三", withoutLunar)

        // Blank lunar
        val blankLunar = WidgetThemeHelper.formatHeaderDate(dateText, "", showLunar = true)
        assertEquals("10月15日 周三", blankLunar)
    }

    @Test
    fun resolveColors_cornerRadiusDrawables() {
        val optSquare = WidgetCustomizationOptions(cornerRadius = WidgetCornerRadius.SQUARE)
        assertEquals(R.drawable.bg_widget_corner_0, WidgetThemeHelper.resolveColors(optSquare, false).cornerDrawableRes)

        val optSmall = WidgetCustomizationOptions(cornerRadius = WidgetCornerRadius.SMALL)
        assertEquals(R.drawable.bg_widget_corner_10, WidgetThemeHelper.resolveColors(optSmall, false).cornerDrawableRes)

        val optStandard = WidgetCustomizationOptions(cornerRadius = WidgetCornerRadius.STANDARD)
        assertEquals(R.drawable.bg_widget_corner_18, WidgetThemeHelper.resolveColors(optStandard, false).cornerDrawableRes)

        val optLarge = WidgetCustomizationOptions(cornerRadius = WidgetCornerRadius.LARGE)
        assertEquals(R.drawable.bg_widget_corner_26, WidgetThemeHelper.resolveColors(optLarge, false).cornerDrawableRes)
    }

    @Test
    fun resolveColors_textColorMode_explicitOverrides() {
        val optLight = WidgetCustomizationOptions(
            isFollowTheme = false,
            customColorHex = "#FFFFFF",
            textColorMode = WidgetTextColorMode.LIGHT,
        )
        val lightColors = WidgetThemeHelper.resolveColors(optLight, false)
        assertTrue(lightColors.isLightContent)
        assertEquals(0xFFFFFFFF.toInt(), lightColors.textPrimary)

        val optDark = WidgetCustomizationOptions(
            isFollowTheme = false,
            customColorHex = "#000000",
            textColorMode = WidgetTextColorMode.DARK,
        )
        val darkColors = WidgetThemeHelper.resolveColors(optDark, false)
        assertFalse(darkColors.isLightContent)
        assertEquals(0xFF0F172A.toInt(), darkColors.textPrimary)
    }

    @Test
    fun resolveColors_autoMode_followSystem() {
        val opt = WidgetCustomizationOptions(isFollowTheme = true, textColorMode = WidgetTextColorMode.AUTO)

        val darkSys = WidgetThemeHelper.resolveColors(opt, isSystemDark = true)
        assertTrue(darkSys.isLightContent)
        assertEquals(0xFF18181B.toInt(), darkSys.effectiveBgColor)

        val lightSys = WidgetThemeHelper.resolveColors(opt, isSystemDark = false)
        assertFalse(lightSys.isLightContent)
        assertEquals(0xFFFFFFFF.toInt(), lightSys.effectiveBgColor)
    }

    @Test
    fun resolveColors_autoMode_highTransparency_defaultsToLightText() {
        // When opacity is very low (< 0.35), wallpaper shines through, so light text is chosen
        val optTransparent = WidgetCustomizationOptions(
            isFollowTheme = false,
            customColorHex = "#FFFFFF",
            opacity = 0.2f,
            textColorMode = WidgetTextColorMode.AUTO,
        )
        val colors = WidgetThemeHelper.resolveColors(optTransparent, false)
        assertTrue(colors.isLightContent)
        assertEquals((0.2f * 255).toInt(), colors.alphaInt)
    }

    @Test
    fun resolveColors_autoMode_customDarkAndLightColors() {
        // Dark background -> Light text
        val optDarkBg = WidgetCustomizationOptions(
            isFollowTheme = false,
            customColorHex = "#0F172A",
            opacity = 1.0f,
            textColorMode = WidgetTextColorMode.AUTO,
        )
        val darkBgColors = WidgetThemeHelper.resolveColors(optDarkBg, false)
        assertTrue(darkBgColors.isLightContent)

        // Light background -> Dark text
        val optLightBg = WidgetCustomizationOptions(
            isFollowTheme = false,
            customColorHex = "#FEF3C7", // warm cream
            opacity = 1.0f,
            textColorMode = WidgetTextColorMode.AUTO,
        )
        val lightBgColors = WidgetThemeHelper.resolveColors(optLightBg, false)
        assertFalse(lightBgColors.isLightContent)
    }
}
