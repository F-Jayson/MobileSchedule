package com.example.mobileschedule.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WidgetSettingsStore(context: Context, preferencesName: String = "widget_customization_preferences") {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    companion object {
        private val _optionsFlow = MutableStateFlow(WidgetCustomizationOptions())
        @Volatile private var isInitialized = false
    }

    init {
        if (!isInitialized) {
            _optionsFlow.value = read()
            isInitialized = true
        }
    }

    val optionsFlow: StateFlow<WidgetCustomizationOptions> = _optionsFlow.asStateFlow()

    fun read(): WidgetCustomizationOptions {
        val isFollowTheme = preferences.getBoolean("is_follow_theme", true)
        val customColorHex = preferences.getString("custom_color_hex", "#FFFFFF") ?: "#FFFFFF"
        val opacity = preferences.getFloat("opacity", 1.0f)
        val textColorModeName = preferences.getString("text_color_mode", WidgetTextColorMode.AUTO.name) ?: WidgetTextColorMode.AUTO.name
        val textColorMode = runCatching { WidgetTextColorMode.valueOf(textColorModeName) }.getOrDefault(WidgetTextColorMode.AUTO)
        val cornerRadiusName = preferences.getString("corner_radius", WidgetCornerRadius.STANDARD.name) ?: WidgetCornerRadius.STANDARD.name
        val cornerRadius = runCatching { WidgetCornerRadius.valueOf(cornerRadiusName) }.getOrDefault(WidgetCornerRadius.STANDARD)
        val showLocation = preferences.getBoolean("show_location", true)
        val showTeacher = preferences.getBoolean("show_teacher", false)
        val showLunar = preferences.getBoolean("show_lunar", false)
        val showTomorrowPreview = preferences.getBoolean("show_tomorrow_preview", true)
        val showAllCourses = preferences.getBoolean("show_all_courses", true)
        val maxCourseRows = preferences.getInt("max_course_rows", 2)
        val clickActionName = preferences.getString("click_action", WidgetClickAction.OPEN_SCHEDULE.name) ?: WidgetClickAction.OPEN_SCHEDULE.name
        val clickAction = runCatching { WidgetClickAction.valueOf(clickActionName) }.getOrDefault(WidgetClickAction.OPEN_SCHEDULE)

        return WidgetCustomizationOptions(
            isFollowTheme = isFollowTheme,
            customColorHex = customColorHex,
            opacity = opacity.coerceIn(0f, 1f),
            textColorMode = textColorMode,
            cornerRadius = cornerRadius,
            showLocation = showLocation,
            showTeacher = showTeacher,
            showLunar = showLunar,
            showTomorrowPreview = showTomorrowPreview,
            showAllCourses = showAllCourses,
            maxCourseRows = maxCourseRows.coerceIn(2, 3),
            clickAction = clickAction,
        )
    }

    fun save(options: WidgetCustomizationOptions): Boolean {
        val ok = preferences.edit()
            .putBoolean("is_follow_theme", options.isFollowTheme)
            .putString("custom_color_hex", options.customColorHex)
            .putFloat("opacity", options.opacity)
            .putString("text_color_mode", options.textColorMode.name)
            .putString("corner_radius", options.cornerRadius.name)
            .putBoolean("show_location", options.showLocation)
            .putBoolean("show_teacher", options.showTeacher)
            .putBoolean("show_lunar", options.showLunar)
            .putBoolean("show_tomorrow_preview", options.showTomorrowPreview)
            .putBoolean("show_all_courses", options.showAllCourses)
            .putInt("max_course_rows", options.maxCourseRows)
            .putString("click_action", options.clickAction.name)
            .commit()

        if (ok) {
            _optionsFlow.value = options
        }
        return ok
    }

    fun reset(): Boolean {
        return save(WidgetCustomizationOptions())
    }
}
