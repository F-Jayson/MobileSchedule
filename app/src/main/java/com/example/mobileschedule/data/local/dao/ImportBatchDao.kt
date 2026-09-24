package com.example.mobileschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mobileschedule.data.local.entity.ImportBatchEntity

@Dao
interface ImportBatchDao {
    @Insert suspend fun insertBatch(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE id = :batchId")
    suspend fun getBatch(batchId: String): ImportBatchEntity?
}
