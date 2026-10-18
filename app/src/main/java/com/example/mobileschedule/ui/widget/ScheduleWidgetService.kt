package com.example.mobileschedule.ui.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.mobileschedule.R
import com.example.mobileschedule.data.model.WidgetCourseItem
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.data.preferences.WidgetSettingsStore
import kotlinx.coroutines.runBlocking

/**
 * RemoteViewsService providing the scrollable list of courses for the 4x2 desktop widget.
 */
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleRemoteViewsFactory(applicationContext, intent)
    }
}

class ScheduleRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private var courses: List<WidgetCourseItem> = emptyList()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        val options = WidgetSettingsStore(context).read()
        val data = ScheduleWidgetUpdater.cachedData
            ?: runBlocking { ScheduleWidgetUpdater.loadWidgetData(context, showAllCourses = options.showAllCourses) }

        val effectiveData = if (data is WidgetScheduleData.TomorrowPreview && !options.showTomorrowPreview) {
            null
        } else {
            data
        }

        courses = when (effectiveData) {
            is WidgetScheduleData.TodayCourses -> effectiveData.courses
            is WidgetScheduleData.TomorrowPreview -> effectiveData.tomorrowCourses
            else -> emptyList()
        }
    }

    override fun onDestroy() {
        courses = emptyList()
    }

    override fun getCount(): Int = courses.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in courses.indices) return null
        val item = courses[position]
        val options = WidgetSettingsStore(context).read()
        val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colors = WidgetThemeHelper.resolveColors(options, isSystemDark)

        val views = RemoteViews(context.packageName, R.layout.widget_course_item)

        views.setTextViewText(R.id.widget_course_item_title, item.title)
        val info = WidgetThemeHelper.formatCourseInfo(
            timeText = item.timeText,
            location = item.location,
            teacher = item.teacher,
            showLocation = options.showLocation,
            showTeacher = options.showTeacher,
        )
        views.setTextViewText(R.id.widget_course_item_info, info)

        val badgeText = when {
            item.isOngoing -> "● 正在上课"
            item.isTomorrow -> "明日 ${item.sectionText}"
            item.isFinished -> "已下课"
            else -> item.sectionText
        }
        views.setTextViewText(R.id.widget_course_item_badge, badgeText)

        views.setTextColor(R.id.widget_course_item_title, colors.textPrimary)
        views.setTextColor(R.id.widget_course_item_info, colors.textSecondary)

        val badgeColor = when {
            item.isOngoing -> if (colors.isLightContent) 0xFF86EFAC.toInt() else 0xFF15803D.toInt()
            item.isFinished -> colors.textSecondary
            else -> colors.badgeNormalText
        }
        views.setTextColor(R.id.widget_course_item_badge, badgeColor)

        // Set click fill-in intent for opening course details
        val fillInIntent = Intent().apply {
            putExtra(ScheduleWidgetUpdater.EXTRA_COURSE_ID, item.id)
        }
        views.setOnClickFillInIntent(R.id.widget_course_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = courses.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
