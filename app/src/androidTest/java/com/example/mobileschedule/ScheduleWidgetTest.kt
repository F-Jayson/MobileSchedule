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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.mobileschedule.data.model.WidgetCourseItem
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.ui.widget.ScheduleAppWidgetProvider
import com.example.mobileschedule.ui.widget.ScheduleWidgetUpdater
import com.example.mobileschedule.ui.widget.ScheduleWidgetWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ScheduleWidgetTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val courseItem1 = WidgetCourseItem(
        id = 101L,
        title = "高等数学",
        sectionText = "第1-2节",
        timeText = "08:00 - 09:35",
        location = "知明 1-101",
        teacher = "李老师",
        isOngoing = true,
        isTomorrow = false,
    )

    private val courseItem2 = WidgetCourseItem(
        id = 102L,
        title = "大学物理",
        sectionText = "第3-4节",
        timeText = "10:00 - 11:35",
        location = "知明 2-203",
        teacher = "王老师",
        isOngoing = false,
        isTomorrow = false,
    )

    private val tomorrowCourseItem = WidgetCourseItem(
        id = 201L,
        title = "无机化学",
        sectionText = "第1-2节",
        timeText = "08:00 - 09:35",
        location = "知达 3-305",
        teacher = "张老师",
        isOngoing = false,
        isTomorrow = true,
    )

    @Test
    fun widgetLayout_todayCourses_scrollableListMode_rendersListView() {
        val data = WidgetScheduleData.TodayCourses(
            dateText = "10月15日 周四",
            weekText = "第 7 周",
            subtitle = "正在上课",
            courses = listOf(courseItem1, courseItem2),
            totalRemainingCount = 2,
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews(context, data)
        assertNotNull(views)
        assertEquals(R.layout.widget_schedule_4x2, views.layoutId)

        val container = FrameLayout(context)
        val inflated = views.apply(context, container)
        assertNotNull(inflated)

        val dateView = inflated.findViewById<TextView>(R.id.widget_header_date)
        val weekView = inflated.findViewById<TextView>(R.id.widget_header_week)
        val subtitleView = inflated.findViewById<TextView>(R.id.widget_header_subtitle)

        assertEquals("10月15日 周四", dateView.text.toString())
        assertEquals("第 7 周", weekView.text.toString())
        assertEquals("正在上课", subtitleView.text.toString())

        assertEquals(View.VISIBLE, inflated.findViewById<View>(R.id.widget_courses_list).visibility)
        assertEquals(View.GONE, inflated.findViewById<View>(R.id.widget_courses_container).visibility)
        assertEquals(View.GONE, inflated.findViewById<View>(R.id.widget_empty_container).visibility)

        saveWidgetSnapshot(inflated, "widget-schedule-today-list.png")
    }

    @Test
    fun widgetLayout_todayCourses_staticRowsMode_rendersCardsAndOngoingBadge() {
        val data = WidgetScheduleData.TodayCourses(
            dateText = "10月15日 周四",
            weekText = "第 7 周",
            subtitle = "正在上课",
            courses = listOf(courseItem1, courseItem2),
            totalRemainingCount = 2,
        )

        val options = com.example.mobileschedule.data.preferences.WidgetCustomizationOptions(showAllCourses = false)
        val views = ScheduleWidgetUpdater.buildRemoteViews(context, data, options)
        assertNotNull(views)
        assertEquals(R.layout.widget_schedule_4x2, views.layoutId)

        val container = FrameLayout(context)
        val inflated = views.apply(context, container)
        assertNotNull(inflated)

        val course1Title = inflated.findViewById<TextView>(R.id.widget_course_title_1)
        val course1Badge = inflated.findViewById<TextView>(R.id.widget_course_badge_1)
        val course2Title = inflated.findViewById<TextView>(R.id.widget_course_title_2)

        assertEquals("高等数学", course1Title.text.toString())
        assertEquals("● 正在上课", course1Badge.text.toString())
        assertEquals("大学物理", course2Title.text.toString())

        assertEquals(View.GONE, inflated.findViewById<View>(R.id.widget_courses_list).visibility)
        assertEquals(View.VISIBLE, inflated.findViewById<View>(R.id.widget_courses_container).visibility)
        assertEquals(View.GONE, inflated.findViewById<View>(R.id.widget_empty_container).visibility)

        saveWidgetSnapshot(inflated, "widget-schedule-today.png")
    }

    @Test
    fun widgetLayout_tomorrowPreview_rendersTomorrowBadgeAndNotice() {
        val data = WidgetScheduleData.TomorrowPreview(
            dateText = "10月15日 周四",
            weekText = "第 7 周",
            statusNotice = "今日课程已全部上完",
            subtitle = "明日课程预告",
            tomorrowCourses = listOf(tomorrowCourseItem),
        )

        val options = com.example.mobileschedule.data.preferences.WidgetCustomizationOptions(showAllCourses = false)
        val views = ScheduleWidgetUpdater.buildRemoteViews(context, data, options)
        val container = FrameLayout(context)
        val inflated = views.apply(context, container)

        val subtitleView = inflated.findViewById<TextView>(R.id.widget_header_subtitle)
        val course1Title = inflated.findViewById<TextView>(R.id.widget_course_title_1)
        val course1Badge = inflated.findViewById<TextView>(R.id.widget_course_badge_1)

        assertEquals("明日课程预告", subtitleView.text.toString())
        assertEquals("无机化学", course1Title.text.toString())
        assertTrue(course1Badge.text.toString().contains("明日"))

        saveWidgetSnapshot(inflated, "widget-schedule-tomorrow.png")
    }

    @Test
    fun widgetLayout_allCompleted_rendersEmptyStateMessage() {
        val data = WidgetScheduleData.AllCompleted(
            dateText = "10月15日 周四",
            weekText = "第 7 周",
            message = "今日课程已全部上完",
            hint = "明日本学期均无课，好好休息吧~",
        )

        val views = ScheduleWidgetUpdater.buildRemoteViews(context, data)
        val container = FrameLayout(context)
        val inflated = views.apply(context, container)

        val coursesContainer = inflated.findViewById<View>(R.id.widget_courses_container)
        val emptyContainer = inflated.findViewById<View>(R.id.widget_empty_container)
        val emptyTitle = inflated.findViewById<TextView>(R.id.widget_empty_title)
        val emptySubtitle = inflated.findViewById<TextView>(R.id.widget_empty_subtitle)

        assertEquals(View.GONE, coursesContainer.visibility)
        assertEquals(View.VISIBLE, emptyContainer.visibility)
        assertEquals("今日课程已全部上完", emptyTitle.text.toString())
        assertTrue(emptySubtitle.text.toString().contains("好好休息"))

        saveWidgetSnapshot(inflated, "widget-schedule-empty.png")
    }

    @Test
    fun widgetProvider_receivesRefreshBroadcast_withoutCrash() {
        val provider = ScheduleAppWidgetProvider()
        val intent = Intent(ScheduleAppWidgetProvider.ACTION_SCHEDULE_CHANGED)
        provider.onReceive(context, intent)
    }

    @Test
    fun widgetWorker_enqueuesPeriodicWorkInWorkManager() {
        ScheduleWidgetWorker.enqueuePeriodicWork(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ScheduleWidgetWorker.WORK_NAME)
            .get()

        assertNotNull(workInfos)
        assertTrue(workInfos.isNotEmpty())
        val state = workInfos.first().state
        assertTrue(state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING)
    }

    private fun saveWidgetSnapshot(view: View, filename: String) {
        val width = 720
        val height = 360
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
