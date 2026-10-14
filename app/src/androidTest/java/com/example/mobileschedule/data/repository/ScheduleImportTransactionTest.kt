package com.example.mobileschedule.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.*
import com.example.mobileschedule.data.model.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleImportTransactionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineScheduleRepository
    private lateinit var management: ScheduleManagementService

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = OfflineScheduleRepository(db)
        management = ScheduleManagementService(db)
    }

    @After
    fun close() {
        db.close()
    }

    private fun samplePreview(
        displayName: String = "2026-2027学年第一学期",
        existingId: Long? = null,
        courses: List<ArrangementBackupItem> = listOf(
            ArrangementBackupItem(
                name = "操作系统",
                teacher = "王老师",
                location = "计算机楼302",
                dayOfWeek = 2,
                startSection = 1,
                endSection = 2,
                weeks = setOf(1, 2, 3, 4),
                origin = CourseOriginDraft.Manual
            )
        )
    ): SemesterImportPreview {
        return SemesterImportPreview(
            displayName = displayName,
            firstWeekMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 18,
            totalSections = 8,
            sectionTimes = listOf(
                SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
                SectionTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
                SectionTime(3, LocalTime.of(9, 55), LocalTime.of(10, 40)),
                SectionTime(4, LocalTime.of(10, 45), LocalTime.of(11, 30)),
                SectionTime(5, LocalTime.of(14, 0), LocalTime.of(14, 45)),
                SectionTime(6, LocalTime.of(14, 50), LocalTime.of(15, 35)),
                SectionTime(7, LocalTime.of(15, 55), LocalTime.of(16, 40)),
                SectionTime(8, LocalTime.of(16, 45), LocalTime.of(17, 30)),
            ),
            arrangementCount = courses.size,
            existingSemesterId = existingId,
            existingSemesterName = if (existingId != null) displayName else null,
            arrangements = courses
        )
    }

    @Test
    fun importAsNewSemesterCreatesSemesterAndArrangements() = runBlocking {
        val preview = samplePreview()
        val commitResult = management.commitImport(preview, overwriteExisting = false)
        assertTrue(commitResult.isSuccess)
        val semesterId = commitResult.getOrThrow()

        // Verify semester was created in Room
        val semester = db.semesterDao().getSemester(semesterId)?.toModel()
        assertNotNull(semester)
        assertEquals("2026-2027学年第一学期", semester!!.displayName)
        assertNotNull(semester.config)
        assertEquals(18, semester.config!!.totalWeeks)
        assertEquals(8, semester.config!!.totalSections)
        assertEquals(LocalDate.of(2026, 9, 7), semester.config!!.firstWeekMonday)
        assertEquals(8, semester.config!!.sectionTimes.size)

        // Verify courses and weeks
        val courses = db.courseDao().getSemesterCourses(semesterId).map { it.toArrangement() }
        assertEquals(1, courses.size)
        assertEquals("操作系统", courses[0].name)
        assertEquals("王老师", courses[0].teacher)
        assertEquals(setOf(1, 2, 3, 4), courses[0].weeks)

        // Verify it was activated since no previous active semester existed
        val activeSemester = (repository.observeActiveSemester().first() as RepoResult.Ok).value
        assertNotNull(activeSemester)
        assertEquals(semesterId, activeSemester!!.id)
    }

    @Test
    fun overwriteExistingSemesterReplacesDataInTransaction() = runBlocking {
        // 1. Create original semester with 2 courses
        val originalDraft = SemesterConfigDraft(
            displayName = "旧学期",
            firstWeekMonday = LocalDate.of(2026, 3, 2),
            totalWeeks = 16,
            totalSections = 6,
            sectionTimes = emptyList()
        )
        val originalSemester = (repository.createSemester(originalDraft) as RepoResult.Ok).value
        val oldCourseId = db.courseDao().insertCourses(listOf(
            CourseEntity(
                name = "旧课程A", teacher = null, location = null,
                dayOfWeek = 1, startSection = 1, endSection = 2,
                semesterId = originalSemester.id, originType = CourseOriginType.MANUAL
            ),
            CourseEntity(
                name = "旧课程B", teacher = null, location = null,
                dayOfWeek = 2, startSection = 3, endSection = 4,
                semesterId = originalSemester.id, originType = CourseOriginType.MANUAL
            )
        ))
        db.courseDao().insertWeeks(listOf(
            CourseWeekEntity(oldCourseId[0], 1),
            CourseWeekEntity(oldCourseId[1], 2)
        ))
        assertEquals(2, db.courseDao().getSemesterCourses(originalSemester.id).size)

        // 2. Overwrite with imported preview
        val newCourses = listOf(
            ArrangementBackupItem(
                name = "新导入课程",
                teacher = "新老师",
                location = "新教室",
                dayOfWeek = 4,
                startSection = 1,
                endSection = 2,
                weeks = setOf(1, 3, 5),
                origin = CourseOriginDraft.Manual
            )
        )
        val preview = samplePreview(
            displayName = "旧学期",
            existingId = originalSemester.id,
            courses = newCourses
        )

        val commitResult = management.commitImport(preview, overwriteExisting = true)
        assertTrue(commitResult.isSuccess)
        assertEquals(originalSemester.id, commitResult.getOrThrow())

        // 3. Verify old courses were purged and replaced by new courses
        val currentCourses = db.courseDao().getSemesterCourses(originalSemester.id).map { it.toArrangement() }
        assertEquals(1, currentCourses.size)
        assertEquals("新导入课程", currentCourses[0].name)
        assertEquals(setOf(1, 3, 5), currentCourses[0].weeks)

        // 4. Verify config was updated to the imported configuration
        val updatedSemester = db.semesterDao().getSemester(originalSemester.id)?.toModel()
        assertEquals(18, updatedSemester!!.config!!.totalWeeks)
        assertEquals(8, updatedSemester.config!!.totalSections)
        assertEquals(LocalDate.of(2026, 9, 7), updatedSemester.config!!.firstWeekMonday)
    }

    @Test
    fun nonOverwriteModeGeneratesUniqueSemesterNameAndPreservesOriginal() = runBlocking {
        // 1. Create original semester
        val originalDraft = SemesterConfigDraft(
            displayName = "同名学期",
            firstWeekMonday = LocalDate.of(2026, 3, 2),
            totalWeeks = 16,
            totalSections = 6,
            sectionTimes = emptyList()
        )
        val originalSemester = (repository.createSemester(originalDraft) as RepoResult.Ok).value
        val originalCourseId = db.courseDao().insertCourses(listOf(
            CourseEntity(
                name = "原始课程", teacher = null, location = null,
                dayOfWeek = 1, startSection = 1, endSection = 2,
                semesterId = originalSemester.id, originType = CourseOriginType.MANUAL
            )
        )).single()
        db.courseDao().insertWeeks(listOf(CourseWeekEntity(originalCourseId, 1)))

        // 2. Import backup with same name but overwriteExisting = false
        val preview = samplePreview(
            displayName = "同名学期",
            existingId = originalSemester.id,
            courses = listOf(
                ArrangementBackupItem(
                    name = "新备份课程",
                    teacher = null, location = null,
                    dayOfWeek = 5, startSection = 1, endSection = 2,
                    weeks = setOf(2), origin = CourseOriginDraft.Manual
                )
            )
        )

        val commitResult = management.commitImport(preview, overwriteExisting = false)
        assertTrue(commitResult.isSuccess)
        val newSemesterId = commitResult.getOrThrow()
        assertNotEquals(originalSemester.id, newSemesterId)

        // 3. Verify original semester is untouched
        val originalAfter = db.semesterDao().getSemester(originalSemester.id)?.toModel()
        assertEquals("同名学期", originalAfter!!.displayName)
        val originalCourses = db.courseDao().getSemesterCourses(originalSemester.id)
        assertEquals(1, originalCourses.size)
        assertEquals("原始课程", originalCourses[0].course.name)

        // 4. Verify new semester has unique name
        val newSemester = db.semesterDao().getSemester(newSemesterId)?.toModel()
        assertEquals("同名学期 (导入)", newSemester!!.displayName)
        val newSemesterCourses = db.courseDao().getSemesterCourses(newSemesterId)
        assertEquals(1, newSemesterCourses.size)
        assertEquals("新备份课程", newSemesterCourses[0].course.name)
    }

    @Test
    fun failedTransactionRollsBackCompletelyWithoutCorruptingDatabase() = runBlocking {
        // Create an initial semester
        val initialDraft = SemesterConfigDraft(
            displayName = "现有学期",
            firstWeekMonday = LocalDate.of(2026, 9, 7),
            totalWeeks = 20,
            totalSections = 8,
            sectionTimes = emptyList()
        )
        val initialSemester = (repository.createSemester(initialDraft) as RepoResult.Ok).value
        val initialCourseId = db.courseDao().insertCourses(listOf(
            CourseEntity(
                name = "初始课程", teacher = null, location = null,
                dayOfWeek = 1, startSection = 1, endSection = 2,
                semesterId = initialSemester.id, originType = CourseOriginType.MANUAL
            )
        )).single()
        db.courseDao().insertWeeks(listOf(CourseWeekEntity(initialCourseId, 1)))

        // Attempt overwrite with non-existent existingSemesterId
        val brokenPreview = samplePreview(
            displayName = "破坏性学期",
            existingId = 999999L // Non-existent ID
        )

        val commitResult = management.commitImport(brokenPreview, overwriteExisting = true)
        assertTrue(commitResult.isFailure)

        // Verify database is completely intact
        val intactSemester = db.semesterDao().getSemester(initialSemester.id)?.toModel()
        assertNotNull(intactSemester)
        assertEquals("现有学期", intactSemester!!.displayName)
        val intactCourses = db.courseDao().getSemesterCourses(initialSemester.id)
        assertEquals(1, intactCourses.size)
        assertEquals("初始课程", intactCourses[0].course.name)

        // Foreign keys check
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }
}
