package com.example.mobileschedule.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.local.entity.ImportBatchEntity
import com.example.mobileschedule.data.local.entity.SourceBindingEntity
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.ImportConfirmation
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfigDraft
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SemesterDeletionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineScheduleRepository
    private lateinit var management: ScheduleManagementService

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java).build()
        repository = OfflineScheduleRepository(db)
        management = ScheduleManagementService(db)
    }

    @After fun close() { db.close() }

    private suspend fun semester(name: String): Semester = (repository.createSemester(SemesterConfigDraft(
        name, LocalDate.of(2026, 9, 7), 20, 2,
        listOf(SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            SectionTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35))))) as RepoResult.Ok).value

    private suspend fun addCourse(semesterId: Long, name: String, batchId: String? = null): Long {
        val id = db.courseDao().insertCourses(listOf(CourseEntity(
            name = name, teacher = "合成教师", location = "合成教室", dayOfWeek = 2,
            startSection = 1, endSection = 2, semesterId = semesterId,
            originType = if (batchId == null) CourseOriginType.MANUAL else CourseOriginType.SCHOOL_IMPORT,
            importBatchId = batchId))).single()
        db.courseDao().insertWeeks(listOf(CourseWeekEntity(id, 1), CourseWeekEntity(id, 3)))
        return id
    }

    private suspend fun addImportedCourse(semesterId: Long, batchId: String, termId: String): Long {
        db.semesterDao().insertSourceBinding(SourceBindingEntity(
            "fjnu", "fjnu-zhengfang-web", termId, semesterId, "合成来源"))
        db.importBatchDao().insertBatch(ImportBatchEntity(
            batchId, semesterId, "fjnu", "fjnu-zhengfang-web", termId, 0, 1, 0))
        return addCourse(semesterId, "合成教务课程", batchId)
    }

    private suspend fun activeId(): Long? = (repository.observeActiveSemester().first() as RepoResult.Ok).value?.id

    private fun assertNoForeignKeyViolations() {
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }

    @Test fun deletionRemovesRelatedDataAndPreservesOtherSemester() = runBlocking {
        val target = semester("待删除合成学期")
        val retained = semester("保留合成学期")
        repository.setActiveSemester(retained.id)
        val manualId = addCourse(target.id, "合成手动课程")
        val importedId = addImportedCourse(target.id, "target-batch", "target-term")
        val retainedCourseId = addImportedCourse(retained.id, "retained-batch", "retained-term")
        val retainedBefore = db.semesterDao().getSemester(retained.id)
        val retainedCourseBefore = db.courseDao().getCourse(retainedCourseId)

        management.deleteSemester(target.id)

        assertNull(db.semesterDao().getSemester(target.id))
        assertNull(db.courseDao().getCourse(manualId))
        assertNull(db.courseDao().getCourse(importedId))
        assertTrue(db.courseDao().getSemesterCourses(target.id).isEmpty())
        assertNull(db.importBatchDao().getBatch("target-batch"))
        assertNull(db.semesterDao().getSourceBinding("fjnu", "fjnu-zhengfang-web", "target-term"))
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM semester_configs WHERE semesterId = ${target.id}").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM section_times WHERE semesterId = ${target.id}").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM course_weeks WHERE courseId IN ($manualId, $importedId)").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        assertEquals(retainedBefore, db.semesterDao().getSemester(retained.id))
        assertEquals(retainedCourseBefore, db.courseDao().getCourse(retainedCourseId))
        assertEquals("retained-batch", db.importBatchDao().getBatch("retained-batch")!!.id)
        assertEquals(retained.id, activeId())
        assertNoForeignKeyViolations()
    }

    @Test fun deletingActiveSemesterSelectsMostRecentlyCreatedRemainingSemester() = runBlocking {
        val oldest = semester("较早合成学期")
        val target = semester("当前合成学期")
        val newest = semester("最近合成学期")
        repository.setActiveSemester(target.id)

        management.deleteSemester(target.id)

        assertEquals(newest.id, activeId())
        assertEquals(listOf(oldest.id, newest.id),
            (repository.observeSemesters().first() as RepoResult.Ok).value.map { it.id })
        assertNoForeignKeyViolations()
    }

    @Test fun deletingOnlyActiveSemesterLeavesEmptySchedule() = runBlocking {
        val target = semester("唯一合成学期")
        repository.setActiveSemester(target.id)
        addImportedCourse(target.id, "only-batch", "only-term")

        management.deleteSemester(target.id)

        assertNull(activeId())
        assertTrue((repository.observeSemesters().first() as RepoResult.Ok).value.isEmpty())
        assertNull((repository.observeActiveWeek(1).first() as RepoResult.Ok).value)
        assertNoForeignKeyViolations()
    }

    @Test fun deletingSemesterPreservesEmptyActiveSelection() = runBlocking {
        val target = semester("待删除合成学期")
        val retained = semester("保留合成学期")
        db.semesterDao().setActiveSemester(null)

        management.deleteSemester(target.id)

        assertNull(activeId())
        assertEquals(listOf(retained.id),
            (repository.observeSemesters().first() as RepoResult.Ok).value.map { it.id })
    }

    @Test fun deletingMissingSemesterPreservesExistingData() = runBlocking {
        val retained = semester("保留合成学期")
        repository.setActiveSemester(retained.id)
        val courseId = addCourse(retained.id, "保留合成课程")
        val before = db.semesterDao().getSemester(retained.id)

        val result = runCatching { management.deleteSemester(retained.id + 1) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, db.semesterDao().getSemester(retained.id))
        assertEquals("保留合成课程", db.courseDao().getCourse(courseId)!!.course.name)
        assertEquals(retained.id, activeId())
        assertNoForeignKeyViolations()
    }

    @Test fun deletingSemesterExpiresPreparedImportWithoutWritingIntoRemainingSemester() = runBlocking {
        val target = semester("预览目标合成学期")
        val retained = semester("保留合成学期")
        repository.setActiveSemester(target.id)
        val parsed = ZhengfangOnlineRead.withQuickImportAcceptance(ZhengfangOnlineRead.parse(
            """{"xsxx":{"XNM":"2026","XQM":"3"},"sjkList":[],"kbList":[{"kcmc":"预览合成课程","jc":"1-2节","xqj":"2","zcd":"1周"}]}""",
            target.id, ZhengfangOnlineRead.selectTerm("2026", 1)!!))!!
        val preview = (repository.prepareImport(parsed.request) as RepoResult.Ok).value
        assertTrue(preview.canCommit)

        management.deleteSemester(target.id)
        val result = repository.commitImport(preview.previewId, ImportConfirmation(preview.previewId,
            preview.scope!!, preview.replaceCount!!, preview.validCount))

        assertEquals(DataErrorCode.PREVIEW_STALE, (result as RepoResult.Err).error.code)
        assertNull(db.semesterDao().getSemester(target.id))
        assertTrue(db.courseDao().getSemesterCourses(retained.id).isEmpty())
        assertTrue(db.semesterDao().getSemester(retained.id)!!.sourceBindings.isEmpty())
        assertEquals(retained.id, activeId())
        assertNoForeignKeyViolations()
    }

    @Test fun failedActiveSelectionRollsBackSemesterDeletionAndDependentRows() = runBlocking {
        val target = semester("当前合成学期")
        val fallback = semester("备用合成学期")
        repository.setActiveSemester(target.id)
        val manualId = addCourse(target.id, "合成手动课程")
        val importedId = addImportedCourse(target.id, "rollback-batch", "rollback-term")
        val before = db.semesterDao().getSemester(target.id)
        val coursesBefore = db.courseDao().getSemesterCourses(target.id)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER abort_synthetic_active_selection BEFORE INSERT ON app_settings
            WHEN NEW.id = 0 AND NEW.activeSemesterId = ${fallback.id}
            BEGIN SELECT RAISE(ABORT, 'Synthetic active selection failure'); END
        """.trimIndent())

        val result = runCatching { management.deleteSemester(target.id) }

        assertFalse(result.isSuccess)
        assertEquals(before, db.semesterDao().getSemester(target.id))
        assertEquals(coursesBefore, db.courseDao().getSemesterCourses(target.id))
        assertEquals(setOf(1, 3), db.courseDao().getCourse(manualId)!!.weeks.map { it.week }.toSet())
        assertEquals(setOf(1, 3), db.courseDao().getCourse(importedId)!!.weeks.map { it.week }.toSet())
        assertEquals("rollback-batch", db.importBatchDao().getBatch("rollback-batch")!!.id)
        assertEquals(target.id, activeId())
        assertNoForeignKeyViolations()
    }
}
