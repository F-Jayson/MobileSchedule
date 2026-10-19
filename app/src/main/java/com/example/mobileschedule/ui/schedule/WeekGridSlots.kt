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

    fun hasActive(currentWeek: Int): Boolean =
        arrangements.any { currentWeek in it.weeks }

    fun isInactiveOnly(currentWeek: Int): Boolean =
        arrangements.isNotEmpty() && arrangements.all { currentWeek !in it.weeks }
}

internal fun buildWeekGridSlots(
    arrangements: List<CourseArrangement>,
    showWeekend: Boolean = true,
    currentWeek: Int? = null,
): List<WeekGridSlot> {
    val filtered = if (showWeekend) arrangements else arrangements.filter { it.dayOfWeek in 1..5 }
    val comparator = if (currentWeek != null) {
        compareBy<CourseArrangement> { it.startSection }
            .thenBy { if (currentWeek in it.weeks) 0 else 1 }
            .thenBy { it.endSection }
            .thenBy { it.id }
    } else {
        compareBy(CourseArrangement::startSection, CourseArrangement::endSection, CourseArrangement::id)
    }

    return filtered.groupBy { it.dayOfWeek }.toSortedMap().flatMap { (day, courses) ->
        val slots = mutableListOf<WeekGridSlot>()
        courses.sortedWith(comparator)
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
}
