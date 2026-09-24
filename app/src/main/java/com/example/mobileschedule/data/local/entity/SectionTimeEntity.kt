package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** Local clock minutes since midnight; no assumed school timetable. */
@Entity(
    tableName = "section_times", primaryKeys = ["semesterId", "section"],
    foreignKeys = [ForeignKey(
        entity = SemesterConfigEntity::class, parentColumns = ["semesterId"], childColumns = ["semesterId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SectionTimeEntity(val semesterId: Long, val section: Int, val startMinute: Int, val endMinute: Int)
