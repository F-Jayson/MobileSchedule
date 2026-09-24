package com.example.mobileschedule.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val previousSequence = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'courses'").use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS semesters (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, displayName TEXT NOT NULL)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS semester_configs (
                    semesterId INTEGER NOT NULL PRIMARY KEY, firstWeekMonday INTEGER NOT NULL,
                    totalWeeks INTEGER NOT NULL, totalSections INTEGER NOT NULL, revision INTEGER NOT NULL,
                    FOREIGN KEY(semesterId) REFERENCES semesters(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS section_times (
                    semesterId INTEGER NOT NULL, section INTEGER NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL,
                    PRIMARY KEY(semesterId, section),
                    FOREIGN KEY(semesterId) REFERENCES semester_configs(semesterId) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS source_bindings (
                    schoolId TEXT NOT NULL, sourceId TEXT NOT NULL, sourceTermId TEXT NOT NULL,
                    semesterId INTEGER NOT NULL, sourceTermLabel TEXT,
                    PRIMARY KEY(schoolId, sourceId, sourceTermId),
                    FOREIGN KEY(semesterId) REFERENCES semesters(id) ON UPDATE NO ACTION ON DELETE RESTRICT)
            """.trimIndent())
            db.execSQL("CREATE INDEX index_source_bindings_semesterId ON source_bindings(semesterId)")
            db.execSQL("CREATE UNIQUE INDEX index_source_bindings_schoolId_sourceId_sourceTermId_semesterId ON source_bindings(schoolId, sourceId, sourceTermId, semesterId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS import_batches (
                    id TEXT NOT NULL PRIMARY KEY, semesterId INTEGER NOT NULL, schoolId TEXT NOT NULL,
                    sourceId TEXT NOT NULL, sourceTermId TEXT NOT NULL, committedAt INTEGER NOT NULL,
                    savedCount INTEGER NOT NULL, removedCount INTEGER NOT NULL,
                    FOREIGN KEY(schoolId, sourceId, sourceTermId, semesterId)
                        REFERENCES source_bindings(schoolId, sourceId, sourceTermId, semesterId) ON UPDATE NO ACTION ON DELETE RESTRICT)
            """.trimIndent())
            db.execSQL("CREATE INDEX index_import_batches_schoolId_sourceId_sourceTermId_semesterId ON import_batches(schoolId, sourceId, sourceTermId, semesterId)")
            db.execSQL("CREATE UNIQUE INDEX index_import_batches_id_semesterId ON import_batches(id, semesterId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    id INTEGER NOT NULL PRIMARY KEY, activeSemesterId INTEGER,
                    FOREIGN KEY(activeSemesterId) REFERENCES semesters(id) ON UPDATE NO ACTION ON DELETE SET NULL)
            """.trimIndent())
            db.execSQL("CREATE INDEX index_app_settings_activeSemesterId ON app_settings(activeSemesterId)")

            // Only existing arrangements need a Legacy semester. Its configuration stays absent.
            db.execSQL("INSERT INTO semesters (id, displayName) SELECT 1, '历史课程（待配置）' WHERE EXISTS (SELECT 1 FROM courses)")
            db.execSQL("INSERT INTO app_settings (id, activeSemesterId) SELECT 0, 1 WHERE EXISTS (SELECT 1 FROM courses)")
            db.execSQL("""
                CREATE TABLE _courses_v2 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, teacher TEXT, location TEXT,
                    dayOfWeek INTEGER NOT NULL, startSection INTEGER NOT NULL, endSection INTEGER NOT NULL,
                    semesterId INTEGER NOT NULL, originType TEXT NOT NULL, importBatchId TEXT, sourceEntryId TEXT,
                    FOREIGN KEY(semesterId) REFERENCES semesters(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(importBatchId, semesterId) REFERENCES import_batches(id, semesterId) ON UPDATE NO ACTION ON DELETE RESTRICT)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO _courses_v2 (id, name, teacher, location, dayOfWeek, startSection, endSection, semesterId, originType)
                SELECT id, name, teacher, location, dayOfWeek, startSection, endSection, 1, 'LEGACY' FROM courses
            """.trimIndent())

            // Dropping the old parent first would cascade-delete weeks. Back them up before rebuilding.
            db.execSQL("CREATE TABLE _migration_course_weeks AS SELECT courseId, week FROM course_weeks")
            db.execSQL("DROP TABLE course_weeks")
            db.execSQL("DROP TABLE courses")
            db.execSQL("ALTER TABLE _courses_v2 RENAME TO courses")
            db.execSQL("CREATE INDEX index_courses_semesterId ON courses(semesterId)")
            db.execSQL("CREATE INDEX index_courses_importBatchId_semesterId ON courses(importBatchId, semesterId)")
            db.execSQL("""
                CREATE TABLE course_weeks (
                    courseId INTEGER NOT NULL, week INTEGER NOT NULL, PRIMARY KEY(courseId, week),
                    FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("INSERT INTO course_weeks SELECT courseId, week FROM _migration_course_weeks")
            db.execSQL("DROP TABLE _migration_course_weeks")

            // MAX(id) cannot preserve IDs of already deleted arrangements.
            db.execSQL("INSERT INTO sqlite_sequence(name, seq) SELECT 'courses', ? WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'courses')", arrayOf(previousSequence))
            db.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = 'courses'", arrayOf(previousSequence))
        }
    }
}
