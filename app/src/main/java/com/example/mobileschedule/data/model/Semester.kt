package com.example.mobileschedule.data.model

import java.time.LocalDate
import java.time.LocalTime

data class Semester(
    val id: Long,
    val displayName: String,
    val sourceBindings: Set<SourceScope>,
    val config: SemesterConfig?,
)

data class SemesterConfig(
    val firstWeekMonday: LocalDate,
    val totalWeeks: Int,
    val totalSections: Int,
    val sectionTimes: List<SectionTime>,
    val revision: Long,
)

data class SectionTime(val section: Int, val start: LocalTime, val end: LocalTime)
