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
            assertEquals(3, db.version)
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
            for (table in listOf("semester_configs", "section_times", "source_bindings", "import_batches", "course_tasks")) {
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
            assertEquals(3, db.version)
            for (table in listOf("courses", "course_weeks", "semesters", "semester_configs", "section_times", "source_bindings", "import_batches", "app_settings", "course_tasks")) {
                db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFrom2To3PreservesExistingDataAndCreatesCourseTasksTableWithCascadeDelete() {
        val name = "migration-v2-to-v3-test.db"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO semesters (id, displayName) VALUES (1, '2026秋季学期')")
            execSQL("INSERT INTO courses (id, name, teacher, location, dayOfWeek, startSection, endSection, semesterId, originType) VALUES (10, '高等数学', '李老师', '理科楼101', 1, 1, 2, 1, 'MANUAL')")
            execSQL("INSERT INTO course_weeks (courseId, week) VALUES (10, 1), (10, 2), (10, 3)")
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(name, 3, true, DatabaseMigrations.MIGRATION_2_3)
        try {
            assertEquals(3, migratedDb.version)
            // Verify existing course remains intact
            migratedDb.query("SELECT id, name, semesterId FROM courses WHERE id = 10").use {
                assertTrue(it.moveToFirst())
                assertEquals(10L, it.getLong(0))
                assertEquals("高等数学", it.getString(1))
                assertEquals(1L, it.getLong(2))
            }
            // Verify course_weeks remain intact
            migratedDb.query("SELECT COUNT(*) FROM course_weeks WHERE courseId = 10").use {
                assertTrue(it.moveToFirst())
                assertEquals(3, it.getInt(0))
            }
            // Insert tasks into new course_tasks table
            migratedDb.execSQL("""
                INSERT INTO course_tasks (courseId, title, content, deadlineEpochMillis, type, isCompleted, createdAt)
                VALUES (10, '大作业答辩', '准备10分钟PPT', 1729300000000, 'EXAM', 0, 1729200000000)
            """.trimIndent())
            migratedDb.query("SELECT id, courseId, title, content, type, isCompleted FROM course_tasks WHERE courseId = 10").use {
                assertTrue(it.moveToFirst())
                assertEquals(10L, it.getLong(1))
                assertEquals("大作业答辩", it.getString(2))
                assertEquals("准备10分钟PPT", it.getString(3))
                assertEquals("EXAM", it.getString(4))
                assertEquals(0, it.getInt(5))
            }

            // Verify foreign key integrity
            migratedDb.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }

            // Verify CASCADE delete: deleting parent course removes its tasks
            migratedDb.execSQL("PRAGMA foreign_keys = ON")
            migratedDb.execSQL("DELETE FROM courses WHERE id = 10")
            migratedDb.query("SELECT COUNT(*) FROM course_tasks WHERE courseId = 10").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            migratedDb.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            migratedDb.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun fullMigrationFrom1To3PreservesAllData() {
        val name = "migration-v1-to-v3-full.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO courses VALUES (8, '大学物理', '王老师', 'B202', 3, 1, 2)")
            execSQL("INSERT INTO course_weeks VALUES (8, 1), (8, 2)")
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(name, 3, true, DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
        try {
            assertEquals(3, migratedDb.version)
            migratedDb.query("SELECT name FROM courses WHERE id = 8").use {
                assertTrue(it.moveToFirst())
                assertEquals("大学物理", it.getString(0))
            }
            migratedDb.query("SELECT COUNT(*) FROM course_tasks").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            migratedDb.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            migratedDb.close()
            context.deleteDatabase(name)
        }
    }
}
