package com.example.mobileschedule.data.repository

import android.os.SystemClock
import androidx.room.withTransaction
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.local.entity.ImportBatchEntity
import com.example.mobileschedule.data.local.entity.SourceBindingEntity
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.rules.ImportValidator
import com.example.mobileschedule.data.rules.ScheduleRules
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local preview tokens; the only course import writer is commit, never a DAO caller. */
internal class ImportCoordinator(private val database: AppDatabase) {
    private val semesters = database.semesterDao()
    private val courses = database.courseDao()
    private val batches = database.importBatchDao()
    private val mutex = Mutex()
    private val pending = LinkedHashMap<String, Pending>()

    suspend fun prepare(request: ImportRequest): RepoResult<ImportPreview> = mutex.withLock {
        try {
            prune()
            val frozen = request.deepCopy()
            val id = UUID.randomUUID().toString()
            val state = database.withTransaction {
                val semester = semesters.getSemester(frozen.targetSemesterId)?.toModel()
                val identity = ImportValidator.buildPreview(id, frozen, semester?.config, null, null)
                val scope = identity.scope
                val binding = scope?.let { semesters.getSourceBinding(it.schoolId, it.sourceId, it.sourceTermId) }
                val old = scope?.let { courses.getImportedInScope(it.schoolId, it.sourceId, it.sourceTermId)
                    .map { row -> row.toArrangement() } } ?: emptyList()
                val preview = ImportValidator.buildPreview(id, frozen, semester?.config, binding?.semesterId, old.size)
                val resolved = if (semester == null) preview.copy(
                    issues = preview.issues + ImportIssue(ImportIssueStage.VALIDATION, ImportIssueCode.TARGET_SEMESTER_NOT_FOUND),
                    canCommit = false,
                ) else preview
                Pending(frozen, resolved, semester?.config?.revision, old, SystemClock.elapsedRealtime())
            }
            pending[id] = state
            if (pending.size > 16) pending.remove(pending.keys.first())
            RepoResult.Ok(state.preview.deepCopy())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            RepoResult.Err(DataError(DataErrorCode.STORAGE_READ_FAILED))
        }
    }

    suspend fun commit(previewId: String, confirmation: ImportConfirmation): RepoResult<ImportReceipt> = mutex.withLock {
        prune()
        val state = pending[previewId] ?: return@withLock stale()
        val preview = state.preview
        if (!preview.canCommit) return@withLock RepoResult.Err(DataError(DataErrorCode.IMPORT_BLOCKED))
        val scope = preview.scope ?: return@withLock RepoResult.Err(DataError(DataErrorCode.IMPORT_BLOCKED))
        if (confirmation.previewId != previewId || confirmation.scope != scope ||
            confirmation.replaceCount != preview.replaceCount || confirmation.validCount != preview.validCount) {
            pending.remove(previewId)
            return@withLock stale()
        }
        try {
            val result = database.withTransaction {
                val semester = semesters.getSemester(preview.targetSemesterId)?.toModel()
                    ?: return@withTransaction stale()
                val config = semester.config ?: return@withTransaction stale()
                if (config.revision != state.revision) return@withTransaction stale()
                val binding = semesters.getSourceBinding(scope.schoolId, scope.sourceId, scope.sourceTermId)
                if (binding != null && binding.semesterId != preview.targetSemesterId) {
                    return@withTransaction RepoResult.Err(DataError(DataErrorCode.SCOPE_CONFLICT))
                }
                val old = courses.getImportedInScope(scope.schoolId, scope.sourceId, scope.sourceTermId)
                    .map { it.toArrangement() }
                if (old != state.oldRows || old.size != preview.replaceCount) return@withTransaction stale()
                val rechecked = ImportValidator.buildPreview(previewId, state.request, config,
                    binding?.semesterId, old.size)
                if (!rechecked.canCommit || rechecked != preview ||
                    rechecked.normalizedArrangements.any { row ->
                        ScheduleRules.validateArrangement(config, row.toDraft()).isNotEmpty()
                    }) return@withTransaction stale()

                if (binding == null) semesters.insertSourceBinding(SourceBindingEntity(
                    scope.schoolId, scope.sourceId, scope.sourceTermId, preview.targetSemesterId,
                    state.request.sourceTermLabel?.trim()?.takeIf { it.isNotEmpty() },
                ))
                val batchId = UUID.randomUUID().toString()
                batches.insertBatch(ImportBatchEntity(batchId, preview.targetSemesterId,
                    scope.schoolId, scope.sourceId, scope.sourceTermId, System.currentTimeMillis(),
                    preview.validCount, old.size))
                val removed = courses.deleteImportedInScope(scope.schoolId, scope.sourceId, scope.sourceTermId)
                check(removed == old.size) { "Import scope changed during transaction" }
                val ids = courses.insertCourses(preview.normalizedArrangements.map { row ->
                    CourseEntity(name = row.name, teacher = row.teacher, location = row.location,
                        dayOfWeek = row.dayOfWeek, startSection = row.startSection, endSection = row.endSection,
                        semesterId = preview.targetSemesterId, originType = CourseOriginType.SCHOOL_IMPORT,
                        importBatchId = batchId, sourceEntryId = row.sourceEntryId)
                })
                check(ids.size == preview.validCount) { "Incomplete course insert" }
                courses.insertWeeks(preview.normalizedArrangements.zip(ids).flatMap { (row, id) ->
                    row.weeks.map { week -> CourseWeekEntity(id, week) }
                })
                RepoResult.Ok(ImportReceipt(batchId, scope, preview.targetSemesterId, removed, ids.size))
            }
            pending.remove(previewId)
            result
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Room rolls back the binding, batch, deletion, courses and weeks together.
            RepoResult.Err(DataError(DataErrorCode.STORAGE_WRITE_FAILED))
        }
    }

    suspend fun discard(previewId: String): RepoResult<Unit> = mutex.withLock {
        pending.remove(previewId)
        RepoResult.Ok(Unit)
    }

    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        pending.entries.removeAll { now - it.value.createdAt > 15 * 60 * 1000L }
    }

    private fun stale(): RepoResult.Err = RepoResult.Err(DataError(DataErrorCode.PREVIEW_STALE))

    private data class Pending(
        val request: ImportRequest,
        val preview: ImportPreview,
        val revision: Long?,
        val oldRows: List<CourseArrangement>,
        val createdAt: Long,
    )

    private fun ImportRequest.deepCopy() = copy(
        candidates = candidates.map { it.copy(weeks = it.weeks?.toSet()) },
        parseIssues = parseIssues.map { it.copy(ruleIssues = it.ruleIssues.toList()) },
        completeness = completeness.copy(pageIndicesRead = completeness.pageIndicesRead.toList()),
    )

    private fun ImportPreview.deepCopy() = copy(
        normalizedArrangements = normalizedArrangements.map { it.copy(weeks = it.weeks.toSet()) },
        issues = issues.map { it.copy(ruleIssues = it.ruleIssues.toList()) },
    )

    private fun PreviewArrangement.toDraft() = ArrangementDraft(
        name, teacher, location, dayOfWeek, startSection, endSection, weeks,
    )
}
