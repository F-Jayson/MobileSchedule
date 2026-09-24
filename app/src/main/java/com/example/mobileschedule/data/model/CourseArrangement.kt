package com.example.mobileschedule.data.model

data class CourseArrangement(
    val id: Long,
    val semesterId: Long,
    val name: String,
    val teacher: String?,
    val location: String?,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: Set<Int>,
    val origin: CourseOrigin,
)

sealed interface CourseOrigin {
    data class SchoolImport(val scope: SourceScope, val batchId: String, val sourceEntryId: String?) : CourseOrigin
    data object Manual : CourseOrigin
    data object Legacy : CourseOrigin
}
