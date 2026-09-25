package com.example.mobileschedule.data.local.entity

/** Projection from one Room query, so each emitted import status is a consistent database snapshot. */
data class ImportStatusRow(
    val semesterId: Long,
    val schoolId: String?,
    val sourceId: String?,
    val sourceTermId: String?,
    val sourceTermLabel: String?,
    val batchId: String?,
    val committedAt: Long?,
    val savedCount: Int?,
    val removedCount: Int?,
    val currentArrangementCount: Int,
)
