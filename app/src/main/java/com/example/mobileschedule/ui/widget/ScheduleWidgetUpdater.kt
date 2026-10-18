package com.example.mobileschedule.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import com.example.mobileschedule.MainActivity
import com.example.mobileschedule.R
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.WidgetCourseItem
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.data.preferences.WidgetClickAction
import com.example.mobileschedule.data.preferences.WidgetCustomizationOptions
import com.example.mobileschedule.data.preferences.WidgetSettingsStore
import com.example.mobileschedule.data.rules.ScheduleRules
import com.example.mobileschedule.ui.today.LunarCalendarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

object ScheduleWidgetUpdater {

    const val ACTION_OPEN_COURSE_DETAIL = "com.example.mobileschedule.action.OPEN_COURSE_DETAIL"
    const val ACTION_OPEN_SCHEDULE = "com.example.mobileschedule.action.OPEN_SCHEDULE"
    const val ACTION_OPEN_TODAY = "com.example.mobileschedule.action.OPEN_TODAY"
    const val EXTRA_COURSE_ID = "com.example.mobileschedule.extra.COURSE_ID"

    @Volatile
    var cachedData: WidgetScheduleData? = null

    suspend fun updateAllWidgets(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ) = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@withContext
        val componentName = ComponentName(context, ScheduleAppWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds == null || widgetIds.isEmpty()) return@withContext

        val options = WidgetSettingsStore(context).read()
        val data = loadWidgetData(context, today, now, showAllCourses = options.showAllCourses)
        cachedData = data

