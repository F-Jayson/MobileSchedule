package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["semesterId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ImportBatchEntity::class, parentColumns = ["id", "semesterId"], childColumns = ["importBatchId", "semesterId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("semesterId"), Index(value = ["importBatchId", "semesterId"])],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String?,
    val location: String?,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val semesterId: Long,
    val originType: CourseOriginType,
    val importBatchId: String? = null,
    val sourceEntryId: String? = null,
)

/** Persisted as these stable names by Room; renaming requires a migration. */
enum class CourseOriginType { SCHOOL_IMPORT, MANUAL, LEGACY }
