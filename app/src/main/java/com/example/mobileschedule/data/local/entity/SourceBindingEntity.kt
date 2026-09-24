package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.mobileschedule.data.model.SourceScope

@Entity(
    tableName = "source_bindings", primaryKeys = ["schoolId", "sourceId", "sourceTermId"],
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["semesterId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("semesterId"), Index(value = ["schoolId", "sourceId", "sourceTermId", "semesterId"], unique = true)],
)
data class SourceBindingEntity(
    val schoolId: String,
    val sourceId: String,
    val sourceTermId: String,
    val semesterId: Long,
    val sourceTermLabel: String?,
)

fun SourceBindingEntity.toScope() = SourceScope(schoolId, sourceId, sourceTermId)
