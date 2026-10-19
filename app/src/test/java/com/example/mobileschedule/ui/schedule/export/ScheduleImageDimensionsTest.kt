package com.example.mobileschedule.ui.schedule.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleImageDimensionsTest {

    @Test
    fun dimensionsCalculationFor12Sections7Days2x() {
        val dims = ExportDimensions.calculate(
            scale = 2.0f,
            daysCount = 7,
            maxSections = 12,
            showWatermark = true,
        )

        // 验证 7 天 12 节在 2x 缩放下尺寸
        // width = (16*2 + 48 + 7*66) * 2 = (32 + 48 + 462) * 2 = 542 * 2 = 1084 px
        assertEquals(7, dims.daysCount)
        assertEquals(2.0f, dims.scale, 0.01f)
        assertTrue("Width should be over 1000px for 2x 7 days", dims.totalWidth >= 1000)
        // height = (110 + 46 + 12*66 + 40) * 2 = (196 + 792) * 2 = 988 * 2 = 1976 px
        assertTrue("Height should be around 1900~2100px for 2x 12 sections", dims.totalHeight in 1900..2100)

        // 验证纵横比适合手机壁纸 (接近 9:16 ~ 9:20)
        val aspectRatio = dims.totalHeight.toFloat() / dims.totalWidth.toFloat()
        assertTrue("Aspect ratio should be wallpaper-friendly (1.7 ~ 2.1)", aspectRatio in 1.7f..2.1f)
    }

    @Test
    fun dimensionsCalculationFor8Sections5Days2x() {
        val dims = ExportDimensions.calculate(
            scale = 2.0f,
            daysCount = 5,
            maxSections = 8,
            showWatermark = true,
        )

        assertEquals(5, dims.daysCount)
        assertEquals(2.0f, dims.scale, 0.01f)
        // 5 列日宽自适应加宽 (86dp)，保证在 5 天模式下排版依旧饱满
        assertTrue("Width should be adequate for 5-day layout", dims.totalWidth >= 950)
        // 8 节课总高度缩短
        assertTrue("Height should be shorter for 8 sections", dims.totalHeight in 1350..1600)
    }

    @Test
    fun dimensionsCalculationFor3xUltraResolution() {
        val dims2x = ExportDimensions.calculate(
            scale = 2.0f,
            daysCount = 7,
            maxSections = 12,
            showWatermark = true,
        )
        val dims3x = ExportDimensions.calculate(
            scale = 3.0f,
            daysCount = 7,
            maxSections = 12,
            showWatermark = true,
        )

        // 3x 尺寸应严格为 2x 的 1.5 倍
        assertEquals((dims2x.totalWidth * 1.5f).toInt(), dims3x.totalWidth)
        assertEquals((dims2x.totalHeight * 1.5f).toInt(), dims3x.totalHeight)
        assertTrue("3x width should be crisp for 2K/4K wallpapers (> 1600px)", dims3x.totalWidth > 1600)
        assertTrue("3x height should exceed 2800px", dims3x.totalHeight > 2800)
    }

    @Test
    fun daysCalculationRespectsShowWeekendAndFirstDayOfWeek() {
        val firstMonday = LocalDate.of(2026, 9, 21) // 2026-09-21 周一

        // 1. 不显示周末 -> 仅 5 天（周一至周五）
        val days5 = ScheduleImageExporter.calculateDays(
            firstWeekMonday = firstMonday,
            weekNum = 1,
            showWeekend = false,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertEquals(5, days5.size)
        assertEquals("周一", days5.first().label)
        assertEquals("周五", days5.last().label)
        assertEquals(1, days5.first().dayOfWeek)
        assertEquals(5, days5.last().dayOfWeek)

        // 2. 显示周末且周一为第一列 -> 7 天（周一至周日）
        val days7Monday = ScheduleImageExporter.calculateDays(
            firstWeekMonday = firstMonday,
            weekNum = 1,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertEquals(7, days7Monday.size)
        assertEquals("周一", days7Monday.first().label)
        assertEquals("周日", days7Monday.last().label)

        // 3. 显示周末且周日为第一列 -> 7 天（周日为 index 0，周一为 index 1）
        val days7Sunday = ScheduleImageExporter.calculateDays(
            firstWeekMonday = firstMonday,
            weekNum = 1,
            showWeekend = true,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )
        assertEquals(7, days7Sunday.size)
        assertEquals("周日", days7Sunday.first().label)
        assertEquals(7, days7Sunday.first().dayOfWeek)
        assertEquals("周六", days7Sunday.last().label)
        assertEquals(6, days7Sunday.last().dayOfWeek)
    }

    @Test
    fun exportThemesEnumAndDisplayNames() {
        assertEquals("浅色柔和", ExportTheme.LIGHT.displayName)
        assertEquals("暗黑壁纸", ExportTheme.DARK.displayName)
        assertEquals("纯白打印", ExportTheme.PRINT.displayName)
        assertEquals(3, ExportTheme.entries.size)
    }
}
