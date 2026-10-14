package com.example.mobileschedule.data.model

import java.time.LocalDate

data class SemesterImportPreview(
    val displayName: String,
    val firstWeekMonday: LocalDate,
    val totalWeeks: Int,
    val totalSections: Int,
    val sectionTimes: List<SectionTime>,
    val arrangementCount: Int,
    val existingSemesterId: Long? = null,
    val existingSemesterName: String? = null,
    val arrangements: List<ArrangementBackupItem>,
)

data class ArrangementBackupItem(
    val name: String,
    val teacher: String?,
    val location: String?,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: Set<Int>,
    val origin: CourseOriginDraft = CourseOriginDraft.Manual,
)

sealed interface CourseOriginDraft {
    data object Manual : CourseOriginDraft
    data object Legacy : CourseOriginDraft
    data class SchoolImport(
        val schoolId: String,
        val sourceId: String,
        val sourceTermId: String,
        val sourceEntryId: String?,
    ) : CourseOriginDraft
}
