package com.example.mobileschedule

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.WidgetCourseItem
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.ui.widget.ScheduleAppWidget2x2Provider
import com.example.mobileschedule.ui.widget.ScheduleAppWidget4x4Provider
import com.example.mobileschedule.ui.widget.ScheduleWidgetNotifier
import com.example.mobileschedule.ui.widget.ScheduleWidgetUpdater
import com.example.mobileschedule.ui.widget.WidgetWeeklyScheduleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleMultiSizeWidgetTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val ongoingCourse = WidgetCourseItem(
        id = 301L,
        title = "软件工程导论",
        sectionText = "第1-2节",
        timeText = "08:20 - 10:00",
        location = "致广 1-201",
        teacher = "张教授",
        isOngoing = true,
        isTomorrow = false,
    )

    private val upcomingCourse = WidgetCourseItem(
        id = 302L,
        title = "编译原理",
        sectionText = "第3-4节",
        timeText = "10:20 - 12:00",
        location = "知明 2-105",
        teacher = "李教授",
        isOngoing = false,
        isTomorrow = false,
    )

    private val tomorrowCourse = WidgetCourseItem(
        id = 303L,
        title = "操作系统",
        sectionText = "第1-2节",
        timeText = "08:20 - 10:00",
        location = "理工楼 304",
        teacher = "王老师",
        isOngoing = false,
        isTomorrow = true,
    )

    @Test
    fun widget2x2_todayCourses_ongoing_rendersFocusCardAndOngoingBadge() {
        val data = WidgetScheduleData.TodayCourses(
            dateText = "10月20日 周二",
            weekText = "第 8 周",
            subtitle = "正在上课",
            courses = listOf(ongoingCourse, upcomingCourse),
            totalRemainingCount = 2,
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews2x2(context, data)
        assertNotNull(views)
        assertEquals(R.layout.widget_schedule_2x2, views.layoutId)

        val container = FrameLayout(context)
        val inflated = views.apply(context, container)
        assertNotNull(inflated)

        val statusPill = inflated.findViewById<TextView>(R.id.widget_2x2_status_pill)
        val courseName = inflated.findViewById<TextView>(R.id.widget_2x2_course_name)
        val timeRange = inflated.findViewById<TextView>(R.id.widget_2x2_time_range)
        val locationTeacher = inflated.findViewById<TextView>(R.id.widget_2x2_location_teacher)
        val headerInfo = inflated.findViewById<TextView>(R.id.widget_2x2_header_info)
        val courseContainer = inflated.findViewById<View>(R.id.widget_2x2_course_container)
        val emptyContainer = inflated.findViewById<View>(R.id.widget_2x2_empty_container)

        assertEquals("正在上课", statusPill.text.toString())
        assertEquals("软件工程导论", courseName.text.toString())
        assertTrue(timeRange.text.toString().contains("08:20"))
        assertTrue(locationTeacher.text.toString().contains("致广 1-201"))
        assertEquals("第 8 周", headerInfo.text.toString())
        assertEquals(View.VISIBLE, courseContainer.visibility)
        assertEquals(View.GONE, emptyContainer.visibility)

        saveWidgetSnapshot(inflated, 400, 400, "widget-2x2-ongoing.png")
    }

    @Test
    fun widget2x2_todayCourses_upcoming_rendersNextCoursePill() {
        val data = WidgetScheduleData.TodayCourses(
            dateText = "10月20日 周二",
            weekText = "第 8 周",
            subtitle = "今日剩余课程",
            courses = listOf(upcomingCourse),
            totalRemainingCount = 1,
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews2x2(context, data)
        val container = FrameLayout(context)
        val inflated = views.apply(context, container)

        val statusPill = inflated.findViewById<TextView>(R.id.widget_2x2_status_pill)
        val courseName = inflated.findViewById<TextView>(R.id.widget_2x2_course_name)

        assertEquals("下一节课", statusPill.text.toString())
        assertEquals("编译原理", courseName.text.toString())

        saveWidgetSnapshot(inflated, 400, 400, "widget-2x2-upcoming.png")
    }

    @Test
    fun widget2x2_tomorrowPreview_rendersTomorrowFirstCourse() {
        val data = WidgetScheduleData.TomorrowPreview(
            dateText = "10月20日 周二",
            weekText = "第 8 周",
            statusNotice = "今日课程已全部上完",
            subtitle = "明日课程预告",
            tomorrowCourses = listOf(tomorrowCourse),
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews2x2(context, data)
        val container = FrameLayout(context)
        val inflated = views.apply(context, container)

        val statusPill = inflated.findViewById<TextView>(R.id.widget_2x2_status_pill)
        val courseName = inflated.findViewById<TextView>(R.id.widget_2x2_course_name)
        val timeRange = inflated.findViewById<TextView>(R.id.widget_2x2_time_range)

        assertEquals("明日首节", statusPill.text.toString())
        assertEquals("操作系统", courseName.text.toString())
        assertTrue(timeRange.text.toString().contains("08:20"))

        saveWidgetSnapshot(inflated, 400, 400, "widget-2x2-tomorrow.png")
    }

    @Test
    fun widget2x2_allCompleted_rendersEmptyState() {
        val data = WidgetScheduleData.AllCompleted(
            dateText = "10月20日 周二",
            weekText = "第 8 周",
            message = "今日课程已全部上完",
            hint = "明日本学期均无课，好好休息吧~",
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews2x2(context, data)
        val container = FrameLayout(context)
        val inflated = views.apply(context, container)

        val courseContainer = inflated.findViewById<View>(R.id.widget_2x2_course_container)
        val emptyContainer = inflated.findViewById<View>(R.id.widget_2x2_empty_container)
        val emptyTitle = inflated.findViewById<TextView>(R.id.widget_2x2_empty_title)
        val emptySubtitle = inflated.findViewById<TextView>(R.id.widget_2x2_empty_subtitle)

        assertEquals(View.GONE, courseContainer.visibility)
        assertEquals(View.VISIBLE, emptyContainer.visibility)
        assertEquals("今日课程已全部上完", emptyTitle.text.toString())
        assertEquals("明日本学期均无课，好好休息吧~", emptySubtitle.text.toString())

        saveWidgetSnapshot(inflated, 400, 400, "widget-2x2-empty.png")
    }

    @Test
    fun widget4x4_weeklyGrid_rendersHeaderHighlightAndCourseCells() {
        val arrangementMon = CourseArrangement(
            id = 501L,
            semesterId = 1L,
            name = "离散数学",
            teacher = "陈老师",
            location = "知明 1-102",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = setOf(8),
            origin = CourseOrigin.Manual,
        )

        val arrangementWed = CourseArrangement(
            id = 502L,
            semesterId = 1L,
            name = "算法分析",
            teacher = "赵老师",
            location = "致广 2-301",
            dayOfWeek = 3,
            startSection = 3,
            endSection = 4,
            weeks = setOf(8),
            origin = CourseOrigin.Manual,
        )

        val grid = mapOf(
            (1 to 1) to arrangementMon,
            (3 to 2) to arrangementWed,
        )

        val weeklyData = WidgetWeeklyScheduleData(
            semesterName = "2025-2026学年秋季学期",
            weekTitle = "第 8 周 (本周课表)",
            todayDayOfWeek = 3, // Wednesday is today
            grid = grid,
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews4x4(context, weeklyData)
        assertNotNull(views)
        assertEquals(R.layout.widget_schedule_4x4, views.layoutId)

        val container = FrameLayout(context)
        val inflated = views.apply(context, container)
        assertNotNull(inflated)

        val weekTitle = inflated.findViewById<TextView>(R.id.widget_4x4_week_title)
        val semesterName = inflated.findViewById<TextView>(R.id.widget_4x4_active_semester)
        val monCell = inflated.findViewById<TextView>(R.id.widget_4x4_d1_s1)
        val wedCell = inflated.findViewById<TextView>(R.id.widget_4x4_d3_s2)
        val emptyCell = inflated.findViewById<TextView>(R.id.widget_4x4_d2_s1)

        assertEquals("第 8 周 (本周课表)", weekTitle.text.toString())
        assertEquals("2025-2026学年秋季学期", semesterName.text.toString())
        assertEquals("离散数学", monCell.text.toString())
        assertEquals("算法分析", wedCell.text.toString())
        assertEquals("", emptyCell.text.toString())

        saveWidgetSnapshot(inflated, 700, 700, "widget-4x4-weekly.png")
    }

    @Test
    fun widget4x4_emptySemester_rendersFallbackState() {
        val weeklyData = WidgetWeeklyScheduleData(
            semesterName = "移动课表",
            weekTitle = "未配置学期",
            todayDayOfWeek = 1,
            grid = emptyMap(),
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews4x4(context, weeklyData)
        val container = FrameLayout(context)
        val inflated = views.apply(context, container)

        val weekTitle = inflated.findViewById<TextView>(R.id.widget_4x4_week_title)
        assertEquals("未配置学期", weekTitle.text.toString())

        saveWidgetSnapshot(inflated, 700, 700, "widget-4x4-empty.png")
    }

    @Test
    fun widgetProviders_receiveBroadcasts_withoutCrash() {
        ScheduleWidgetNotifier.init(context)

        val provider2x2 = ScheduleAppWidget2x2Provider()
        provider2x2.onReceive(context, Intent(ScheduleAppWidget2x2Provider.ACTION_SCHEDULE_CHANGED))
        provider2x2.onReceive(context, Intent(ScheduleAppWidget2x2Provider.ACTION_REFRESH_WIDGET))

        val provider4x4 = ScheduleAppWidget4x4Provider()
        provider4x4.onReceive(context, Intent(ScheduleAppWidget4x4Provider.ACTION_SCHEDULE_CHANGED))
        provider4x4.onReceive(context, Intent(ScheduleAppWidget4x4Provider.ACTION_REFRESH_WIDGET))

        ScheduleWidgetNotifier.notifyDataChanged()
    }

    private fun saveWidgetSnapshot(view: View, width: Int, height: Int, filename: String) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        saveSyntheticScreenshot(filename, bitmap)
    }
}
