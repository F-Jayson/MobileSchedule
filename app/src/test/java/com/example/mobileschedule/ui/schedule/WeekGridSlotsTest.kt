package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekGridSlotsTest {
    @Test fun keepsWeekendsAndCrossSectionHeight() {
        val slots = buildWeekGridSlots(listOf(course(1, 6, 3, 5), course(2, 7, 1, 1)))
        assertEquals(listOf(6, 7), slots.map { it.dayOfWeek })
        assertEquals(3, slots[0].startSection)
        assertEquals(3, slots[0].sectionSpan)
        assertEquals(1, slots[1].sectionSpan)
    }

    @Test fun overlappingCoursesStayAccessibleAndSeparateNamesAreNotMerged() {
        val slots = buildWeekGridSlots(listOf(
            course(1, 2, 3, 4), course(2, 2, 4, 5), course(3, 2, 7, 8),
        ))
        assertEquals(2, slots.size)
        assertEquals(listOf(1L, 2L), slots[0].arrangements.map { it.id })
        assertEquals(3, slots[0].sectionSpan)
        assertEquals(listOf(3L), slots[1].arrangements.map { it.id })
    }

    private fun course(id: Long, day: Int, start: Int, end: Int) = CourseArrangement(
        id, 1, "同名课程", null, null, day, start, end, setOf(1), CourseOrigin.Manual,
    )
}
