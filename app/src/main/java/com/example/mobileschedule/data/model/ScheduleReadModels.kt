package com.example.mobileschedule.data.model

import java.time.LocalTime

/** A single subscription always pairs the selected week with the same active semester. */
data class ActiveWeek(val semester: Semester, val schedule: WeekSchedule)

data class CourseDetail(
    val arrangement: CourseArrangement,
    val semesterDisplayName: String,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
)

data class ImportSourceStatus(
    val scope: SourceScope,
    val sourceTermLabel: String?,
    val latestBatch: ImportBatch?,
    val currentArrangementCount: Int,
)

data class SemesterImportStatus(val semesterId: Long, val sources: List<ImportSourceStatus>) {
    val importedArrangementCount: Int get() = sources.sumOf { it.currentArrangementCount }
    val hasImportedCourses: Boolean get() = importedArrangementCount > 0
}
