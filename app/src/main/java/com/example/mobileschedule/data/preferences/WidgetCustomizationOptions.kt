package com.example.mobileschedule.data.preferences

enum class WidgetTextColorMode(val title: String) {
    AUTO("自动适配"),
    LIGHT("浅色文字（适合暗色桌面）"),
    DARK("深色文字（适合浅色桌面）"),
}

enum class WidgetCornerRadius(val title: String, val radiusDp: Int) {
    SQUARE("直角 (0dp)", 0),
    SMALL("小圆角 (10dp)", 10),
    STANDARD("标准 (18dp)", 18),
    LARGE("大圆角 (26dp)", 26),
}

enum class WidgetClickAction(val title: String) {
    OPEN_SCHEDULE("打开周课表视图"),
    OPEN_TODAY("打开今日课表视图"),
}

data class WidgetColorPreset(
    val name: String,
    val hex: String,
)

data class WidgetCustomizationOptions(
    val isFollowTheme: Boolean = true,
    val customColorHex: String = "#FFFFFF",
    val opacity: Float = 1.0f,
    val textColorMode: WidgetTextColorMode = WidgetTextColorMode.AUTO,
    val cornerRadius: WidgetCornerRadius = WidgetCornerRadius.STANDARD,
    val showLocation: Boolean = true,
    val showTeacher: Boolean = false,
    val showLunar: Boolean = false,
    val showTomorrowPreview: Boolean = true,
    val showAllCourses: Boolean = true,
    val maxCourseRows: Int = 2,
    val clickAction: WidgetClickAction = WidgetClickAction.OPEN_SCHEDULE,
) {
    companion object {
        val PRESETS = listOf(
            WidgetColorPreset("纯净白", "#FFFFFF"),
            WidgetColorPreset("极简灰", "#F1F5F9"),
            WidgetColorPreset("暖柔米", "#FEF3C7"),
            WidgetColorPreset("淡雅绿", "#ECFDF5"),
            WidgetColorPreset("冰川蓝", "#EFF6FF"),
            WidgetColorPreset("柔雾紫", "#F5F3FF"),
            WidgetColorPreset("曜石黑", "#0F172A"),
            WidgetColorPreset("深空灰", "#1E293B"),
            WidgetColorPreset("深海蓝", "#1E1B4B"),
            WidgetColorPreset("青翠绿", "#064E3B"),
            WidgetColorPreset("福师蓝", "#2563EB"),
            WidgetColorPreset("晚霞橙", "#EA580C"),
        )
    }
}
