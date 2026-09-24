package com.example.mobileschedule.data.repository

import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Implemented contract v1.1 subset for task 2.2. Detail and import calls arrive in later tasks. */
interface ScheduleRepository {
    fun observeSemesters(): Flow<RepoResult<List<Semester>>>
    fun observeActiveSemester(): Flow<RepoResult<Semester?>>
    fun observeSemesterConfig(semesterId: Long): Flow<RepoResult<SemesterConfig?>>
    fun observeWeek(semesterId: Long, week: Int): Flow<RepoResult<WeekSchedule>>
    suspend fun weekPosition(semesterId: Long, today: LocalDate): RepoResult<WeekPosition>

    suspend fun createSemester(draft: SemesterConfigDraft): RepoResult<Semester>
    suspend fun saveSemesterConfig(
        semesterId: Long, expectedRevision: Long, draft: SemesterConfigDraft,
    ): RepoResult<SemesterConfig>
    suspend fun setActiveSemester(semesterId: Long): RepoResult<Unit>
}
