package com.example.mobileschedule.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.local.entity.ImportBatchEntity
import com.example.mobileschedule.data.local.entity.SourceBindingEntity
import com.example.mobileschedule.data.model.*
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportTransactionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "import-transaction-test.db"
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before fun open() {
        context.deleteDatabase(dbName)
        database = AppDatabase.open(context, dbName)
        repository = OfflineScheduleRepository(database)
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(dbName)
    }

    private fun <T> ok(result: RepoResult<T>): T {
        assertTrue("Expected Ok, got $result", result is RepoResult.Ok)
        return (result as RepoResult.Ok).value
    }

    private fun <T> err(result: RepoResult<T>, code: DataErrorCode) {
        assertTrue("Expected Err, got $result", result is RepoResult.Err)
        assertEquals(code, (result as RepoResult.Err).error.code)
    }

    private suspend fun semester(name: String = "合成学期") = ok(repository.createSemester(
        SemesterConfigDraft(name, LocalDate.of(2026, 9, 7), 12, 4, emptyList()),
    ))

    private fun row(index: Int, name: String = "课程", section: Int = 1, weeks: Set<Int> = setOf(1, 3)) =
        ImportCandidate(index, null, name, null, null, 2, section, section, weeks)

    private fun request(semesterId: Long, rows: List<ImportCandidate>, source: String = "dom", term: String = "term-1") =
        ImportRequest(semesterId, "fjnu", source, term, "合成学期", rows.size, rows.size, rows,
            emptyList(), CompletenessEvidence(CompletenessStatus.VERIFIED_FULL, term,
                listOf(0), 1, rows.size, "synthetic complete-page fixture"))

    private suspend fun save(request: ImportRequest): ImportReceipt {
        val preview = ok(repository.prepareImport(request))
        assertTrue("$preview", preview.canCommit)
        return ok(repository.commitImport(preview.previewId, ImportConfirmation(
            preview.previewId, preview.scope!!, preview.replaceCount!!, preview.validCount)))
    }

    @Test fun firstImportAndRepeatedSnapshotKeepArrangementCountStable() = runBlocking {
        val target = semester()
        val source = request(target.id, listOf(row(0), row(1, section = 2)))
        val first = save(source)
        assertEquals(0, first.removedCount)
        assertEquals(2, first.savedCount)
        val oldIds = ok(repository.observeWeek(target.id, 1).first()).arrangements.map { it.id }
        assertEquals(2, oldIds.size)

        val preview = ok(repository.prepareImport(source))
        assertEquals(2, preview.replaceCount)
        val second = ok(repository.commitImport(preview.previewId,
            ImportConfirmation(preview.previewId, preview.scope!!, 2, 2)))
        assertEquals(2, second.removedCount)
        assertEquals(2, second.savedCount)
        assertNotEquals(first.batchId, second.batchId)
        val current = ok(repository.observeWeek(target.id, 1).first()).arrangements
        assertEquals(2, current.size)
        assertTrue(current.none { it.id in oldIds })
        assertTrue(current.all { (it.origin as CourseOrigin.SchoolImport).batchId == second.batchId })
        assertEquals(setOf(1, 3), current.first().weeks)
        err(repository.commitImport(preview.previewId, ImportConfirmation(preview.previewId, preview.scope!!, 2, 2)),
            DataErrorCode.PREVIEW_STALE)
        assertEquals(2, ok(repository.observeWeek(target.id, 1).first()).arrangements.size)
    }

    @Test fun activeWeekSubscriberReceivesCommittedArrangementAfterReturningFromImport() = runBlocking {
        val target = semester()
        ok(repository.setActiveSemester(target.id))
        assertTrue(ok(repository.observeActiveWeek(1).first())!!.schedule.arrangements.isEmpty())
        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) {
                repository.observeActiveWeek(1).first { result ->
                    (result as? RepoResult.Ok)?.value?.schedule?.arrangements?.size == 1
                }
            }
        }

        val receipt = save(request(target.id, listOf(row(0, "合成导入课", weeks = setOf(1)))))
        val active = ok(updated.await())!!
        assertEquals(target.id, active.semester.id)
        assertEquals(receipt.savedCount, active.schedule.arrangements.size)
        assertEquals("合成导入课", active.schedule.arrangements.single().name)
    }

    @Test fun replacementIsolatesSourceTermChannelSemesterAndManualRows() = runBlocking {
        val target = semester()
        val other = semester("另一学期")
        val manualId = database.courseDao().insertCourses(listOf(CourseEntity(name = "手动课", teacher = null,
            location = null, dayOfWeek = 1, startSection = 1, endSection = 1, semesterId = target.id,
            originType = CourseOriginType.MANUAL))).single()
        val legacyId = database.courseDao().insertCourses(listOf(CourseEntity(name = "历史课", teacher = null,
            location = null, dayOfWeek = 1, startSection = 2, endSection = 2, semesterId = target.id,
            originType = CourseOriginType.LEGACY))).single()
        database.semesterDao().insertSourceBinding(SourceBindingEntity("another-school", "dom", "term-1", target.id, null))
        database.importBatchDao().insertBatch(ImportBatchEntity("other-school-batch", target.id,
            "another-school", "dom", "term-1", 1L, 1, 0))
        val otherSchoolId = database.courseDao().insertCourses(listOf(CourseEntity(name = "其他学校", teacher = null,
            location = null, dayOfWeek = 1, startSection = 3, endSection = 3, semesterId = target.id,
            originType = CourseOriginType.SCHOOL_IMPORT, importBatchId = "other-school-batch"))).single()
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(manualId, 1), CourseWeekEntity(legacyId, 1),
            CourseWeekEntity(otherSchoolId, 1)))
        save(request(target.id, listOf(row(0, "要替换"))))
        save(request(target.id, listOf(row(0, "其他来源")), source = "another-dom"))
        save(request(target.id, listOf(row(0, "其他学期")), term = "term-2"))
        save(request(other.id, listOf(row(0, "另一本地学期")), term = "term-3"))

        val conflict = ok(repository.prepareImport(request(other.id, listOf(row(0, "冲突")))))
        assertFalse(conflict.canCommit)
        assertTrue(conflict.issues.any { it.code == ImportIssueCode.SCOPE_CONFLICT })
        err(repository.commitImport(conflict.previewId, ImportConfirmation(conflict.previewId,
            SourceScope("fjnu", "dom", "term-1"), 1, 1)), DataErrorCode.IMPORT_BLOCKED)

        val replaced = save(request(target.id, listOf(row(0, "新课"))))
        assertEquals(1, replaced.removedCount)
        val names = ok(repository.observeWeek(target.id, 1).first()).arrangements.map { it.name }.toSet()
        assertEquals(setOf("手动课", "历史课", "其他学校", "其他来源", "其他学期", "新课"), names)
        assertEquals(setOf("另一本地学期"), ok(repository.observeWeek(other.id, 1).first()).arrangements.map { it.name }.toSet())
        assertNotNull(database.courseDao().getCourse(manualId))
        assertNotNull(database.courseDao().getCourse(legacyId))
        assertNotNull(database.courseDao().getCourse(otherSchoolId))
    }

    @Test fun changingReturnedPreviewCannotAlterValidatedRowsOrConfirmation() = runBlocking {
        val target = semester()
        val preview = ok(repository.prepareImport(request(target.id, listOf(row(0, "原定课程")))))
        val exposed = preview.normalizedArrangements.first().weeks as MutableSet<Int>
        exposed.clear()
        exposed += 12
        val receipt = ok(repository.commitImport(preview.previewId,
            ImportConfirmation(preview.previewId, preview.scope!!, 0, 1)))
        assertEquals(1, receipt.savedCount)
        val saved = ok(repository.observeWeek(target.id, 1).first()).arrangements.single()
        assertEquals("原定课程", saved.name)
        assertEquals(setOf(1, 3), saved.weeks)
    }

    @Test fun zeroPartialIllegalAndUnknownBatchesCannotReplaceOldCourses() = runBlocking {
        val target = semester()
        save(request(target.id, listOf(row(0, "原课"))))
        val base = request(target.id, listOf(row(0, "新课")))
        val blocked = listOf(
            request(target.id, emptyList()),
            base.copy(completeness = base.completeness.copy(status = CompletenessStatus.PARTIAL)),
            request(target.id, listOf(row(0, section = 9))),
            base.copy(sourceTermId = null),
            base.copy(sourceObservedCount = null),
        )
        for (input in blocked) {
            val preview = ok(repository.prepareImport(input))
            assertFalse("$preview", preview.canCommit)
            err(repository.commitImport(preview.previewId, ImportConfirmation(preview.previewId,
                SourceScope("fjnu", "dom", "term-1"), 1, preview.validCount)), DataErrorCode.IMPORT_BLOCKED)
        }
        assertEquals(listOf("原课"), ok(repository.observeWeek(target.id, 1).first()).arrangements.map { it.name })
    }

    @Test fun changedConfirmationConfigOrSameCountDataMakesPreviewStale() = runBlocking {
        val target = semester()
        save(request(target.id, listOf(row(0, "原课"))))
        val next = request(target.id, listOf(row(0, "新课")))
        val mismatched = ok(repository.prepareImport(next))
        err(repository.commitImport(mismatched.previewId,
            ImportConfirmation(mismatched.previewId, mismatched.scope!!, 0, 1)), DataErrorCode.PREVIEW_STALE)

        val config = ok(repository.observeSemesterConfig(target.id).first())!!
        val staleConfig = ok(repository.prepareImport(next))
        ok(repository.saveSemesterConfig(target.id, config.revision,
            SemesterConfigDraft("修改后", config.firstWeekMonday, 12, 4, emptyList())))
        err(repository.commitImport(staleConfig.previewId,
            ImportConfirmation(staleConfig.previewId, staleConfig.scope!!, 1, 1)), DataErrorCode.PREVIEW_STALE)

        val staleData = ok(repository.prepareImport(next))
        database.openHelper.writableDatabase.execSQL("UPDATE courses SET name = '同数不同内容' WHERE name = '原课'")
        err(repository.commitImport(staleData.previewId,
            ImportConfirmation(staleData.previewId, staleData.scope!!, 1, 1)), DataErrorCode.PREVIEW_STALE)
        assertEquals(listOf("同数不同内容"), ok(repository.observeWeek(target.id, 1).first()).arrangements.map { it.name })
        val discarded = ok(repository.prepareImport(next))
        ok(repository.discardImport(discarded.previewId))
        err(repository.commitImport(discarded.previewId,
            ImportConfirmation(discarded.previewId, discarded.scope!!, 1, 1)), DataErrorCode.PREVIEW_STALE)
    }

    @Test fun failedInsertionRollsBackDeletionBatchAndWeeks() = runBlocking {
        val target = semester()
        val first = save(request(target.id, listOf(row(0, "原课", weeks = setOf(1, 3)))) )
        val before = ok(repository.observeWeek(target.id, 1).first()).arrangements.single()
        val preview = ok(repository.prepareImport(request(target.id, listOf(row(0, "新课")))))
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_school_insert BEFORE INSERT ON courses
            WHEN NEW.originType = 'SCHOOL_IMPORT' BEGIN SELECT RAISE(ABORT, 'synthetic insert failure'); END
        """.trimIndent())
        err(repository.commitImport(preview.previewId,
            ImportConfirmation(preview.previewId, preview.scope!!, 1, 1)), DataErrorCode.STORAGE_WRITE_FAILED)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_school_insert")
        val after = ok(repository.observeWeek(target.id, 1).first()).arrangements.single()
        assertEquals(before.id, after.id)
        assertEquals(setOf(1, 3), after.weeks)
        assertEquals(first.batchId, (after.origin as CourseOrigin.SchoolImport).batchId)
        assertEquals(1, database.importBatchDao().getBatch(first.batchId)!!.savedCount)
        val rolledBackStatus = ok(repository.observeImportStatus(target.id).first())
        assertEquals(first.batchId, rolledBackStatus.sources.single().latestBatch!!.id)
        assertEquals(1, rolledBackStatus.importedArrangementCount)
        val recovered = ok(repository.commitImport(preview.previewId,
            ImportConfirmation(preview.previewId, preview.scope!!, 1, 1)))
        assertEquals(1, recovered.savedCount)
        assertEquals("新课", ok(repository.observeWeek(target.id, 1).first()).arrangements.single().name)
        assertEquals(recovered.batchId, ok(repository.observeImportStatus(target.id).first()).sources.single().latestBatch!!.id)
    }

    @Test fun failedFirstImportLeavesNoBindingOrPartialCourses() = runBlocking {
        val target = semester()
        val preview = ok(repository.prepareImport(request(target.id, listOf(row(0, "第一条"), row(1, "第二条", 2)))))
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_second_import BEFORE INSERT ON courses
            WHEN NEW.originType = 'SCHOOL_IMPORT' AND NEW.name = '第二条'
            BEGIN SELECT RAISE(ABORT, 'synthetic second-row failure'); END
        """.trimIndent())
        err(repository.commitImport(preview.previewId,
            ImportConfirmation(preview.previewId, preview.scope!!, 0, 2)), DataErrorCode.STORAGE_WRITE_FAILED)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_second_import")
        assertTrue(ok(repository.observeWeek(target.id, 1).first()).arrangements.isEmpty())
        assertTrue(database.semesterDao().getSemester(target.id)!!.sourceBindings.isEmpty())
        val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM import_batches")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }
}
