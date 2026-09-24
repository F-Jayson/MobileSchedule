package com.example.mobileschedule.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.local.entity.SemesterEntity
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.RuleIssueCode
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.WeekPosition
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
class ScheduleRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "schedule-rules-test.db"
    private val monday = LocalDate.of(2026, 9, 7)
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before fun open() {
        context.deleteDatabase(name)
        database = AppDatabase.open(context, name)
        repository = OfflineScheduleRepository(database)
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(name)
    }

    private fun draft(weeks: Int = 12, sections: Int = 4, name: String = " 合成学期 ") =
        SemesterConfigDraft(name, monday, weeks, sections, listOf(
            SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            SectionTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
            SectionTime(3, LocalTime.of(9, 40), LocalTime.of(10, 25)),
            SectionTime(4, LocalTime.of(10, 30), LocalTime.of(11, 15)),
        ).take(sections))

    private fun <T> ok(result: RepoResult<T>): T {
        assertTrue("Expected Ok, got $result", result is RepoResult.Ok)
        return (result as RepoResult.Ok).value
    }

    private fun <T> error(result: RepoResult<T>, code: DataErrorCode): Int? {
        assertTrue("Expected Err, got $result", result is RepoResult.Err)
        val err = (result as RepoResult.Err).error
        assertEquals(code, err.code)
        return err.affectedCount
    }

    @Test fun createActivateAndReadConfigurationSurviveRestart() = runBlocking {
        assertTrue(ok(repository.observeSemesters().first()).isEmpty())
        assertNull(ok(repository.observeActiveSemester().first()))
        val semester = ok(repository.createSemester(draft()))
        assertEquals("合成学期", semester.displayName)
        assertEquals(1L, semester.config!!.revision)
        assertNull(ok(repository.observeActiveSemester().first()))
        ok(repository.setActiveSemester(semester.id))
        assertEquals(semester.id, ok(repository.observeActiveSemester().first())!!.id)

        database.close()
        database = AppDatabase.open(context, name)
        repository = OfflineScheduleRepository(database)
        val saved = ok(repository.observeSemesterConfig(semester.id).first())!!
        assertEquals(monday, saved.firstWeekMonday)
        assertEquals(12, saved.totalWeeks)
        assertEquals(listOf(1, 2, 3, 4), saved.sectionTimes.map { it.section })
        assertEquals(LocalTime.of(11, 15), saved.sectionTimes.last().end)
        assertEquals(semester.id, ok(repository.observeActiveSemester().first())!!.id)
        assertEquals(WeekPosition.BeforeSemester, ok(repository.weekPosition(semester.id, monday.minusDays(1))))
        assertEquals(WeekPosition.InSemester(1), ok(repository.weekPosition(semester.id, monday)))
        assertEquals(WeekPosition.InSemester(1), ok(repository.weekPosition(semester.id, monday.plusDays(6))))
        assertEquals(WeekPosition.InSemester(2), ok(repository.weekPosition(semester.id, monday.plusDays(7))))
        assertEquals(WeekPosition.AfterSemester, ok(repository.weekPosition(semester.id, monday.plusWeeks(12))))
    }

    @Test fun weekObservationKeepsSparseWeeksAndSeparatesEmptyAndInvalidWeeks() = runBlocking {
        val semester = ok(repository.createSemester(draft()))
        val other = ok(repository.createSemester(draft(name = "另一学期")))
        val row = CourseEntity(name = "同名", teacher = null, location = null, dayOfWeek = 2,
            startSection = 1, endSection = 2, semesterId = semester.id, originType = CourseOriginType.MANUAL)
        val ids = database.courseDao().insertCourses(listOf(row, row.copy(location = "B101"), row.copy(semesterId = other.id)))
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(ids[0], 1), CourseWeekEntity(ids[0], 3),
            CourseWeekEntity(ids[0], 9), CourseWeekEntity(ids[1], 3), CourseWeekEntity(ids[2], 3)))

        val week3 = ok(repository.observeWeek(semester.id, 3).first())
        assertEquals(monday.plusWeeks(2), week3.monday)
        assertEquals(monday.plusWeeks(2).plusDays(6), week3.sunday)
        assertEquals(listOf(ids[0], ids[1]), week3.arrangements.map { it.id })
        assertEquals(setOf(1, 3, 9), week3.arrangements.first().weeks)
        assertTrue(ok(repository.observeWeek(semester.id, 2).first()).arrangements.isEmpty())
        error(repository.observeWeek(semester.id, 0).first(), DataErrorCode.WEEK_OUT_OF_RANGE)
        error(repository.observeWeek(semester.id, 13).first(), DataErrorCode.WEEK_OUT_OF_RANGE)
        error(repository.observeWeek(999, 1).first(), DataErrorCode.SEMESTER_NOT_FOUND)
        Unit
    }

    @Test fun invalidAndStaleEditsDoNotReplaceStoredConfiguration() = runBlocking {
        error(repository.createSemester(draft(name = " ")), DataErrorCode.CONFIG_INVALID)
        assertTrue(ok(repository.observeSemesters().first()).isEmpty())
        val semester = ok(repository.createSemester(draft()))
        val invalid = draft(name = "  ")
        val invalidResult = repository.saveSemesterConfig(semester.id, 1, invalid)
        error(invalidResult, DataErrorCode.CONFIG_INVALID)
        assertTrue((invalidResult as RepoResult.Err).error.issues.any { it.code == RuleIssueCode.DISPLAY_NAME_REQUIRED })
        error(repository.saveSemesterConfig(semester.id, 0, draft(name = "错误修订")), DataErrorCode.CONFIG_CONFLICT)

        val changed = ok(repository.saveSemesterConfig(semester.id, 1, draft(name = "修改后")))
        assertEquals(2L, changed.revision)
        assertEquals("修改后", ok(repository.observeSemesters().first()).single().displayName)
        error(repository.saveSemesterConfig(semester.id, 1, draft(name = "过期写入")), DataErrorCode.CONFIG_CONFLICT)
        assertEquals("修改后", ok(repository.observeSemesters().first()).single().displayName)
        assertEquals(2L, ok(repository.observeSemesterConfig(semester.id).first())!!.revision)

        val unchanged = ok(repository.saveSemesterConfig(semester.id, 2, draft(name = "修改后")))
        assertEquals(2L, unchanged.revision)
        val withoutTimes = ok(repository.saveSemesterConfig(semester.id, 2, draft(name = "修改后").copy(sectionTimes = emptyList())))
        assertEquals(3L, withoutTimes.revision)
        assertTrue(withoutTimes.sectionTimes.isEmpty())
        assertTrue(database.semesterDao().getSemester(semester.id)!!.sectionTimes.isEmpty())
    }

    @Test fun shorteningWeekAndSectionLimitsReportsDistinctAffectedRowsWithoutChangingAnything() = runBlocking {
        val semester = ok(repository.createSemester(draft()))
        val byWeek = CourseEntity(name = "第九周", teacher = null, location = null, dayOfWeek = 1,
            startSection = 1, endSection = 2, semesterId = semester.id, originType = CourseOriginType.LEGACY)
        val ids = database.courseDao().insertCourses(listOf(byWeek, byWeek.copy(name = "第四节", endSection = 4),
            byWeek.copy(name = "两个越界", endSection = 4)))
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(ids[0], 9), CourseWeekEntity(ids[1], 2), CourseWeekEntity(ids[2], 9)))
        val smaller = draft(weeks = 8, sections = 3, name = "不应保存")
        assertEquals(3, error(repository.saveSemesterConfig(semester.id, 1, smaller), DataErrorCode.CONFIG_CONFLICT))
        val unchanged = ok(repository.observeSemesterConfig(semester.id).first())!!
        assertEquals(12, unchanged.totalWeeks)
        assertEquals(4, unchanged.totalSections)
        assertEquals(1L, unchanged.revision)
        assertEquals("合成学期", ok(repository.observeSemesters().first()).single().displayName)
        assertEquals(setOf(9), database.courseDao().getCourse(ids[0])!!.weeks.map { it.week }.toSet())

        val extended = ok(repository.saveSemesterConfig(semester.id, 1, draft(weeks = 13, name = "扩展后")))
        assertEquals(2L, extended.revision)
        assertEquals(13, extended.totalWeeks)
        assertEquals(3, database.courseDao().observeSemesterCourses(semester.id).first().size)
    }

    @Test fun unconfiguredLegacySemesterMustBeEditedInPlaceAndActiveTargetMustExist() = runBlocking {
        val legacyId = database.semesterDao().insertSemester(SemesterEntity(displayName = "历史课程（待配置）"))
        val legacy = CourseEntity(name = "原课", teacher = "", location = "", dayOfWeek = 3,
            startSection = 3, endSection = 4, semesterId = legacyId, originType = CourseOriginType.LEGACY)
        val courseId = database.courseDao().insertCourses(listOf(legacy)).single()
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(courseId, 9)))
        assertNull(ok(repository.observeSemesterConfig(legacyId).first()))
        error(repository.weekPosition(legacyId, monday), DataErrorCode.CONFIG_REQUIRED)
        error(repository.observeWeek(legacyId, 1).first(), DataErrorCode.CONFIG_REQUIRED)
        assertEquals(1, error(repository.saveSemesterConfig(legacyId, 0, draft(weeks = 8)), DataErrorCode.CONFIG_CONFLICT))
        assertNull(ok(repository.observeSemesterConfig(legacyId).first()))
        val configured = ok(repository.saveSemesterConfig(legacyId, 0, draft()))
        assertEquals(1L, configured.revision)
        assertEquals(1, ok(repository.observeSemesters().first()).size)
        assertEquals(legacyId, ok(repository.observeWeek(legacyId, 9).first()).semesterId)
        error(repository.setActiveSemester(999), DataErrorCode.SEMESTER_NOT_FOUND)
        assertNull(ok(repository.observeActiveSemester().first()))
        error(repository.observeSemesterConfig(999).first(), DataErrorCode.SEMESTER_NOT_FOUND)
        ok(repository.setActiveSemester(legacyId))
        assertEquals(legacyId, ok(repository.observeActiveSemester().first())!!.id)
        error(repository.saveSemesterConfig(999, 0, draft()), DataErrorCode.SEMESTER_NOT_FOUND)
        Unit
    }
}
