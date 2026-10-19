package com.example.mobileschedule.ui.schedule.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ScheduleRules
import com.example.mobileschedule.ui.theme.CourseColorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

enum class ExportTheme(val displayName: String) {
    LIGHT("浅色柔和"),
    DARK("暗黑壁纸"),
    PRINT("纯白打印");
}

enum class ExportResolution(val displayName: String, val scale: Float) {
    SUPER_2X("2x 超清", 2.0f),
    ULTRA_3X("3x 极清壁纸", 3.0f);
}

data class ExportOptions(
    val theme: ExportTheme = ExportTheme.LIGHT,
    val resolution: ExportResolution = ExportResolution.SUPER_2X,
    val showWeekend: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val showNonCurrentWeekCourses: Boolean = false,
    val showLocation: Boolean = true,
    val showTeacher: Boolean = true,
    val showWatermark: Boolean = true,
)

data class ExportDimensions(
    val scale: Float,
    val timeColWidth: Float,
    val dayColWidth: Float,
    val paddingHorizontal: Float,
    val headerHeight: Float,
    val dateHeaderHeight: Float,
    val sectionHeight: Float,
    val footerHeight: Float,
    val totalWidth: Int,
    val totalHeight: Int,
    val daysCount: Int,
) {
    companion object {
        fun calculate(
            scale: Float,
            daysCount: Int,
            maxSections: Int,
            showWatermark: Boolean,
        ): ExportDimensions {
            val s = scale.coerceAtLeast(1.0f)
            val timeColWidth = 48f * s
            val dayColWidth = if (daysCount <= 5) 86f * s else 66f * s
            val paddingHorizontal = 16f * s
            val headerHeight = 110f * s
            val dateHeaderHeight = 46f * s
            val sectionHeight = 66f * s
            val footerHeight = if (showWatermark) 40f * s else 16f * s
            val totalWidth = (paddingHorizontal * 2 + timeColWidth + daysCount * dayColWidth).toInt()
            val totalHeight = (headerHeight + dateHeaderHeight + maxSections * sectionHeight + footerHeight).toInt()
            return ExportDimensions(
                scale = s,
                timeColWidth = timeColWidth,
                dayColWidth = dayColWidth,
                paddingHorizontal = paddingHorizontal,
                headerHeight = headerHeight,
                dateHeaderHeight = dateHeaderHeight,
                sectionHeight = sectionHeight,
                footerHeight = footerHeight,
                totalWidth = totalWidth,
                totalHeight = totalHeight,
                daysCount = daysCount,
            )
        }
    }
}

internal data class ExportDay(
    val dayOfWeek: Int,
    val label: String,
    val date: LocalDate,
    val columnIndex: Int,
)

internal data class ThemeColorSpec(
    val backgroundColor: Int,
    val headerCardColor: Int,
    val headerCardStroke: Int,
    val titleTextColor: Int,
    val subtitleTextColor: Int,
    val schoolBadgeBg: Int,
    val schoolBadgeText: Int,
    val gridLineColor: Int,
    val timeColBg: Int,
    val timeSectionTextColor: Int,
    val timeRangeTextColor: Int,
    val dateHeaderBg: Int,
    val dateHeaderTextColor: Int,
    val dateHeaderSubtextColor: Int,
    val todayPillBg: Int,
    val todayPillText: Int,
    val footerTextColor: Int,
    val inactiveCardBg: Int,
    val inactiveCardText: Int,
    val inactiveBadgeBg: Int,
    val inactiveBadgeText: Int,
)

