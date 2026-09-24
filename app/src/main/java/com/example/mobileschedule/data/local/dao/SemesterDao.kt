package com.example.mobileschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.mobileschedule.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/** Storage primitives for the data layer. Validation and multi-table writes belong in the Repository. */
@Dao
interface SemesterDao {
    @Insert suspend fun insertSemester(semester: SemesterEntity): Long
    @Insert suspend fun insertConfig(config: SemesterConfigEntity)
    @Insert suspend fun insertSectionTimes(times: List<SectionTimeEntity>)
    @Insert suspend fun insertSourceBinding(binding: SourceBindingEntity)

    @Transaction
    @Query("SELECT * FROM semesters ORDER BY id")
    fun observeSemesters(): Flow<List<SemesterWithDetails>>

    @Transaction
    @Query("SELECT * FROM semesters WHERE id = :semesterId")
    suspend fun getSemester(semesterId: Long): SemesterWithDetails?

    @Transaction
    @Query("SELECT * FROM semesters WHERE id = :semesterId")
    fun observeSemester(semesterId: Long): Flow<SemesterWithDetails?>

    @Transaction
    @Query("SELECT * FROM semesters WHERE id = (SELECT activeSemesterId FROM app_settings WHERE id = 0)")
    fun observeActiveSemester(): Flow<SemesterWithDetails?>

    @Query("UPDATE semesters SET displayName = :displayName WHERE id = :semesterId")
    suspend fun updateDisplayName(semesterId: Long, displayName: String)

    @Query("UPDATE semester_configs SET firstWeekMonday = :firstWeekMonday, totalWeeks = :totalWeeks, totalSections = :totalSections, revision = :revision WHERE semesterId = :semesterId")
    suspend fun updateConfig(semesterId: Long, firstWeekMonday: Long, totalWeeks: Int, totalSections: Int, revision: Long)

    @Query("DELETE FROM section_times WHERE semesterId = :semesterId")
    suspend fun deleteSectionTimes(semesterId: Long)

    // This leaf row has no dependents; REPLACE is also supported by SQLite on API 26.
    @Query("INSERT OR REPLACE INTO app_settings (id, activeSemesterId) VALUES (0, :semesterId)")
    suspend fun setActiveSemester(semesterId: Long?)
}
