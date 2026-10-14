package com.example.mobileschedule.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.mobileschedule.MainActivity
import com.example.mobileschedule.R
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.WidgetCourseItem
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.data.rules.ScheduleRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

object ScheduleWidgetUpdater {

    const val ACTION_OPEN_COURSE_DETAIL = "com.example.mobileschedule.action.OPEN_COURSE_DETAIL"
    const val ACTION_OPEN_SCHEDULE = "com.example.mobileschedule.action.OPEN_SCHEDULE"
    const val EXTRA_COURSE_ID = "com.example.mobileschedule.extra.COURSE_ID"

    suspend fun updateAllWidgets(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ) = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@withContext
        val componentName = ComponentName(context, ScheduleAppWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds == null || widgetIds.isEmpty()) return@withContext

        val data = loadWidgetData(context, today, now)

        for (widgetId in widgetIds) {
            val views = buildRemoteViews(context, data)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    suspend fun loadWidgetData(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ): WidgetScheduleData = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.open(context)
            val activeId = database.semesterDao().getActiveSemesterId()
            if (activeId != null) {
                val semesterDetails = database.semesterDao().getSemester(activeId)
                val courseRows = database.courseDao().getSemesterCourses(activeId)
                ScheduleRules.widgetSchedule(
                    semester = semesterDetails?.toModel(),
                    arrangements = courseRows.map { it.toArrangement() },
                    today = today,
                    now = now,
                )
            } else {
                ScheduleRules.widgetSchedule(null, emptyList(), today, now)
            }
        } catch (_: Exception) {
            ScheduleRules.widgetSchedule(null, emptyList(), today, now)
        }
    }

    fun buildRemoteViews(context: Context, data: WidgetScheduleData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_4x2)
        val schedulePendingIntent = createSchedulePendingIntent(context)

        // 1. Root & Header click opens schedule
        views.setOnClickPendingIntent(R.id.widget_root, schedulePendingIntent)
        views.setOnClickPendingIntent(R.id.widget_header, schedulePendingIntent)

        // 2. Header text
        views.setTextViewText(R.id.widget_header_date, data.dateText)
        views.setTextViewText(R.id.widget_header_week, data.weekText)

        // 3. Render content based on state
        when (data) {
            is WidgetScheduleData.TodayCourses -> {
                views.setViewVisibility(R.id.widget_courses_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                views.setTextViewText(R.id.widget_header_subtitle, data.subtitle)

                renderCourseRows(
                    context = context,
                    views = views,
                    courses = data.courses,
                    moreText = if (data.totalRemainingCount > 2) {
                        "+ 还有 ${data.totalRemainingCount - 2} 节课程，点击查看完整课表"
                    } else null,
                    schedulePendingIntent = schedulePendingIntent,
                )
            }

            is WidgetScheduleData.TomorrowPreview -> {
                views.setViewVisibility(R.id.widget_courses_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                views.setTextViewText(R.id.widget_header_subtitle, data.subtitle)

                renderCourseRows(
                    context = context,
                    views = views,
                    courses = data.tomorrowCourses,
                    moreText = "${data.statusNotice} · 查看明日全部 ${data.tomorrowCourses.size} 节课",
                    schedulePendingIntent = schedulePendingIntent,
                )
            }

            is WidgetScheduleData.AllCompleted -> {
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "今日结束")
                views.setTextViewText(R.id.widget_empty_title, data.message)
                views.setTextViewText(R.id.widget_empty_subtitle, data.hint)
                views.setOnClickPendingIntent(R.id.widget_empty_container, schedulePendingIntent)
            }

            is WidgetScheduleData.BeforeSemester -> {
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "未开学")
                views.setTextViewText(R.id.widget_empty_title, data.message)
                views.setTextViewText(R.id.widget_empty_subtitle, "开学后将自动展示每日课表 · 点击查看")
                views.setOnClickPendingIntent(R.id.widget_empty_container, schedulePendingIntent)
            }

            is WidgetScheduleData.AfterSemester -> {
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "已结课")
                views.setTextViewText(R.id.widget_empty_title, data.message)
                views.setTextViewText(R.id.widget_empty_subtitle, "点击进入应用查看往期课表或配置新学期")
                views.setOnClickPendingIntent(R.id.widget_empty_container, schedulePendingIntent)
            }

            is WidgetScheduleData.NoSemester -> {
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "未配置")
                views.setTextViewText(R.id.widget_empty_title, "尚未配置学期与课表")
                views.setTextViewText(R.id.widget_empty_subtitle, "点击进入应用导入或创建课表")
                views.setOnClickPendingIntent(R.id.widget_empty_container, schedulePendingIntent)
            }
        }

        return views
    }

    private fun renderCourseRows(
        context: Context,
        views: RemoteViews,
        courses: List<WidgetCourseItem>,
        moreText: String?,
        schedulePendingIntent: PendingIntent,
    ) {
        val c1 = courses.getOrNull(0)
        if (c1 != null) {
            views.setViewVisibility(R.id.widget_course_row_1, View.VISIBLE)
            views.setTextViewText(R.id.widget_course_title_1, c1.title)
            views.setTextViewText(R.id.widget_course_info_1, "${c1.timeText} · ${c1.location}")
            val badgeText = if (c1.isOngoing) "● 正在上课" else if (c1.isTomorrow) "明日 ${c1.sectionText}" else c1.sectionText
            views.setTextViewText(R.id.widget_course_badge_1, badgeText)
            views.setOnClickPendingIntent(
                R.id.widget_course_row_1,
                createCourseDetailPendingIntent(context, c1.id),
            )
        } else {
            views.setViewVisibility(R.id.widget_course_row_1, View.GONE)
        }

        val c2 = courses.getOrNull(1)
        if (c2 != null) {
            views.setViewVisibility(R.id.widget_course_row_2, View.VISIBLE)
            views.setTextViewText(R.id.widget_course_title_2, c2.title)
            views.setTextViewText(R.id.widget_course_info_2, "${c2.timeText} · ${c2.location}")
            val badgeText = if (c2.isTomorrow) "明日 ${c2.sectionText}" else c2.sectionText
            views.setTextViewText(R.id.widget_course_badge_2, badgeText)
            views.setOnClickPendingIntent(
                R.id.widget_course_row_2,
                createCourseDetailPendingIntent(context, c2.id),
            )
        } else {
            views.setViewVisibility(R.id.widget_course_row_2, View.GONE)
        }

        if (moreText != null) {
            views.setViewVisibility(R.id.widget_course_more, View.VISIBLE)
            views.setTextViewText(R.id.widget_course_more_text, moreText)
            views.setOnClickPendingIntent(R.id.widget_course_more, schedulePendingIntent)
        } else {
            views.setViewVisibility(R.id.widget_course_more, View.GONE)
        }
    }

    fun createCourseDetailPendingIntent(context: Context, courseId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_COURSE_DETAIL
            putExtra(EXTRA_COURSE_ID, courseId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            courseId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createSchedulePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SCHEDULE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