object ScheduleImageExporter {

    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun resolveThemeSpec(theme: ExportTheme): ThemeColorSpec = when (theme) {
        ExportTheme.LIGHT -> ThemeColorSpec(
            backgroundColor = Color.rgb(248, 249, 250),
            headerCardColor = Color.rgb(255, 255, 255),
            headerCardStroke = Color.rgb(226, 232, 240),
            titleTextColor = Color.rgb(30, 41, 59),
            subtitleTextColor = Color.rgb(100, 116, 139),
            schoolBadgeBg = Color.rgb(232, 241, 252),
            schoolBadgeText = Color.rgb(30, 58, 138),
            gridLineColor = Color.rgb(241, 243, 245),
            timeColBg = Color.rgb(248, 249, 250),
            timeSectionTextColor = Color.rgb(51, 65, 85),
            timeRangeTextColor = Color.rgb(148, 163, 184),
            dateHeaderBg = Color.rgb(255, 255, 255),
            dateHeaderTextColor = Color.rgb(30, 41, 59),
            dateHeaderSubtextColor = Color.rgb(100, 116, 139),
            todayPillBg = Color.rgb(37, 99, 235),
            todayPillText = Color.rgb(255, 255, 255),
            footerTextColor = Color.rgb(148, 163, 184),
            inactiveCardBg = Color.rgb(241, 243, 245),
            inactiveCardText = Color.rgb(108, 117, 125),
            inactiveBadgeBg = Color.rgb(222, 226, 230),
            inactiveBadgeText = Color.rgb(73, 80, 87),
        )
        ExportTheme.DARK -> ThemeColorSpec(
            backgroundColor = Color.rgb(15, 23, 42),
            headerCardColor = Color.rgb(30, 41, 59),
            headerCardStroke = Color.rgb(51, 65, 85),
            titleTextColor = Color.rgb(248, 250, 252),
            subtitleTextColor = Color.rgb(148, 163, 184),
            schoolBadgeBg = Color.rgb(30, 58, 138),
            schoolBadgeText = Color.rgb(186, 230, 253),
            gridLineColor = Color.rgb(30, 41, 59),
            timeColBg = Color.rgb(15, 23, 42),
            timeSectionTextColor = Color.rgb(226, 232, 240),
            timeRangeTextColor = Color.rgb(100, 116, 139),
            dateHeaderBg = Color.rgb(30, 41, 59),
            dateHeaderTextColor = Color.rgb(248, 250, 252),
            dateHeaderSubtextColor = Color.rgb(148, 163, 184),
            todayPillBg = Color.rgb(59, 130, 246),
            todayPillText = Color.rgb(255, 255, 255),
            footerTextColor = Color.rgb(100, 116, 139),
            inactiveCardBg = Color.rgb(30, 38, 56),
            inactiveCardText = Color.rgb(148, 163, 184),
            inactiveBadgeBg = Color.rgb(51, 65, 85),
            inactiveBadgeText = Color.rgb(226, 232, 240),
        )
        ExportTheme.PRINT -> ThemeColorSpec(
            backgroundColor = Color.rgb(255, 255, 255),
            headerCardColor = Color.rgb(255, 255, 255),
            headerCardStroke = Color.rgb(0, 0, 0),
            titleTextColor = Color.rgb(0, 0, 0),
            subtitleTextColor = Color.rgb(51, 51, 51),
            schoolBadgeBg = Color.rgb(238, 238, 238),
            schoolBadgeText = Color.rgb(0, 0, 0),
            gridLineColor = Color.rgb(208, 208, 208),
            timeColBg = Color.rgb(255, 255, 255),
            timeSectionTextColor = Color.rgb(0, 0, 0),
            timeRangeTextColor = Color.rgb(85, 85, 85),
            dateHeaderBg = Color.rgb(255, 255, 255),
            dateHeaderTextColor = Color.rgb(0, 0, 0),
            dateHeaderSubtextColor = Color.rgb(68, 68, 68),
            todayPillBg = Color.rgb(0, 0, 0),
            todayPillText = Color.rgb(255, 255, 255),
            footerTextColor = Color.rgb(102, 102, 102),
            inactiveCardBg = Color.rgb(245, 245, 245),
            inactiveCardText = Color.rgb(119, 119, 119),
            inactiveBadgeBg = Color.rgb(238, 238, 238),
            inactiveBadgeText = Color.rgb(85, 85, 85),
        )
    }

