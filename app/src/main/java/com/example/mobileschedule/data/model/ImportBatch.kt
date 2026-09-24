package com.example.mobileschedule.data.model

import java.time.Instant

/** A successful import; counts refer to course arrangements, not distinct course names. */
data class ImportBatch(
    val id: String,
    val semesterId: Long,
    val scope: SourceScope,
    val committedAt: Instant,
    val savedCount: Int,
    val removedCount: Int,
)
