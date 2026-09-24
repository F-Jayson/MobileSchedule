package com.example.mobileschedule.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.example.mobileschedule.data.model.Course
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SourceScope

data class CourseWithWeeks(
    @Embedded val course: CourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val weeks: List<CourseWeekEntity>,
    @Relation(parentColumn = "importBatchId", entityColumn = "id")
    val batch: ImportBatchEntity? = null,
)

fun CourseWithWeeks.toModel() = Course(
    id = course.id,
    name = course.name,
    teacher = course.teacher.orEmpty(),
    location = course.location.orEmpty(),
    dayOfWeek = course.dayOfWeek,
    startSection = course.startSection,
    endSection = course.endSection,
    weeks = weeks.map { it.week }.toSortedSet(),
)

fun CourseWithWeeks.toArrangement(): CourseArrangement {
    val origin = when (course.originType) {
        CourseOriginType.SCHOOL_IMPORT -> {
            val source = requireNotNull(batch) { "Imported arrangement has no batch" }
            require(source.id == course.importBatchId && source.semesterId == course.semesterId)
            CourseOrigin.SchoolImport(
                SourceScope(source.schoolId, source.sourceId, source.sourceTermId), source.id, course.sourceEntryId,
            )
        }
        CourseOriginType.MANUAL, CourseOriginType.LEGACY -> {
            require(course.importBatchId == null && course.sourceEntryId == null && batch == null)
            if (course.originType == CourseOriginType.MANUAL) CourseOrigin.Manual else CourseOrigin.Legacy
        }
    }
    return CourseArrangement(
        course.id, course.semesterId, course.name,
        course.teacher?.trim()?.takeIf { it.isNotEmpty() }, course.location?.trim()?.takeIf { it.isNotEmpty() },
        course.dayOfWeek, course.startSection, course.endSection, weeks.map { it.week }.toSortedSet(), origin,
    )
}