        for (widgetId in widgetIds) {
            val views = buildRemoteViews(context, data, options, widgetId)
            appWidgetManager.updateAppWidget(widgetId, views)
            if (options.showAllCourses) {
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_courses_list)
            }
        }
    }

    suspend fun loadWidgetData(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
        showAllCourses: Boolean = false,
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
                    showAllCourses = showAllCourses,
                )
            } else {
                ScheduleRules.widgetSchedule(null, emptyList(), today, now, showAllCourses = showAllCourses)
            }
        } catch (_: Exception) {
            ScheduleRules.widgetSchedule(null, emptyList(), today, now, showAllCourses = showAllCourses)
        }
    }

    fun buildRemoteViews(
        context: Context,
        data: WidgetScheduleData,
        options: WidgetCustomizationOptions = WidgetSettingsStore(context).read(),
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_4x2)
        val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colors = WidgetThemeHelper.resolveColors(options, isSystemDark)

        // 1. Dynamic background layer
        views.setImageViewResource(R.id.widget_background_image, colors.cornerDrawableRes)
        views.setInt(R.id.widget_background_image, "setColorFilter", colors.effectiveBgColor)
        views.setInt(R.id.widget_background_image, "setImageAlpha", colors.alphaInt)

        // 2. Click behavior
        val mainPendingIntent = if (options.clickAction == WidgetClickAction.OPEN_TODAY) {
            createTodayPendingIntent(context)
        } else {
            createSchedulePendingIntent(context)
        }
        views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_header, mainPendingIntent)

        // 3. Header text & colors
        val today = LocalDate.now()
        val lunarStr = if (options.showLunar) LunarCalendarHelper.getLunarDateString(today) else ""
        val headerDate = WidgetThemeHelper.formatHeaderDate(data.dateText, lunarStr, options.showLunar)
        views.setTextViewText(R.id.widget_header_date, headerDate)
        views.setTextViewText(R.id.widget_header_week, data.weekText)

        views.setTextColor(R.id.widget_header_date, colors.textPrimary)
        views.setTextColor(R.id.widget_header_week, colors.textAccent)
        views.setTextColor(R.id.widget_header_subtitle, colors.textSecondary)
        views.setInt(R.id.widget_header_icon, "setColorFilter", colors.textAccent)
        views.setInt(R.id.widget_header_launch_icon, "setColorFilter", colors.textSecondary)
        views.setInt(R.id.widget_divider, "setBackgroundColor", colors.dividerColor)

        // 4. Content states
        val effectiveData = if (data is WidgetScheduleData.TomorrowPreview && !options.showTomorrowPreview) {
            WidgetScheduleData.AllCompleted(
                dateText = data.dateText,
                weekText = data.weekText,
                message = "今日课程已全部上完",
                hint = "好好休息一下吧 · 点击查看完整课表",
            )
        } else {
            data
        }

        when (effectiveData) {
            is WidgetScheduleData.TodayCourses -> {
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                views.setTextViewText(R.id.widget_header_subtitle, effectiveData.subtitle)

                if (options.showAllCourses) {
                    views.setViewVisibility(R.id.widget_courses_list, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                    bindCoursesList(context, views, appWidgetId)
                } else {
                    views.setViewVisibility(R.id.widget_courses_list, View.GONE)
                    views.setViewVisibility(R.id.widget_courses_container, View.VISIBLE)

                    val maxRows = options.maxCourseRows
                    val remainingCount = effectiveData.totalRemainingCount
                    val moreCount = remainingCount - maxRows
                    val moreText = if (moreCount > 0) {
                        "+ 还有 $moreCount 节课程，点击查看完整课表"
                    } else null

                    renderCourseRows(
                        context = context,
                        views = views,
                        courses = effectiveData.courses,
                        options = options,
                        colors = colors,
                        moreText = moreText,
                        schedulePendingIntent = mainPendingIntent,
                    )
                }
            }

            is WidgetScheduleData.TomorrowPreview -> {
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                views.setTextViewText(R.id.widget_header_subtitle, effectiveData.subtitle)

                if (options.showAllCourses) {
                    views.setViewVisibility(R.id.widget_courses_list, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                    bindCoursesList(context, views, appWidgetId)
                } else {
                    views.setViewVisibility(R.id.widget_courses_list, View.GONE)
                    views.setViewVisibility(R.id.widget_courses_container, View.VISIBLE)

                    val maxRows = options.maxCourseRows
                    val moreCount = effectiveData.tomorrowCourses.size - maxRows
                    val moreText = if (moreCount > 0) {
                        "${effectiveData.statusNotice} · 明日共 ${effectiveData.tomorrowCourses.size} 节课"
                    } else null

                    renderCourseRows(
                        context = context,
                        views = views,
                        courses = effectiveData.tomorrowCourses,
                        options = options,
                        colors = colors,
                        moreText = moreText,
                        schedulePendingIntent = mainPendingIntent,
                    )
                }
            }

            is WidgetScheduleData.AllCompleted -> {
                views.setViewVisibility(R.id.widget_courses_list, View.GONE)
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "今日结束")
                views.setTextViewText(R.id.widget_empty_title, effectiveData.message)
                views.setTextViewText(R.id.widget_empty_subtitle, effectiveData.hint)
                views.setTextColor(R.id.widget_empty_title, colors.textPrimary)
                views.setTextColor(R.id.widget_empty_subtitle, colors.textSecondary)
                views.setOnClickPendingIntent(R.id.widget_empty_container, mainPendingIntent)
            }

            is WidgetScheduleData.BeforeSemester -> {
                views.setViewVisibility(R.id.widget_courses_list, View.GONE)
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "未开学")
                views.setTextViewText(R.id.widget_empty_title, effectiveData.message)
                views.setTextViewText(R.id.widget_empty_subtitle, "开学后将自动展示每日课表 · 点击查看")
                views.setTextColor(R.id.widget_empty_title, colors.textPrimary)
                views.setTextColor(R.id.widget_empty_subtitle, colors.textSecondary)
                views.setOnClickPendingIntent(R.id.widget_empty_container, mainPendingIntent)
            }

            is WidgetScheduleData.AfterSemester -> {
                views.setViewVisibility(R.id.widget_courses_list, View.GONE)
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "已结课")
                views.setTextViewText(R.id.widget_empty_title, effectiveData.message)
                views.setTextViewText(R.id.widget_empty_subtitle, "点击进入应用查看往期课表或配置新学期")
                views.setTextColor(R.id.widget_empty_title, colors.textPrimary)
                views.setTextColor(R.id.widget_empty_subtitle, colors.textSecondary)
                views.setOnClickPendingIntent(R.id.widget_empty_container, mainPendingIntent)
            }

            is WidgetScheduleData.NoSemester -> {
                views.setViewVisibility(R.id.widget_courses_list, View.GONE)
                views.setViewVisibility(R.id.widget_courses_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_header_subtitle, "未配置")
                views.setTextViewText(R.id.widget_empty_title, "尚未配置学期与课表")
                views.setTextViewText(R.id.widget_empty_subtitle, "点击进入应用导入或创建课表")
                views.setTextColor(R.id.widget_empty_title, colors.textPrimary)
                views.setTextColor(R.id.widget_empty_subtitle, colors.textSecondary)
                views.setOnClickPendingIntent(R.id.widget_empty_container, mainPendingIntent)
            }
        }

        return views
    }

    private fun bindCoursesList(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
    ) {
        val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_courses_list, serviceIntent)

        val clickIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_COURSE_DETAIL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            0,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        views.setPendingIntentTemplate(R.id.widget_courses_list, clickPendingIntent)
    }

    private fun renderCourseRows(
        context: Context,
        views: RemoteViews,
        courses: List<WidgetCourseItem>,
        options: WidgetCustomizationOptions,
        colors: WidgetResolvedColors,
        moreText: String?,
        schedulePendingIntent: PendingIntent,
    ) {
        val maxRows = options.maxCourseRows

        // Row 1
        val c1 = courses.getOrNull(0)
        if (c1 != null) {
            views.setViewVisibility(R.id.widget_course_row_1, View.VISIBLE)
            views.setTextViewText(R.id.widget_course_title_1, c1.title)
            val info1 = WidgetThemeHelper.formatCourseInfo(c1.timeText, c1.location, c1.teacher, options.showLocation, options.showTeacher)
            views.setTextViewText(R.id.widget_course_info_1, info1)
            val badgeText = if (c1.isOngoing) "● 正在上课" else if (c1.isTomorrow) "明日 ${c1.sectionText}" else c1.sectionText
            views.setTextViewText(R.id.widget_course_badge_1, badgeText)
            views.setTextColor(R.id.widget_course_title_1, colors.textPrimary)
            views.setTextColor(R.id.widget_course_info_1, colors.textSecondary)
            views.setTextColor(R.id.widget_course_badge_1, if (c1.isOngoing) (if (colors.isLightContent) 0xFF86EFAC.toInt() else 0xFF15803D.toInt()) else colors.badgeNormalText)
            views.setOnClickPendingIntent(
                R.id.widget_course_row_1,
                createCourseDetailPendingIntent(context, c1.id),
            )
        } else {
            views.setViewVisibility(R.id.widget_course_row_1, View.GONE)
        }

        // Row 2
        val c2 = courses.getOrNull(1)
        if (c2 != null) {
            views.setViewVisibility(R.id.widget_course_row_2, View.VISIBLE)
            views.setTextViewText(R.id.widget_course_title_2, c2.title)
            val info2 = WidgetThemeHelper.formatCourseInfo(c2.timeText, c2.location, c2.teacher, options.showLocation, options.showTeacher)
            views.setTextViewText(R.id.widget_course_info_2, info2)
            val badgeText = if (c2.isTomorrow) "明日 ${c2.sectionText}" else c2.sectionText
            views.setTextViewText(R.id.widget_course_badge_2, badgeText)
            views.setTextColor(R.id.widget_course_title_2, colors.textPrimary)
            views.setTextColor(R.id.widget_course_info_2, colors.textSecondary)
            views.setTextColor(R.id.widget_course_badge_2, colors.badgeNormalText)
            views.setOnClickPendingIntent(
                R.id.widget_course_row_2,
                createCourseDetailPendingIntent(context, c2.id),
            )
        } else {
            views.setViewVisibility(R.id.widget_course_row_2, View.GONE)
        }

        // Row 3
        val c3 = if (maxRows >= 3) courses.getOrNull(2) else null
        if (c3 != null) {
            views.setViewVisibility(R.id.widget_course_row_3, View.VISIBLE)
            views.setTextViewText(R.id.widget_course_title_3, c3.title)
            val info3 = WidgetThemeHelper.formatCourseInfo(c3.timeText, c3.location, c3.teacher, options.showLocation, options.showTeacher)
            views.setTextViewText(R.id.widget_course_info_3, info3)
            val badgeText = if (c3.isTomorrow) "明日 ${c3.sectionText}" else c3.sectionText
            views.setTextViewText(R.id.widget_course_badge_3, badgeText)
            views.setTextColor(R.id.widget_course_title_3, colors.textPrimary)
            views.setTextColor(R.id.widget_course_info_3, colors.textSecondary)
            views.setTextColor(R.id.widget_course_badge_3, colors.badgeNormalText)
            views.setOnClickPendingIntent(
                R.id.widget_course_row_3,
                createCourseDetailPendingIntent(context, c3.id),
            )
        } else {
            views.setViewVisibility(R.id.widget_course_row_3, View.GONE)
        }

        // More notice
        if (moreText != null) {
            views.setViewVisibility(R.id.widget_course_more, View.VISIBLE)
            views.setTextViewText(R.id.widget_course_more_text, moreText)
            views.setTextColor(R.id.widget_course_more_text, colors.textAccent)
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

    fun createTodayPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TODAY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
