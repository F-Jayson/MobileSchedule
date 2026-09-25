package com.example.mobileschedule.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.Course
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.repository.OfflineCourseRepository
import com.example.mobileschedule.data.repository.OfflineScheduleRepository
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.RepoResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun registeredMigrationPreservesLegacyIdsWeeksAndSequenceWithoutInventingConfiguration() {
        val name = "migration-populated-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO courses VALUES (8, '同名课程', '', '', 2, 1, 2)")
            execSQL("INSERT INTO courses VALUES (21, '同名课程', '教师', 'A101', 5, 3, 4)")
            execSQL("INSERT INTO course_weeks VALUES (8, 1), (8, 3), (8, 9), (21, 2)")
            execSQL("INSERT INTO courses VALUES (90, '已删除记录', '', '', 1, 1, 1)")
            execSQL("DELETE FROM courses WHERE id = 90")
            close()
        }
        val room = AppDatabase.open(context, name)
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(2, db.version)
            db.query("SELECT id, name, teacher, location, semesterId, originType FROM courses ORDER BY id").use {
                assertTrue(it.moveToFirst())
                assertEquals(8L, it.getLong(0))
                assertEquals("同名课程", it.getString(1))
                assertEquals("", it.getString(2))
                assertEquals("", it.getString(3))
                val legacySemester = it.getLong(4)
                assertEquals("LEGACY", it.getString(5))
                assertTrue(it.moveToNext())
                assertEquals(21L, it.getLong(0))
                assertEquals(legacySemester, it.getLong(4))
                assertFalse(it.moveToNext())
            }
            db.query("SELECT week FROM course_weeks WHERE courseId = 8 ORDER BY week").use {
                val weeks = buildList { while (it.moveToNext()) add(it.getInt(0)) }
                assertEquals(listOf(1, 3, 9), weeks)
            }
            for (table in listOf("semester_configs", "section_times", "source_bindings", "import_batches")) {
                db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            db.query("SELECT activeSemesterId FROM app_settings WHERE id = 0").use {
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
            runBlocking {
                assertEquals(
                    listOf(Course(8, "同名课程", "", "", 2, 1, 2, setOf(1, 3, 9)), Course(21, "同名课程", "教师", "A101", 5, 3, 4, setOf(2))),
                    OfflineCourseRepository(room.courseDao()).observeCourses().first(),
                )
                val semester = requireNotNull(room.semesterDao().observeActiveSemester().first()).toModel()
                assertEquals(null, semester.config)
                assertTrue(semester.sourceBindings.isEmpty())
                assertEquals(CourseOrigin.Legacy, room.courseDao().getCourse(8)!!.toArrangement().origin)
                val reads = OfflineScheduleRepository(room)
                val week = reads.observeActiveWeek(1).first()
                assertTrue(week is RepoResult.Err && week.error.code == DataErrorCode.CONFIG_REQUIRED)
                val detail = reads.observeCourseDetail(8).first() as RepoResult.Ok
                assertEquals(setOf(1, 3, 9), detail.value!!.arrangement.weeks)
                assertEquals(CourseOrigin.Legacy, detail.value!!.arrangement.origin)
                assertEquals(null, detail.value!!.startTime)
                val imports = reads.observeImportStatus(semester.id).first() as RepoResult.Ok
                assertTrue(imports.value.sources.isEmpty())
            }
            db.execSQL("INSERT INTO courses (name, teacher, location, dayOfWeek, startSection, endSection, semesterId, originType) VALUES ('新增', NULL, NULL, 1, 1, 1, 1, 'MANUAL')")
            db.query("SELECT id FROM courses WHERE name = '新增'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getLong(0) > 90L)
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun emptyV1RemainsUnconfiguredAndEmptyAfterUpgrade() {
        val name = "migration-empty-test.db"
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 2, true, DatabaseMigrations.MIGRATION_1_2).close()
        val room = AppDatabase.open(context, name)
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(2, db.version)
            for (table in listOf("courses", "course_weeks", "semesters", "semester_configs", "section_times", "source_bindings", "import_batches", "app_settings")) {
                db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
