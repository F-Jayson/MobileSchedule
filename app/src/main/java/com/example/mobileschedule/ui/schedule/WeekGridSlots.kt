package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.data.model.CourseArrangement

/** A visible cell retains every arrangement when time ranges overlap. */
internal data class WeekGridSlot(
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val arrangements: List<CourseArrangement>,
) {
    val sectionSpan: Int get() = endSection - startSection + 1
}

internal fun buildWeekGridSlots(arrangements: List<CourseArrangement>): List<WeekGridSlot> =
    arrangements.groupBy { it.dayOfWeek }.toSortedMap().flatMap { (day, courses) ->
        val slots = mutableListOf<WeekGridSlot>()
        courses.sortedWith(compareBy(CourseArrangement::startSection, CourseArrangement::endSection, CourseArrangement::id))
            .forEach { course ->
                val previous = slots.lastOrNull()
                if (previous != null && course.startSection <= previous.endSection) {
                    slots[slots.lastIndex] = previous.copy(
                        endSection = maxOf(previous.endSection, course.endSection),
                        arrangements = previous.arrangements + course,
                    )
                } else {
                    slots += WeekGridSlot(day, course.startSection, course.endSection, listOf(course))
                }
            }
        slots
    }
