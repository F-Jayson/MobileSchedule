package com.example.mobileschedule.data.model

/**
 * Domain model representing a single course item displayed in the App Widget.
 */
data class WidgetCourseItem(
    val id: Long,
    val title: String,
    val sectionText: String, // e.g. "第1-2节"
    val timeText: String,    // e.g. "08:00 - 09:35"
    val location: String,    // e.g. "知明 1-101"
    val teacher: String?,
    val isOngoing: Boolean,  // true if class is currently in progress
    val isTomorrow: Boolean, // true if this is a preview of tomorrow's class
    val isFinished: Boolean = false, // true if class has already finished today
)

/**
 * Sealed hierarchy representing the visual state of the 4x2 Schedule Widget.
 */
sealed interface WidgetScheduleData {
    val dateText: String     // e.g. "10月15日 周四"
    val weekText: String     // e.g. "第 7 周"

    /** Active semester with remaining courses today (CURRENT or UPCOMING). */
    data class TodayCourses(
        override val dateText: String,
        override val weekText: String,
        val subtitle: String, // e.g. "今日剩余" or "正在上课"
        val courses: List<WidgetCourseItem>,
        val totalRemainingCount: Int,
    ) : WidgetScheduleData

    /** Today has no remaining courses or is completely free, previewing tomorrow's classes. */
    data class TomorrowPreview(
        override val dateText: String,
        override val weekText: String,
        val statusNotice: String, // "今日课程已全部上完" or "今日全天无课"
        val subtitle: String,     // "明日预告"
        val tomorrowCourses: List<WidgetCourseItem>,
    ) : WidgetScheduleData

    /** Today finished/free and tomorrow also has no courses scheduled. */
    data class AllCompleted(
        override val dateText: String,
        override val weekText: String,
        val message: String,      // "今日课程已全部上完" or "今日全天无课"
        val hint: String,         // "明日本学期均无课，好好休息吧~"
    ) : WidgetScheduleData

    /** Before the semester starts. */
    data class BeforeSemester(
        override val dateText: String,
        override val weekText: String, // "尚未开学"
        val daysUntilStart: Long,
        val message: String,           // "学期尚未开始 · 距开学还有 X 天"
    ) : WidgetScheduleData

    /** After the semester has finished. */
    data class AfterSemester(
        override val dateText: String,
        override val weekText: String, // "已结课"
        val message: String,           // "本学期已结课，假期愉快！"
    ) : WidgetScheduleData

    /** No active semester configured or empty database. */
    data class NoSemester(
        override val dateText: String,
        override val weekText: String, // "未配置学期"
        val message: String,           // "点击进入应用配置学期与课表"
    ) : WidgetScheduleData
}
