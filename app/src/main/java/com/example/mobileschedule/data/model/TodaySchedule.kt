package com.example.mobileschedule.data.model

import java.time.LocalDate
import java.time.LocalTime

/** Status of a course relative to current time. */
enum class CourseStatus {
    CURRENT,   // 正在进行中
    UPCOMING,  // 即将开始 / 下一节
    PAST,      // 已结束
}

/** Represents a single course arrangement on today's schedule with section times and status. */
data class TodayCourseItem(
    val arrangement: CourseArrangement,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val status: CourseStatus,
    val isNext: Boolean = false,
)

/** Read-model representing the complete state of today's schedule. */
sealed interface TodaySchedule {
    data object NoActiveSemester : TodaySchedule

    data class ConfigRequired(val semester: Semester) : TodaySchedule

    data class BeforeSemester(
        val semester: Semester,
        val today: LocalDate,
        val daysUntilStart: Long,
    ) : TodaySchedule

    data class AfterSemester(
        val semester: Semester,
        val today: LocalDate,
    ) : TodaySchedule

    data class Ready(
        val semester: Semester,
        val currentWeek: Int,
        val date: LocalDate,
        val dayOfWeek: Int, // 1..7 (Monday=1, Sunday=7)
        val courses: List<TodayCourseItem>,
    ) : TodaySchedule
}