    internal fun calculateDays(
        firstWeekMonday: LocalDate,
        weekNum: Int,
        showWeekend: Boolean,
        firstDayOfWeek: DayOfWeek,
    ): List<ExportDay> {
        val monday = firstWeekMonday.plusWeeks((weekNum - 1).toLong())
        return if (!showWeekend) {
            listOf(
                ExportDay(1, "周一", monday, 0),
                ExportDay(2, "周二", monday.plusDays(1), 1),
                ExportDay(3, "周三", monday.plusDays(2), 2),
                ExportDay(4, "周四", monday.plusDays(3), 3),
                ExportDay(5, "周五", monday.plusDays(4), 4),
            )
        } else if (firstDayOfWeek == DayOfWeek.SUNDAY) {
            val sunday = monday.minusDays(1)
            listOf(
                ExportDay(7, "周日", sunday, 0),
                ExportDay(1, "周一", monday, 1),
                ExportDay(2, "周二", monday.plusDays(1), 2),
                ExportDay(3, "周三", monday.plusDays(2), 3),
                ExportDay(4, "周四", monday.plusDays(3), 4),
                ExportDay(5, "周五", monday.plusDays(4), 5),
                ExportDay(6, "周六", monday.plusDays(5), 6),
            )
        } else {
            listOf(
                ExportDay(1, "周一", monday, 0),
                ExportDay(2, "周二", monday.plusDays(1), 1),
                ExportDay(3, "周三", monday.plusDays(2), 2),
                ExportDay(4, "周四", monday.plusDays(3), 3),
                ExportDay(5, "周五", monday.plusDays(4), 4),
                ExportDay(6, "周六", monday.plusDays(5), 5),
                ExportDay(7, "周日", monday.plusDays(6), 6),
            )
        }
    }

