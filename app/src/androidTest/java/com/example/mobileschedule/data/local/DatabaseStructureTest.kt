package com.example.mobileschedule.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.entity.*
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SourceScope
import com.example.mobileschedule.data.repository.OfflineCourseRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseStructureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "structure-test.db"
    private lateinit var database: AppDatabase

    @Before fun open() {
        context.deleteDatabase(name)
        database = AppDatabase.open(context, name)
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun freshDatabaseRoundTripsConfigurationSourcesAndDistinctArrangementsAfterReopen() = runBlocking {
        val semesters = database.semesterDao()
        assertTrue(semesters.observeSemesters().first().isEmpty())
        assertNull(semesters.observeActiveSemester().first())
        val semesterId = semesters.insertSemester(SemesterEntity(displayName = "合成测试学期"))
        assertNull(semesters.getSemester(semesterId)!!.toModel().config)
        val monday = LocalDate.of(2026, 9, 7)
        semesters.insertConfig(SemesterConfigEntity(semesterId, monday.toEpochDay(), 18, 2, 1))
        semesters.insertSectionTimes(listOf(SectionTimeEntity(semesterId, 2, 550, 595), SectionTimeEntity(semesterId, 1, 490, 535)))
        semesters.setActiveSemester(semesterId)
        semesters.insertSourceBinding(SourceBindingEntity("synthetic", "channel", "term", semesterId, "来源标签"))
        val batch = ImportBatchEntity(UUID.randomUUID().toString(), semesterId, "synthetic", "channel", "term", 1_000L, 2, 0)
        database.importBatchDao().insertBatch(batch)
        val first = CourseEntity(name = "同名", teacher = null, location = null, dayOfWeek = 2, startSection = 1, endSection = 2,
            semesterId = semesterId, originType = CourseOriginType.SCHOOL_IMPORT, importBatchId = batch.id)
        val ids = database.courseDao().insertCourses(listOf(first, first.copy(dayOfWeek = 4, location = "B101")))
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(ids[0], 1), CourseWeekEntity(ids[0], 3), CourseWeekEntity(ids[0], 9), CourseWeekEntity(ids[1], 2)))

        database.close()
        database = AppDatabase.open(context, name)
        val semester = database.semesterDao().observeActiveSemester().first()!!.toModel()
        assertEquals(semesterId, semester.id)
        assertEquals(monday, semester.config!!.firstWeekMonday)
        assertEquals(listOf(1, 2), semester.config.sectionTimes.map { it.section })
        assertEquals(LocalTime.of(8, 10), semester.config.sectionTimes.first().start)
        assertEquals(setOf(SourceScope("synthetic", "channel", "term")), semester.sourceBindings)
        assertEquals("来源标签", database.semesterDao().getSemester(semesterId)!!.sourceBindings.single().sourceTermLabel)
        assertEquals(batch, database.importBatchDao().getBatch(batch.id))
        val rows = database.courseDao().observeSemesterCourses(semesterId).first().map { it.toArrangement() }
        assertEquals(2, rows.size)
        assertNotEquals(rows[0].id, rows[1].id)
        assertEquals(listOf(1, 3, 9), rows[0].weeks.toList())
        assertNull(rows[0].teacher)
        assertNull(rows[0].location)
        assertEquals(CourseOrigin.SchoolImport(SourceScope("synthetic", "channel", "term"), batch.id, null), rows[0].origin)
        assertEquals(rows[0], database.courseDao().getCourse(ids[0])!!.toArrangement())
        assertEquals(2, OfflineCourseRepository(database.courseDao()).observeCourses().first().size)
    }

    @Test
    fun foreignKeysRejectOrphansScopeRebindingAndCrossSemesterBatches() = runBlocking {
        val semesters = database.semesterDao()
        val a = semesters.insertSemester(SemesterEntity(displayName = "A"))
        val b = semesters.insertSemester(SemesterEntity(displayName = "B"))
        val binding = SourceBindingEntity("synthetic", "channel", "term", a, null)
        semesters.insertSourceBinding(binding)
        expectConstraint { semesters.insertSourceBinding(binding.copy(semesterId = b)) }
        val batch = ImportBatchEntity(UUID.randomUUID().toString(), a, "synthetic", "channel", "term", 0, 1, 0)
        expectConstraint { database.importBatchDao().insertBatch(batch.copy(semesterId = b)) }
        database.importBatchDao().insertBatch(batch)
        val imported = CourseEntity(name = "课", teacher = null, location = null, dayOfWeek = 1, startSection = 1, endSection = 1,
            semesterId = a, originType = CourseOriginType.SCHOOL_IMPORT, importBatchId = batch.id)
        expectConstraint { database.courseDao().insertCourses(listOf(imported.copy(semesterId = b))) }
        expectConstraint { database.courseDao().insertCourses(listOf(imported.copy(importBatchId = "missing"))) }
        expectConstraint { semesters.insertSectionTimes(listOf(SectionTimeEntity(a, 1, 480, 525))) }
        expectConstraint { database.courseDao().insertWeeks(listOf(CourseWeekEntity(999, 1))) }
        semesters.setActiveSemester(a)
        expectConstraint { semesters.setActiveSemester(999) }
        assertEquals(a, semesters.observeActiveSemester().first()!!.semester.id)
        val id = database.courseDao().insertCourses(listOf(imported)).single()
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(id, 1)))
        expectConstraint { database.courseDao().insertWeeks(listOf(CourseWeekEntity(id, 1))) }
        assertEquals(setOf(1), database.courseDao().getCourse(id)!!.toArrangement().weeks)
    }

    @Test
    fun deletionPreservesOwnedArrangementsAndCascadesOnlyTheirWeeks() = runBlocking {
        val semesters = database.semesterDao()
        val a = semesters.insertSemester(SemesterEntity(displayName = "A"))
        val b = semesters.insertSemester(SemesterEntity(displayName = "B"))
        semesters.insertConfig(SemesterConfigEntity(a, LocalDate.of(2026, 9, 7).toEpochDay(), 18, 1, 1))
        semesters.insertSectionTimes(listOf(SectionTimeEntity(a, 1, 480, 525)))
        val course = CourseEntity(name = "手动", teacher = null, location = null, dayOfWeek = 1, startSection = 1, endSection = 1,
            semesterId = a, originType = CourseOriginType.MANUAL)
        val id = database.courseDao().insertCourses(listOf(course)).single()
        val other = database.courseDao().insertCourses(listOf(course.copy(semesterId = b))).single()
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(id, 1), CourseWeekEntity(other, 3)))
        val sql = database.openHelper.writableDatabase
        expectConstraint { sql.execSQL("DELETE FROM semesters WHERE id = ?", arrayOf(a)) }
        sql.execSQL("DELETE FROM courses WHERE id = ?", arrayOf(id))
        sql.query("SELECT COUNT(*) FROM course_weeks WHERE courseId = ?", arrayOf(id)).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        assertEquals(setOf(3), database.courseDao().getCourse(other)!!.toArrangement().weeks)
        semesters.setActiveSemester(a)
        sql.execSQL("DELETE FROM semesters WHERE id = ?", arrayOf(a))
        assertNull(semesters.observeActiveSemester().first())
        for (table in listOf("semester_configs", "section_times")) {
            sql.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
        sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    private suspend fun expectConstraint(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected SQLite foreign-key or unique constraint rejection")
        } catch (_: SQLiteConstraintException) {
            // Expected. Other exceptions must still fail the test.
        }
    }
}
