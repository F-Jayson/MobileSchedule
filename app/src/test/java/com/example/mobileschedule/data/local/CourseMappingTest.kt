package com.example.mobileschedule.data.local

import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.local.entity.CourseWithWeeks
import com.example.mobileschedule.data.local.entity.toModel
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseMappingTest {
    @Test
    fun disjointWeeksRemainDisjointDuringMapping() {
        val row = CourseWithWeeks(
            CourseEntity(8, "软件工程", "老师", "A101", 2, 3, 4),
            listOf(CourseWeekEntity(8, 9), CourseWeekEntity(8, 1), CourseWeekEntity(8, 3)),
        )
        val course = row.toModel()
        assertEquals(setOf(1, 3, 9), course.weeks)
        assertEquals(8L, course.id)
        assertEquals("A101", course.location)
    }
}
