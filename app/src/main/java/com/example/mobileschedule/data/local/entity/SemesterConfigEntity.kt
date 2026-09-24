package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** No row means unconfigured. Dates are epoch days, with no timezone conversion. */
@Entity(
    tableName = "semester_configs",
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["semesterId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SemesterConfigEntity(
    @PrimaryKey val semesterId: Long,
    val firstWeekMonday: Long,
    val totalWeeks: Int,
    val totalSections: Int,
    val revision: Long,
)
