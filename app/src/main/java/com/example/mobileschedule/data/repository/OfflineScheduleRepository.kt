package com.example.mobileschedule.data.repository

import androidx.room.withTransaction
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.SectionTimeEntity
import com.example.mobileschedule.data.local.entity.SemesterConfigEntity
import com.example.mobileschedule.data.local.entity.SemesterEntity
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.DataError
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.ImportRequest
import com.example.mobileschedule.data.model.ImportPreview
import com.example.mobileschedule.data.model.ImportConfirmation
import com.example.mobileschedule.data.model.ImportReceipt
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.WeekPosition
import com.example.mobileschedule.data.model.WeekSchedule
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class OfflineScheduleRepository @Inject constructor(private val database: AppDatabase) : ScheduleRepository {
    private val semesters = database.semesterDao()
    private val courses = database.courseDao()
    private val imports = ImportCoordinator(database)

    override suspend fun prepareImport(request: ImportRequest): RepoResult<ImportPreview> = imports.prepare(request)

    override suspend fun commitImport(previewId: String, confirmation: ImportConfirmation): RepoResult<ImportReceipt> =
        imports.commit(previewId, confirmation)

    override suspend fun discardImport(previewId: String): RepoResult<Unit> = imports.discard(previewId)

    override fun observeSemesters(): Flow<RepoResult<List<Semester>>> =
        semesters.observeSemesters().map { rows -> rows.map { it.toModel() } }.asResult()

    override fun observeActiveSemester(): Flow<RepoResult<Semester?>> =
        semesters.observeActiveSemester().map { it?.toModel() }.asResult()

    override fun observeSemesterConfig(semesterId: Long): Flow<RepoResult<SemesterConfig?>> =
        semesters.observeSemester(semesterId).map { row ->
            if (row == null) RepoResult.Err(DataError(DataErrorCode.SEMESTER_NOT_FOUND))
            else RepoResult.Ok(row.toModel().config)
        }.asReadResult()

    override fun observeWeek(semesterId: Long, week: Int): Flow<RepoResult<WeekSchedule>> =
        combine(semesters.observeSemester(semesterId), courses.observeSemesterCourses(semesterId)) { row, courseRows ->
            when {
                row == null -> RepoResult.Err(DataError(DataErrorCode.SEMESTER_NOT_FOUND))
                row.config == null -> RepoResult.Err(DataError(DataErrorCode.CONFIG_REQUIRED))
                else -> {
                    val config = requireNotNull(row.toModel().config)
                    if (week !in 1..config.totalWeeks) RepoResult.Err(DataError(DataErrorCode.WEEK_OUT_OF_RANGE))
                    else RepoResult.Ok(ScheduleRules.weekSchedule(semesterId, config, week, courseRows.map { it.toArrangement() }))
                }
            }
        }.asReadResult()

    override suspend fun weekPosition(semesterId: Long, today: LocalDate): RepoResult<WeekPosition> = try {
        val semester = semesters.getSemester(semesterId)?.toModel()
            ?: return RepoResult.Err(DataError(DataErrorCode.SEMESTER_NOT_FOUND))
        val config = semester.config ?: return RepoResult.Err(DataError(DataErrorCode.CONFIG_REQUIRED))
        RepoResult.Ok(ScheduleRules.weekPosition(config, today))
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        RepoResult.Err(DataError(DataErrorCode.STORAGE_READ_FAILED))
    }

    override suspend fun createSemester(draft: SemesterConfigDraft): RepoResult<Semester> {
        val issues = ScheduleRules.validateConfig(draft)
        if (issues.isNotEmpty()) return RepoResult.Err(DataError(DataErrorCode.CONFIG_INVALID, issues))
        return try {
            database.withTransaction {
                val semesterId = semesters.insertSemester(SemesterEntity(displayName = draft.displayName.trim()))
                semesters.insertConfig(draft.toEntity(semesterId, 1))
                semesters.insertSectionTimes(draft.timesToEntities(semesterId))
                RepoResult.Ok(requireNotNull(semesters.getSemester(semesterId)).toModel())
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            RepoResult.Err(DataError(DataErrorCode.STORAGE_WRITE_FAILED))
        }
    }

    override suspend fun saveSemesterConfig(
        semesterId: Long, expectedRevision: Long, draft: SemesterConfigDraft,
    ): RepoResult<SemesterConfig> {
        val issues = ScheduleRules.validateConfig(draft)
        if (issues.isNotEmpty()) return RepoResult.Err(DataError(DataErrorCode.CONFIG_INVALID, issues))
        return try {
            database.withTransaction {
                val current = semesters.getSemester(semesterId)?.toModel()
                    ?: return@withTransaction RepoResult.Err(DataError(DataErrorCode.SEMESTER_NOT_FOUND))
                val revision = current.config?.revision ?: 0L
                if (revision != expectedRevision || revision == Long.MAX_VALUE) {
                    return@withTransaction RepoResult.Err(DataError(DataErrorCode.CONFIG_CONFLICT))
                }
                val affected = courses.countOutsideConfig(semesterId, draft.totalWeeks, draft.totalSections)
                if (affected != 0) {
                    return@withTransaction RepoResult.Err(DataError(DataErrorCode.CONFIG_CONFLICT, affectedCount = affected))
                }
                if (current.displayName == draft.displayName.trim() && current.config?.matches(draft) == true) {
                    return@withTransaction RepoResult.Ok(current.config)
                }
                val nextRevision = revision + 1
                semesters.updateDisplayName(semesterId, draft.displayName.trim())
                if (current.config == null) {
                    semesters.insertConfig(draft.toEntity(semesterId, nextRevision))
                } else {
                    semesters.updateConfig(semesterId, draft.firstWeekMonday.toEpochDay(),
                        draft.totalWeeks, draft.totalSections, nextRevision)
                    semesters.deleteSectionTimes(semesterId)
                }
                semesters.insertSectionTimes(draft.timesToEntities(semesterId))
                RepoResult.Ok(requireNotNull(semesters.getSemester(semesterId)?.toModel()?.config))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            RepoResult.Err(DataError(DataErrorCode.STORAGE_WRITE_FAILED))
        }
    }

    override suspend fun setActiveSemester(semesterId: Long): RepoResult<Unit> = try {
        database.withTransaction {
            if (semesters.getSemester(semesterId) == null) {
                RepoResult.Err(DataError(DataErrorCode.SEMESTER_NOT_FOUND))
            } else {
                semesters.setActiveSemester(semesterId)
                RepoResult.Ok(Unit)
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        RepoResult.Err(DataError(DataErrorCode.STORAGE_WRITE_FAILED))
    }

    private fun SemesterConfigDraft.toEntity(semesterId: Long, revision: Long) =
        SemesterConfigEntity(semesterId, firstWeekMonday.toEpochDay(), totalWeeks, totalSections, revision)

    private fun SemesterConfigDraft.timesToEntities(semesterId: Long) = sectionTimes.map {
        SectionTimeEntity(semesterId, it.section, it.start.toSecondOfDay() / 60, it.end.toSecondOfDay() / 60)
    }

    private fun SemesterConfig.matches(draft: SemesterConfigDraft) =
        firstWeekMonday == draft.firstWeekMonday && totalWeeks == draft.totalWeeks &&
            totalSections == draft.totalSections && sectionTimes == draft.sectionTimes

    private fun <T> Flow<T>.asResult(): Flow<RepoResult<T>> =
        map<T, RepoResult<T>> { RepoResult.Ok(it) }.asReadResult()

    private fun <T> Flow<RepoResult<T>>.asReadResult(): Flow<RepoResult<T>> = catch { error ->
        if (error is CancellationException) throw error
        emit(RepoResult.Err(DataError(DataErrorCode.STORAGE_READ_FAILED)))
    }
}
