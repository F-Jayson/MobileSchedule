package com.example.mobileschedule.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.example.mobileschedule.data.model.Course

data class CourseWithWeeks(
    @Embedded val course: CourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val weeks: List<CourseWeekEntity>,
)

fun CourseWithWeeks.toModel() = Course(
    id = course.id,
    name = course.name,
    teacher = course.teacher,
    location = course.location,
    dayOfWeek = course.dayOfWeek,
    startSection = course.startSection,
    endSection = course.endSection,
    weeks = weeks.map { it.week }.toSortedSet(),
)
