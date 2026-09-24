package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mobileschedule.data.model.ImportBatch
import com.example.mobileschedule.data.model.SourceScope
import java.time.Instant

@Entity(
    tableName = "import_batches",
    foreignKeys = [ForeignKey(
        entity = SourceBindingEntity::class,
        parentColumns = ["schoolId", "sourceId", "sourceTermId", "semesterId"],
        childColumns = ["schoolId", "sourceId", "sourceTermId", "semesterId"], onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["schoolId", "sourceId", "sourceTermId", "semesterId"]), Index(value = ["id", "semesterId"], unique = true)],
)
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val semesterId: Long,
    val schoolId: String,
    val sourceId: String,
    val sourceTermId: String,
    val committedAt: Long,
    val savedCount: Int,
    val removedCount: Int,
)

fun ImportBatchEntity.toModel() = ImportBatch(
    id, semesterId, SourceScope(schoolId, sourceId, sourceTermId), Instant.ofEpochMilli(committedAt), savedCount, removedCount,
)
