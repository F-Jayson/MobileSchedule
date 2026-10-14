package com.example.mobileschedule.data.repository

import com.example.mobileschedule.data.model.ArrangementDraft
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.ImportRequest
import com.example.mobileschedule.data.model.ImportPreview
import com.example.mobileschedule.data.model.ImportConfirmation
import com.example.mobileschedule.data.model.ImportReceipt
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseDetail
import com.example.mobileschedule.data.model.SemesterImportStatus
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.TodaySchedule
import com.example.mobileschedule.data.model.WidgetScheduleData
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** Contract v1.1 plus the active-week and import-status read models required by task 2.4. */
interface ScheduleRepository {
    fun observeSemesters(): Flow<RepoResult<List<Semester>>>
    fun observeActiveSemester(): Flow<RepoResult<Semester?>>
    fun observeSemesterConfig(semesterId: Long): Flow<RepoResult<SemesterConfig?>>
    fun observeWeek(semesterId: Long, week: Int): Flow<RepoResult<WeekSchedule>>
    fun observeActiveWeek(selectedWeek: Int): Flow<RepoResult<ActiveWeek?>>
    fun observeCourseDetail(arrangementId: Long): Flow<RepoResult<CourseDetail?>>
    fun observeImportStatus(semesterId: Long): Flow<RepoResult<SemesterImportStatus>>
    fun observeTodaySchedule(
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ): Flow<RepoResult<TodaySchedule>>
    suspend fun loadWidgetSchedule(
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ): WidgetScheduleData
    suspend fun weekPosition(semesterId: Long, today: LocalDate): RepoResult<WeekPosition>

    suspend fun createSemester(draft: SemesterConfigDraft): RepoResult<Semester>
    suspend fun saveSemesterConfig(
        semesterId: Long, expectedRevision: Long, draft: SemesterConfigDraft,
    ): RepoResult<SemesterConfig>
    suspend fun setActiveSemester(semesterId: Long): RepoResult<Unit>

    suspend fun createManualCourse(semesterId: Long, draft: ArrangementDraft): RepoResult<CourseArrangement>
    suspend fun updateManualCourse(arrangementId: Long, draft: ArrangementDraft): RepoResult<CourseArrangement>
    suspend fun deleteManualCourse(arrangementId: Long): RepoResult<Unit>
    suspend fun checkArrangementConflict(
        semesterId: Long, draft: ArrangementDraft, excludingArrangementId: Long? = null,
    ): RepoResult<List<CourseArrangement>>

    suspend fun prepareImport(request: ImportRequest): RepoResult<ImportPreview>
    suspend fun commitImport(previewId: String, confirmation: ImportConfirmation): RepoResult<ImportReceipt>
    suspend fun discardImport(previewId: String): RepoResult<Unit>
}
