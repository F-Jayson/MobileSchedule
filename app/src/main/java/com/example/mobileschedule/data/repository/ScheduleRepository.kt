package com.example.mobileschedule.data.repository

import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.ImportRequest
import com.example.mobileschedule.data.model.ImportPreview
import com.example.mobileschedule.data.model.ImportConfirmation
import com.example.mobileschedule.data.model.ImportReceipt
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Contract v1.1; course detail remains for task 2.4. Import writes only through commitImport. */
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

    suspend fun prepareImport(request: ImportRequest): RepoResult<ImportPreview>
    suspend fun commitImport(previewId: String, confirmation: ImportConfirmation): RepoResult<ImportReceipt>
    suspend fun discardImport(previewId: String): RepoResult<Unit>
}