    fun renderBitmap(
        semesterName: String,
        schoolName: String = "福建师范大学",
        weekNum: Int,
        firstWeekMonday: LocalDate,
        maxSections: Int,
        sectionTimes: List<SectionTime> = ScheduleRules.defaultSectionTimes,
        arrangements: List<CourseArrangement>,
        today: LocalDate = LocalDate.now(),
        options: ExportOptions = ExportOptions(),
    ): Bitmap {
        val scale = options.resolution.scale
        val displayedDays = calculateDays(firstWeekMonday, weekNum, options.showWeekend, options.firstDayOfWeek)
        val dimensions = ExportDimensions.calculate(
            scale = scale,
            daysCount = displayedDays.size,
            maxSections = maxSections,
            showWatermark = options.showWatermark,
        )

        val bitmap = Bitmap.createBitmap(dimensions.totalWidth, dimensions.totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val themeSpec = resolveThemeSpec(options.theme)

        // 1. 绘制总背景
        canvas.drawColor(themeSpec.backgroundColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        // 2. 绘制顶部学期与周次信息卡片
        drawHeaderCard(canvas, dimensions, semesterName, schoolName, weekNum, displayedDays, themeSpec, paint, textPaint)

        // 3. 绘制日期星期表头
        val gridStartY = dimensions.headerHeight
        drawDateHeaders(canvas, dimensions, displayedDays, today, gridStartY, themeSpec, paint, textPaint)

        // 4. 绘制节次时间标尺与背景网格
        val contentStartY = gridStartY + dimensions.dateHeaderHeight
        drawTimeRulerAndGrid(canvas, dimensions, maxSections, sectionTimes, contentStartY, themeSpec, paint, textPaint)

        // 5. 绘制课程卡片
        drawCourseCards(
            canvas, dimensions, displayedDays, arrangements, weekNum,
            contentStartY, maxSections, options, themeSpec, paint, textPaint,
        )

        // 6. 绘制底部水印
        if (options.showWatermark) {
            drawFooter(canvas, dimensions, schoolName, today, themeSpec, paint)
        }

        return bitmap
    }

    private fun drawHeaderCard(
        canvas: Canvas,
        dimensions: ExportDimensions,
        semesterName: String,
        schoolName: String,
        weekNum: Int,
        days: List<ExportDay>,
        spec: ThemeColorSpec,
        paint: Paint,
        textPaint: TextPaint,
    ) {
        val s = dimensions.scale
        val cardLeft = dimensions.paddingHorizontal
        val cardTop = 16f * s
        val cardRight = dimensions.totalWidth - dimensions.paddingHorizontal
        val cardBottom = dimensions.headerHeight - 8f * s
        val cornerRadius = 14f * s

        // 卡片底色与边框
        paint.style = Paint.Style.FILL
        paint.color = spec.headerCardColor
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * s
        paint.color = spec.headerCardStroke
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL

        // 学校名称标签胶囊
        val badgeLeft = cardLeft + 14f * s
        val badgeTop = cardTop + 14f * s
        paint.color = spec.schoolBadgeBg
        textPaint.textSize = 10f * s
        textPaint.typeface = Typeface.DEFAULT_BOLD
        val badgeText = schoolName
        val badgeTextWidth = textPaint.measureText(badgeText)
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeTextWidth + 14f * s, badgeTop + 20f * s)
        canvas.drawRoundRect(badgeRect, 10f * s, 10f * s, paint)

        paint.color = spec.schoolBadgeText
        paint.textSize = 10f * s
        paint.typeface = Typeface.DEFAULT_BOLD
        val badgeTextY = badgeTop + 14f * s
        canvas.drawText(badgeText, badgeLeft + 7f * s, badgeTextY, paint)

        // 学期名称与第 X 周大标题
        paint.color = spec.titleTextColor
        paint.textSize = 16f * s
        paint.typeface = Typeface.DEFAULT_BOLD
        val titleText = "$semesterName · 第 $weekNum 周"
        canvas.drawText(titleText, cardLeft + 14f * s, cardTop + 54f * s, paint)

        // 日期范围副标题
        val startDateStr = days.firstOrNull()?.date?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) ?: ""
        val endDateStr = days.lastOrNull()?.date?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) ?: ""
        val subtitleText = "$startDateStr ~ $endDateStr"
        paint.color = spec.subtitleTextColor
        paint.textSize = 11f * s
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(subtitleText, cardLeft + 14f * s, cardTop + 72f * s, paint)
    }

    private fun drawDateHeaders(
        canvas: Canvas,
        dimensions: ExportDimensions,
        days: List<ExportDay>,
        today: LocalDate,
        startY: Float,
        spec: ThemeColorSpec,
        paint: Paint,
        textPaint: TextPaint,
    ) {
        val s = dimensions.scale
        val headerRect = RectF(
            dimensions.paddingHorizontal + dimensions.timeColWidth,
            startY,
            dimensions.totalWidth - dimensions.paddingHorizontal,
            startY + dimensions.dateHeaderHeight,
        )

        paint.color = spec.dateHeaderBg
        paint.style = Paint.Style.FILL
        canvas.drawRect(headerRect, paint)

        // 绘制各列星期与日期
        for (day in days) {
            val colLeft = dimensions.paddingHorizontal + dimensions.timeColWidth + day.columnIndex * dimensions.dayColWidth
            val colCenter = colLeft + dimensions.dayColWidth / 2f
            val isToday = day.date == today

            if (isToday) {
                // 今日药丸高亮
                paint.color = spec.todayPillBg
                val pillWidth = 42f * s
                val pillHeight = 20f * s
                val pillRect = RectF(
                    colCenter - pillWidth / 2f,
                    startY + 4f * s,
                    colCenter + pillWidth / 2f,
                    startY + 4f * s + pillHeight,
                )
                canvas.drawRoundRect(pillRect, 10f * s, 10f * s, paint)

                paint.color = spec.todayPillText
                paint.textSize = 11f * s
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(day.label, colCenter, startY + 18f * s, paint)
            } else {
                paint.color = spec.dateHeaderTextColor
                paint.textSize = 11.5f * s
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(day.label, colCenter, startY + 18f * s, paint)
            }

            // 日期文本 (MM/dd)
            paint.color = spec.dateHeaderSubtextColor
            paint.textSize = 9.5f * s
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(day.date.format(dateFormatter), colCenter, startY + 36f * s, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTimeRulerAndGrid(
        canvas: Canvas,
        dimensions: ExportDimensions,
        maxSections: Int,
        sectionTimes: List<SectionTime>,
        startY: Float,
        spec: ThemeColorSpec,
        paint: Paint,
        textPaint: TextPaint,
    ) {
        val s = dimensions.scale
        val timeColLeft = dimensions.paddingHorizontal
        val timeColWidth = dimensions.timeColWidth
        val gridRight = dimensions.totalWidth - dimensions.paddingHorizontal

        paint.color = spec.gridLineColor
        paint.strokeWidth = 0.8f * s
        paint.style = Paint.Style.STROKE

        for (section in 1..maxSections) {
            val rowTop = startY + (section - 1) * dimensions.sectionHeight
            val rowBottom = rowTop + dimensions.sectionHeight

            // 节次行分割横线
            canvas.drawLine(timeColLeft, rowBottom, gridRight, rowBottom, paint)

            // 左侧时间标尺内容
            paint.style = Paint.Style.FILL
            paint.color = spec.timeSectionTextColor
            paint.textSize = 12f * s
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            val sectionX = timeColLeft + timeColWidth / 2f
            canvas.drawText(section.toString(), sectionX, rowTop + 24f * s, paint)

            // 起止时间
            val sectionTime = sectionTimes.firstOrNull { it.section == section }
            if (sectionTime != null) {
                paint.color = spec.timeRangeTextColor
                paint.textSize = 8.5f * s
                paint.typeface = Typeface.DEFAULT
                canvas.drawText(sectionTime.start.format(timeFormatter), sectionX, rowTop + 40f * s, paint)
                canvas.drawText(sectionTime.end.format(timeFormatter), sectionX, rowTop + 53f * s, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.color = spec.gridLineColor
        }

        // 竖向网格分割线
        paint.color = spec.gridLineColor
        paint.strokeWidth = 0.8f * s
        // 时间标尺与周列分割线
        canvas.drawLine(timeColLeft + timeColWidth, startY, timeColLeft + timeColWidth, startY + maxSections * dimensions.sectionHeight, paint)

        // 每日列之间的竖向网格线
        for (i in 1 until dimensions.daysCount) {
            val colX = timeColLeft + timeColWidth + i * dimensions.dayColWidth
            canvas.drawLine(colX, startY, colX, startY + maxSections * dimensions.sectionHeight, paint)
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawCourseCards(
        canvas: Canvas,
        dimensions: ExportDimensions,
        days: List<ExportDay>,
        arrangements: List<CourseArrangement>,
        currentWeek: Int,
        contentStartY: Float,
        maxSections: Int,
        options: ExportOptions,
        spec: ThemeColorSpec,
        paint: Paint,
        textPaint: TextPaint,
    ) {
        val s = dimensions.scale
        val cornerRadius = 8f * s

        // 过滤在展示日内的课程，并区分活跃与非本周
        val dayMap = days.associateBy { it.dayOfWeek }
        val visibleArrangements = arrangements.filter { course ->
            dayMap.containsKey(course.dayOfWeek) &&
            course.startSection in 1..maxSections &&
            (options.showNonCurrentWeekCourses || course.weeks.contains(currentWeek))
        }.sortedWith(
            compareBy(
                { if (it.weeks.contains(currentWeek)) 0 else 1 }, // 活跃课程优先排在最前
                { it.startSection },
                { it.id },
            )
        )

        for (course in visibleArrangements) {
            val day = dayMap[course.dayOfWeek] ?: continue
            val isActive = course.weeks.contains(currentWeek)

            val colLeft = dimensions.paddingHorizontal + dimensions.timeColWidth + day.columnIndex * dimensions.dayColWidth
            val cardLeft = colLeft + 2.5f * s
            val cardRight = colLeft + dimensions.dayColWidth - 2.5f * s
            val cardWidth = cardRight - cardLeft

            val startSec = course.startSection.coerceIn(1, maxSections)
            val endSec = course.endSection.coerceIn(startSec, maxSections)
            val cardTop = contentStartY + (startSec - 1) * dimensions.sectionHeight + 2.5f * s
            val cardBottom = contentStartY + endSec * dimensions.sectionHeight - 2.5f * s
            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

            // 解析卡片色彩
            val isDark = options.theme == ExportTheme.DARK
            val isPrint = options.theme == ExportTheme.PRINT

            val (cardBg, titleColor, subtextColor) = if (isPrint) {
                if (isActive) {
                    Triple(Color.rgb(252, 252, 252), Color.rgb(0, 0, 0), Color.rgb(60, 60, 60))
                } else {
                    Triple(spec.inactiveCardBg, spec.inactiveCardText, spec.inactiveCardText)
                }
            } else if (isActive) {
                val palette = CourseColorTheme.forCourse(course.name, isDark = isDark, colorIndex = null)
                Triple(palette.container.toArgb(), palette.onContainer.toArgb(), palette.secondaryText.toArgb())
            } else {
                Triple(spec.inactiveCardBg, spec.inactiveCardText, spec.inactiveCardText)
            }

            // 绘制卡片底色
            paint.style = Paint.Style.FILL
            paint.color = cardBg
            paint.alpha = if (isActive) 255 else 115 // 非本周 45% 透明度
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)

            // 绘制边框
            if (!isActive) {
                // 虚线细边框
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * s
                paint.color = if (isPrint) Color.rgb(80, 80, 80) else Color.rgb(173, 181, 189)
                paint.alpha = 180
                paint.pathEffect = DashPathEffect(floatArrayOf(6f * s, 4f * s), 0f)
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)
                paint.pathEffect = null
            } else if (isPrint) {
                // 打印模式下清晰黑色实线边框
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * s
                paint.color = Color.rgb(0, 0, 0)
                paint.alpha = 255
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f * s
                paint.color = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(20, 0, 0, 0)
                paint.alpha = 255
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint)
            }
            paint.style = Paint.Style.FILL

            // 非本周右上角小角标
            if (!isActive) {
                val badgeWidth = 26f * s
                val badgeHeight = 12f * s
                val badgeRect = RectF(cardRight - badgeWidth - 3f * s, cardTop + 3f * s, cardRight - 3f * s, cardTop + 3f * s + badgeHeight)
                paint.color = spec.inactiveBadgeBg
                paint.alpha = 220
                canvas.drawRoundRect(badgeRect, 4f * s, 4f * s, paint)

                paint.color = spec.inactiveBadgeText
                paint.alpha = 255
                paint.textSize = 7.5f * s
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("非本周", badgeRect.centerX(), badgeRect.centerY() + 2.5f * s, paint)
                paint.textAlign = Paint.Align.LEFT
            }

            // 绘制卡片内文本（名称、教室、教师、周次说明）
            val textLeft = cardLeft + 5f * s
            val textRight = cardRight - 5f * s
            val maxTextWidth = max(1, (textRight - textLeft).toInt())
            var currentY = cardTop + 8f * s

            // 课程名称
            textPaint.color = titleColor
            textPaint.alpha = if (isActive) 255 else 200
            textPaint.textSize = 10.5f * s
            textPaint.typeface = Typeface.DEFAULT_BOLD

            val titleLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(course.name, 0, course.name.length, textPaint, maxTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setMaxLines(if (endSec > startSec) 3 else 2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(course.name, textPaint, maxTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
            }

            canvas.save()
            canvas.translate(textLeft, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 4f * s

            // 教室地点
            val location = course.location
            if (options.showLocation && !location.isNullOrBlank()) {
                val locText = "@$location"
                textPaint.color = subtextColor
                textPaint.alpha = if (isActive) 255 else 180
                textPaint.textSize = 8.5f * s
                textPaint.typeface = Typeface.DEFAULT_BOLD

                val locLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(locText, 0, locText.length, textPaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .setMaxLines(2)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(locText, textPaint, maxTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
                }

                canvas.save()
                canvas.translate(textLeft, currentY)
                locLayout.draw(canvas)
                canvas.restore()
                currentY += locLayout.height + 2f * s
            }

            // 任课教师
            val teacher = course.teacher
            if (options.showTeacher && !teacher.isNullOrBlank() && currentY + 12f * s < cardBottom) {
                val teacherText = teacher
                textPaint.color = subtextColor
                textPaint.alpha = if (isActive) 230 else 160
                textPaint.textSize = 8f * s
                textPaint.typeface = Typeface.DEFAULT

                val teacherLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(teacherText, 0, teacherText.length, textPaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .setMaxLines(1)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(teacherText, textPaint, maxTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
                }

                canvas.save()
                canvas.translate(textLeft, currentY)
                teacherLayout.draw(canvas)
                canvas.restore()
                currentY += teacherLayout.height + 2f * s
            }

            // 非本周周次说明
            if (!isActive && currentY + 10f * s < cardBottom) {
                val weekNotice = "${ScheduleRules.formatWeekInterval(course.weeks)}上课"
                textPaint.color = spec.inactiveCardText
                textPaint.alpha = 210
                textPaint.textSize = 7.5f * s
                textPaint.typeface = Typeface.DEFAULT_BOLD

                val weekLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(weekNotice, 0, weekNotice.length, textPaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .setMaxLines(1)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(weekNotice, textPaint, maxTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
                }

                canvas.save()
                canvas.translate(textLeft, currentY)
                weekLayout.draw(canvas)
                canvas.restore()
            }
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        dimensions: ExportDimensions,
        schoolName: String,
        today: LocalDate,
        spec: ThemeColorSpec,
        paint: Paint,
    ) {
        val s = dimensions.scale
        val footerY = dimensions.totalHeight - 16f * s
        val text = "$schoolName 移动课表 · 由同学自主生成导出 · ${today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}"

        paint.color = spec.footerTextColor
        paint.textSize = 9.5f * s
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, dimensions.totalWidth / 2f, footerY, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String = "MobileSchedule_Week_${System.currentTimeMillis()}.png",
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MobileSchedule")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        }
        uri
    }

    suspend fun createShareIntent(
        context: Context,
        bitmap: Bitmap,
        weekNum: Int,
    ): Intent = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(cacheDir, "schedule_week_${weekNum}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
