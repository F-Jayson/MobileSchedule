package com.example.mobileschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mobileschedule.data.local.entity.ImportBatchEntity
import com.example.mobileschedule.data.local.entity.ImportStatusRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportBatchDao {
    @Insert suspend fun insertBatch(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE id = :batchId")
    suspend fun getBatch(batchId: String): ImportBatchEntity?

    /** Includes an unbound semester as one null-scope row; no second query can race a commit. */
    @Query("""
        SELECT s.id AS semesterId, b.schoolId, b.sourceId, b.sourceTermId, b.sourceTermLabel,
            ib.id AS batchId, ib.committedAt, ib.savedCount, ib.removedCount,
            (SELECT COUNT(*) FROM courses AS c JOIN import_batches AS cb
                ON cb.id = c.importBatchId AND cb.semesterId = c.semesterId
                WHERE c.semesterId = s.id AND c.originType = 'SCHOOL_IMPORT'
                    AND cb.schoolId = b.schoolId AND cb.sourceId = b.sourceId
                    AND cb.sourceTermId = b.sourceTermId) AS currentArrangementCount
        FROM semesters AS s
        LEFT JOIN source_bindings AS b ON b.semesterId = s.id
        LEFT JOIN import_batches AS ib ON ib.semesterId = s.id
            AND ib.schoolId = b.schoolId AND ib.sourceId = b.sourceId
            AND ib.sourceTermId = b.sourceTermId
        WHERE s.id = :semesterId
        ORDER BY b.schoolId, b.sourceId, b.sourceTermId, ib.committedAt DESC, ib.rowid DESC
    """)
    fun observeImportStatus(semesterId: Long): Flow<List<ImportStatusRow>>
}
