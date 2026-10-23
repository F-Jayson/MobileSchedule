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
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.WeekPosition
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
        updateAllWidgets4x2(context, today, now)
        updateAllWidgets2x2(context, today, now)
        updateAllWidgets4x4(context, today, now)
    }

    suspend fun updateAllWidgets4x2(
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

    suspend fun updateAllWidgets2x2(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ) = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@withContext
        val componentName = ComponentName(context, ScheduleAppWidget2x2Provider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds == null || widgetIds.isEmpty()) return@withContext

        val options = WidgetSettingsStore(context).read()
        val data = loadWidgetData(context, today, now, showAllCourses = false)

        for (widgetId in widgetIds) {
            val views = buildRemoteViews2x2(context, data, options)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    suspend fun updateAllWidgets4x4(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ) = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@withContext
        val componentName = ComponentName(context, ScheduleAppWidget4x4Provider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds == null || widgetIds.isEmpty()) return@withContext

        val options = WidgetSettingsStore(context).read()
        val weeklyData = loadWeeklyScheduleData(context, today)

        for (widgetId in widgetIds) {
            val views = buildRemoteViews4x4(context, weeklyData, options)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    suspend fun loadWidgetData(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
        showAllCourses: Boolean = false,
    ): WidgetScheduleData = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
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

    fun buildRemoteViews2x2(
        context: Context,
        data: WidgetScheduleData,
        options: WidgetCustomizationOptions = WidgetSettingsStore(context).read(),
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_2x2)
        val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colors = WidgetThemeHelper.resolveColors(options, isSystemDark)

        // 1. Dynamic background layer
        views.setImageViewResource(R.id.widget_2x2_background_image, colors.cornerDrawableRes)
        views.setInt(R.id.widget_2x2_background_image, "setColorFilter", colors.effectiveBgColor)
        views.setInt(R.id.widget_2x2_background_image, "setImageAlpha", colors.alphaInt)

        // 2. Click behavior
        val mainPendingIntent = if (options.clickAction == WidgetClickAction.OPEN_TODAY) {
            createTodayPendingIntent(context)
        } else {
            createSchedulePendingIntent(context)
        }
        views.setOnClickPendingIntent(R.id.widget_2x2_root, mainPendingIntent)

        // 3. Header colors and texts
        views.setTextViewText(R.id.widget_2x2_header_info, data.weekText)
        views.setTextColor(R.id.widget_2x2_header_info, colors.textSecondary)
        views.setInt(R.id.widget_2x2_header_icon, "setColorFilter", colors.textAccent)
        views.setInt(R.id.widget_2x2_launch_icon, "setColorFilter", colors.textSecondary)
        views.setInt(R.id.widget_2x2_divider, "setBackgroundColor", colors.dividerColor)

        val today = LocalDate.now()
        val lunarStr = if (options.showLunar) LunarCalendarHelper.getLunarDateString(today) else ""
        val headerDate = WidgetThemeHelper.formatHeaderDate(data.dateText, lunarStr, options.showLunar)
        views.setTextViewText(R.id.widget_2x2_date_text, headerDate)
        views.setTextColor(R.id.widget_2x2_date_text, colors.textSecondary)

        // Course & empty colors
        views.setTextColor(R.id.widget_2x2_course_name, colors.textPrimary)
        views.setTextColor(R.id.widget_2x2_time_range, colors.textAccent)
        views.setTextColor(R.id.widget_2x2_location_teacher, colors.textSecondary)
        views.setTextColor(R.id.widget_2x2_empty_title, colors.textPrimary)
        views.setTextColor(R.id.widget_2x2_empty_subtitle, colors.textSecondary)

        // 4. Focus course or empty state
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
                val ongoing = effectiveData.courses.firstOrNull { it.isOngoing }
                val focus = ongoing ?: effectiveData.courses.firstOrNull()
                if (focus != null) {
                    views.setViewVisibility(R.id.widget_2x2_course_container, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_2x2_empty_container, View.GONE)

                    if (focus.isOngoing) {
                        views.setTextViewText(R.id.widget_2x2_status_pill, "正在上课")
                        views.setTextColor(R.id.widget_2x2_status_pill, if (colors.isLightContent) 0xFF86EFAC.toInt() else 0xFF15803D.toInt())
                    } else {
                        views.setTextViewText(R.id.widget_2x2_status_pill, "下一节课")
                        views.setTextColor(R.id.widget_2x2_status_pill, colors.textAccent)
                    }

                    views.setTextViewText(R.id.widget_2x2_course_name, focus.title)
                    views.setTextViewText(R.id.widget_2x2_time_range, "${focus.timeText} (${focus.sectionText})")

                    val info = WidgetThemeHelper.formatCourseInfo("", focus.location, focus.teacher, options.showLocation, options.showTeacher).trim().removePrefix("·").trim()
                    views.setTextViewText(R.id.widget_2x2_location_teacher, info.ifBlank { "未指定地点" })
                    views.setOnClickPendingIntent(R.id.widget_2x2_course_container, createCourseDetailPendingIntent(context, focus.id))
                } else {
                    render2x2Empty(views, "今日无课", "好好休息一下吧", "无课", colors, mainPendingIntent)
                }
            }

            is WidgetScheduleData.TomorrowPreview -> {
                val firstTomorrow = effectiveData.tomorrowCourses.firstOrNull()
                if (firstTomorrow != null) {
                    views.setViewVisibility(R.id.widget_2x2_course_container, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_2x2_empty_container, View.GONE)

                    views.setTextViewText(R.id.widget_2x2_status_pill, "明日首节")
                    views.setTextColor(R.id.widget_2x2_status_pill, colors.badgeNormalText)

                    views.setTextViewText(R.id.widget_2x2_course_name, firstTomorrow.title)
                    views.setTextViewText(R.id.widget_2x2_time_range, "${firstTomorrow.timeText} (${firstTomorrow.sectionText})")

                    val info = WidgetThemeHelper.formatCourseInfo("", firstTomorrow.location, firstTomorrow.teacher, options.showLocation, options.showTeacher).trim().removePrefix("·").trim()
                    views.setTextViewText(R.id.widget_2x2_location_teacher, info.ifBlank { "未指定地点" })
                    views.setOnClickPendingIntent(R.id.widget_2x2_course_container, createCourseDetailPendingIntent(context, firstTomorrow.id))
                } else {
                    render2x2Empty(views, effectiveData.statusNotice, "好好休息一下吧", "今日结束", colors, mainPendingIntent)
                }
            }

            is WidgetScheduleData.AllCompleted -> {
                render2x2Empty(views, effectiveData.message, effectiveData.hint, "今日结束", colors, mainPendingIntent)
            }

            is WidgetScheduleData.BeforeSemester -> {
                render2x2Empty(views, effectiveData.message, "开学后将自动展示每日课表", "未开学", colors, mainPendingIntent)
            }

            is WidgetScheduleData.AfterSemester -> {
                render2x2Empty(views, effectiveData.message, "本学期课程已全部结课", "已结课", colors, mainPendingIntent)
            }

            is WidgetScheduleData.NoSemester -> {
                render2x2Empty(views, "尚未配置学期与课表", "点击进入应用导入或创建课表", "未配置", colors, mainPendingIntent)
            }
        }

        return views
    }

    private fun render2x2Empty(
        views: RemoteViews,
        title: String,
        subtitle: String,
        pillText: String,
        colors: WidgetResolvedColors,
        mainPendingIntent: PendingIntent,
    ) {
        views.setViewVisibility(R.id.widget_2x2_course_container, View.GONE)
        views.setViewVisibility(R.id.widget_2x2_empty_container, View.VISIBLE)
        views.setTextViewText(R.id.widget_2x2_status_pill, pillText)
        views.setTextColor(R.id.widget_2x2_status_pill, colors.textSecondary)
        views.setTextViewText(R.id.widget_2x2_empty_title, title)
        views.setTextViewText(R.id.widget_2x2_empty_subtitle, subtitle)
        views.setOnClickPendingIntent(R.id.widget_2x2_empty_container, mainPendingIntent)
    }

    suspend fun loadWeeklyScheduleData(
        context: Context,
        today: LocalDate = LocalDate.now(),
    ): WidgetWeeklyScheduleData = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val activeId = database.semesterDao().getActiveSemesterId()
            if (activeId != null) {
                val semesterDetails = database.semesterDao().getSemester(activeId)
                val semesterModel = semesterDetails?.toModel()
                val config = semesterModel?.config
                val courseRows = database.courseDao().getSemesterCourses(activeId)
                val arrangements = courseRows.map { it.toArrangement() }

                if (config != null) {
                    when (val position = ScheduleRules.weekPosition(config, today)) {
                        is WeekPosition.InSemester -> {
                            val currentWeek = position.week
                            val weekArrangements = arrangements.filter { currentWeek in it.weeks }
                            val grid = mutableMapOf<Pair<Int, Int>, CourseArrangement>()

                            for (day in 1..7) {
                                val dayCourses = weekArrangements.filter { it.dayOfWeek == day }
                                for (slot in 1..5) {
                                    val match = dayCourses.firstOrNull { courseOverlapsSlot(it.startSection, it.endSection, slot) }
                                    if (match != null) {
                                        grid[day to slot] = match
                                    }
                                }
                            }

                            WidgetWeeklyScheduleData(
                                semesterName = semesterModel.displayName,
                                weekTitle = "第 $currentWeek 周 (本周课表)",
                                todayDayOfWeek = today.dayOfWeek.value,
                                grid = grid,
                            )
                        }
                        is WeekPosition.BeforeSemester -> {
                            WidgetWeeklyScheduleData(
                                semesterName = semesterModel.displayName,
                                weekTitle = "尚未开学",
                                todayDayOfWeek = today.dayOfWeek.value,
                            )
                        }
                        is WeekPosition.AfterSemester -> {
                            WidgetWeeklyScheduleData(
                                semesterName = semesterModel.displayName,
                                weekTitle = "学期已结课",
                                todayDayOfWeek = today.dayOfWeek.value,
                            )
                        }
                    }
                } else {
                    WidgetWeeklyScheduleData(
                        semesterName = semesterModel?.displayName ?: "移动课表",
                        weekTitle = "课表未配置时间",
                        todayDayOfWeek = today.dayOfWeek.value,
                    )
                }
            } else {
                WidgetWeeklyScheduleData(
                    semesterName = "移动课表",
                    weekTitle = "未配置学期",
                    todayDayOfWeek = today.dayOfWeek.value,
                )
            }
        } catch (_: Exception) {
            WidgetWeeklyScheduleData(
                semesterName = "移动课表",
                weekTitle = "课表数据加载中",
                todayDayOfWeek = today.dayOfWeek.value,
            )
        }
    }

    private fun courseOverlapsSlot(startSection: Int, endSection: Int, slot: Int): Boolean {
        val (slotStart, slotEnd) = when (slot) {
            1 -> 1 to 2
            2 -> 3 to 4
            3 -> 5 to 6
            4 -> 7 to 8
            else -> 9 to 99
        }
        return maxOf(startSection, slotStart) <= minOf(endSection, slotEnd)
    }

    fun buildRemoteViews4x4(
        context: Context,
        data: WidgetWeeklyScheduleData,
        options: WidgetCustomizationOptions = WidgetSettingsStore(context).read(),
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_4x4)
        val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colors = WidgetThemeHelper.resolveColors(options, isSystemDark)

        // 1. Dynamic background layer
        views.setImageViewResource(R.id.widget_4x4_background_image, colors.cornerDrawableRes)
        views.setInt(R.id.widget_4x4_background_image, "setColorFilter", colors.effectiveBgColor)
        views.setInt(R.id.widget_4x4_background_image, "setImageAlpha", colors.alphaInt)

        // 2. Click behavior
        val mainPendingIntent = if (options.clickAction == WidgetClickAction.OPEN_TODAY) {
            createTodayPendingIntent(context)
        } else {
            createSchedulePendingIntent(context)
        }
        views.setOnClickPendingIntent(R.id.widget_4x4_root, mainPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_4x4_header, mainPendingIntent)

        // 3. Header colors and texts
        views.setTextViewText(R.id.widget_4x4_week_title, data.weekTitle)
        views.setTextColor(R.id.widget_4x4_week_title, colors.textPrimary)
        views.setTextViewText(R.id.widget_4x4_active_semester, data.semesterName)
        views.setTextColor(R.id.widget_4x4_active_semester, colors.textSecondary)

        views.setInt(R.id.widget_4x4_header_icon, "setColorFilter", colors.textAccent)
        views.setInt(R.id.widget_4x4_launch_icon, "setColorFilter", colors.textSecondary)
        views.setInt(R.id.widget_4x4_divider, "setBackgroundColor", colors.dividerColor)

        // 4. Ruler column
        views.setTextColor(R.id.widget_4x4_ruler_header, colors.textSecondary)
        views.setTextColor(R.id.widget_4x4_ruler_1, colors.textSecondary)
        views.setTextColor(R.id.widget_4x4_ruler_2, colors.textSecondary)
        views.setTextColor(R.id.widget_4x4_ruler_3, colors.textSecondary)
        views.setTextColor(R.id.widget_4x4_ruler_4, colors.textSecondary)
        views.setTextColor(R.id.widget_4x4_ruler_5, colors.textSecondary)

        // 5. Day columns & slots
        val colIds = intArrayOf(
            R.id.widget_4x4_col_1, R.id.widget_4x4_col_2, R.id.widget_4x4_col_3,
            R.id.widget_4x4_col_4, R.id.widget_4x4_col_5, R.id.widget_4x4_col_6, R.id.widget_4x4_col_7
        )
        val headerIds = intArrayOf(
            R.id.widget_4x4_header_1, R.id.widget_4x4_header_2, R.id.widget_4x4_header_3,
            R.id.widget_4x4_header_4, R.id.widget_4x4_header_5, R.id.widget_4x4_header_6, R.id.widget_4x4_header_7
        )

        val cellMap = mapOf(
            (1 to 1) to R.id.widget_4x4_d1_s1, (1 to 2) to R.id.widget_4x4_d1_s2, (1 to 3) to R.id.widget_4x4_d1_s3, (1 to 4) to R.id.widget_4x4_d1_s4, (1 to 5) to R.id.widget_4x4_d1_s5,
            (2 to 1) to R.id.widget_4x4_d2_s1, (2 to 2) to R.id.widget_4x4_d2_s2, (2 to 3) to R.id.widget_4x4_d2_s3, (2 to 4) to R.id.widget_4x4_d2_s4, (2 to 5) to R.id.widget_4x4_d2_s5,
            (3 to 1) to R.id.widget_4x4_d3_s1, (3 to 2) to R.id.widget_4x4_d3_s2, (3 to 3) to R.id.widget_4x4_d3_s3, (3 to 4) to R.id.widget_4x4_d3_s4, (3 to 5) to R.id.widget_4x4_d3_s5,
            (4 to 1) to R.id.widget_4x4_d4_s1, (4 to 2) to R.id.widget_4x4_d4_s2, (4 to 3) to R.id.widget_4x4_d4_s3, (4 to 4) to R.id.widget_4x4_d4_s4, (4 to 5) to R.id.widget_4x4_d4_s5,
            (5 to 1) to R.id.widget_4x4_d5_s1, (5 to 2) to R.id.widget_4x4_d5_s2, (5 to 3) to R.id.widget_4x4_d5_s3, (5 to 4) to R.id.widget_4x4_d5_s4, (5 to 5) to R.id.widget_4x4_d5_s5,
            (6 to 1) to R.id.widget_4x4_d6_s1, (6 to 2) to R.id.widget_4x4_d6_s2, (6 to 3) to R.id.widget_4x4_d6_s3, (6 to 4) to R.id.widget_4x4_d6_s4, (6 to 5) to R.id.widget_4x4_d6_s5,
            (7 to 1) to R.id.widget_4x4_d7_s1, (7 to 2) to R.id.widget_4x4_d7_s2, (7 to 3) to R.id.widget_4x4_d7_s3, (7 to 4) to R.id.widget_4x4_d7_s4, (7 to 5) to R.id.widget_4x4_d7_s5,
        )

        for (day in 1..7) {
            val isToday = day == data.todayDayOfWeek
            val colId = colIds[day - 1]
            val headerId = headerIds[day - 1]

            if (isToday) {
                views.setInt(colId, "setBackgroundResource", R.drawable.bg_widget_today_highlight)
                views.setTextColor(headerId, colors.textAccent)
            } else {
                views.setInt(colId, "setBackgroundResource", 0)
                views.setTextColor(headerId, colors.textSecondary)
            }

            for (slot in 1..5) {
                val cellId = cellMap[day to slot] ?: continue
                val course = data.grid[day to slot]
                if (course != null) {
                    views.setInt(cellId, "setBackgroundResource", R.drawable.bg_widget_mini_card)
                    views.setTextViewText(cellId, course.name)
                    views.setTextColor(cellId, colors.textPrimary)
                    views.setOnClickPendingIntent(cellId, createCourseDetailPendingIntent(context, course.id))
                } else {
                    views.setInt(cellId, "setBackgroundResource", 0)
                    views.setTextViewText(cellId, "")
                    views.setOnClickPendingIntent(cellId, mainPendingIntent)
                }
            }
        }

        return views
    }
}

data class WidgetWeeklyScheduleData(
    val semesterName: String,
    val weekTitle: String,
    val todayDayOfWeek: Int,
    val grid: Map<Pair<Int, Int>, CourseArrangement> = emptyMap(),
)
