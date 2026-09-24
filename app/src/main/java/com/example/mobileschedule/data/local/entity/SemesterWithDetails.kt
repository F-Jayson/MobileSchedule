package com.example.mobileschedule.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import java.time.LocalDate
import java.time.LocalTime

data class SemesterWithDetails(
    @Embedded val semester: SemesterEntity,
    @Relation(parentColumn = "id", entityColumn = "semesterId") val config: SemesterConfigEntity?,
    @Relation(parentColumn = "id", entityColumn = "semesterId") val sectionTimes: List<SectionTimeEntity>,
    @Relation(parentColumn = "id", entityColumn = "semesterId") val sourceBindings: List<SourceBindingEntity>,
)

fun SemesterWithDetails.toModel() = Semester(
    id = semester.id,
    displayName = semester.displayName,
    sourceBindings = sourceBindings.map { it.toScope() }.toSet(),
    config = config?.let { config ->
        SemesterConfig(
            firstWeekMonday = LocalDate.ofEpochDay(config.firstWeekMonday),
            totalWeeks = config.totalWeeks,
            totalSections = config.totalSections,
            sectionTimes = sectionTimes.sortedBy { it.section }.map {
                SectionTime(it.section, LocalTime.ofSecondOfDay(it.startMinute * 60L), LocalTime.ofSecondOfDay(it.endMinute * 60L))
            },
            revision = config.revision,
        )
    },
)
