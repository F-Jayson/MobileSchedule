package com.example.mobileschedule.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.model.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleReadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "schedule-read-test.db"
    private val monday = LocalDate.of(2026, 9, 7)
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

    private suspend fun <T> ReceiveChannel<T>.until(predicate: (T) -> Boolean): T = withTimeout(5_000) {
        var value = receive()
        while (!predicate(value)) value = receive()
        value
    }

    private fun times() = listOf(
        SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
        SectionTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
        SectionTime(3, LocalTime.of(9, 40), LocalTime.of(10, 25)),
        SectionTime(4, LocalTime.of(10, 30), LocalTime.of(11, 15)),
    )

    private fun draft(name: String = "合成学期", sectionTimes: List<SectionTime> = times()) =
        SemesterConfigDraft(name, monday, 12, 4, sectionTimes)

    private fun request(semesterId: Long, rows: List<ImportCandidate>, source: String = "dom") =
        ImportRequest(semesterId, "fjnu", source, "term-1", "合成来源学期", rows.size, rows.size, rows,
            emptyList(), CompletenessEvidence(CompletenessStatus.VERIFIED_FULL, "term-1",
                listOf(0), 1, rows.size, "synthetic full-page fixture"))

    private fun row(index: Int, name: String, weeks: Set<Int> = setOf(1, 3), section: Int = 1) =
        ImportCandidate(index, null, name, "教师", "A101", 2, section, section + 1, weeks)

    private suspend fun save(request: ImportRequest): ImportReceipt {
        val preview = ok(repository.prepareImport(request))
        assertTrue(preview.issues.toString(), preview.canCommit)
        return ok(repository.commitImport(preview.previewId, ImportConfirmation(
            preview.previewId, preview.scope!!, preview.replaceCount!!, preview.validCount)))
    }

    @Test fun activeWeekDistinguishesNoSemesterEmptyWeekAndOutOfRangeAndFollowsSwitch() = runBlocking {
        assertNull(ok(repository.observeActiveWeek(1).first()))
        val first = ok(repository.createSemester(draft()))
        val second = ok(repository.createSemester(draft("另一学期")))
        ok(repository.setActiveSemester(first.id))
        val active = ok(repository.observeActiveWeek(2).first())!!
        assertEquals(first.id, active.semester.id)
        assertEquals(monday.plusWeeks(1), active.schedule.monday)
        assertTrue(active.schedule.arrangements.isEmpty())
        err(repository.observeActiveWeek(13).first(), DataErrorCode.WEEK_OUT_OF_RANGE)

        val updates = repository.observeActiveWeek(1).produceIn(this)
        assertEquals(first.id, ok(updates.receive())!!.semester.id)
        ok(repository.setActiveSemester(second.id))
        val switched = ok(updates.until { it is RepoResult.Ok && it.value?.semester?.id == second.id })!!
        assertEquals("另一学期", switched.semester.displayName)
        updates.cancel()
    }

    @Test fun preexistingSubscribersReceiveImportAndReplacementWithSparseWeeks() = runBlocking {
        val semester = ok(repository.createSemester(draft()))
        ok(repository.setActiveSemester(semester.id))
        val weeks = repository.observeActiveWeek(1).produceIn(this)
        val imports = repository.observeImportStatus(semester.id).produceIn(this)
        assertTrue(ok(weeks.receive())!!.schedule.arrangements.isEmpty())
        assertTrue(ok(imports.receive()).sources.isEmpty())

        val first = save(request(semester.id, listOf(row(0, "离散周"), row(1, "第二周", setOf(2), 3))))
        val weekOne = ok(weeks.until { it is RepoResult.Ok && it.value?.schedule?.arrangements?.size == 1 })!!
        assertEquals(listOf("离散周"), weekOne.schedule.arrangements.map { it.name })
        assertEquals(setOf(1, 3), weekOne.schedule.arrangements.single().weeks)
        assertEquals(listOf("第二周"), ok(repository.observeActiveWeek(2).first())!!.schedule.arrangements.map { it.name })
        assertEquals(listOf("离散周"), ok(repository.observeActiveWeek(3).first())!!.schedule.arrangements.map { it.name })
        val status = ok(imports.until { it is RepoResult.Ok && it.value.importedArrangementCount == 2 })
        assertEquals(first.batchId, status.sources.single().latestBatch!!.id)
        assertEquals(2, status.sources.single().currentArrangementCount)

        val oldId = weekOne.schedule.arrangements.single().id
        val second = save(request(semester.id, listOf(row(0, "替换后"))))
        val updated = ok(weeks.until { it is RepoResult.Ok &&
            it.value?.schedule?.arrangements?.singleOrNull()?.name == "替换后" })!!
        assertEquals(second.batchId, (updated.schedule.arrangements.single().origin as CourseOrigin.SchoolImport).batchId)
        assertNull(ok(repository.observeCourseDetail(oldId).first()))
        val latest = ok(imports.until { it is RepoResult.Ok &&
            it.value.sources.singleOrNull()?.latestBatch?.id == second.batchId })
        assertEquals(1, latest.importedArrangementCount)
        assertEquals(1, latest.sources.single().currentArrangementCount)
        weeks.cancel()
        imports.cancel()
    }

    @Test fun detailShowsCompleteWeeksAndUpdatedSectionTimesAndThenBecomesUnavailable() = runBlocking {
        val semester = ok(repository.createSemester(draft()))
        save(request(semester.id, listOf(row(0, "课程详情", setOf(1, 3, 9)))))
        val id = ok(repository.observeWeek(semester.id, 1).first()).arrangements.single().id
        val details = repository.observeCourseDetail(id).produceIn(this)
        val original = ok(details.receive())!!
        assertEquals("合成学期", original.semesterDisplayName)
        assertEquals(setOf(1, 3, 9), original.arrangement.weeks)
        assertEquals(LocalTime.of(8, 0), original.startTime)
        assertEquals(LocalTime.of(9, 35), original.endTime)
        assertEquals(SourceScope("fjnu", "dom", "term-1"),
            (original.arrangement.origin as CourseOrigin.SchoolImport).scope)

        val changed = listOf(
            SectionTime(1, LocalTime.of(9, 0), LocalTime.of(9, 45)),
            SectionTime(2, LocalTime.of(9, 50), LocalTime.of(10, 35)),
            SectionTime(3, LocalTime.of(10, 40), LocalTime.of(11, 25)),
            SectionTime(4, LocalTime.of(11, 30), LocalTime.of(12, 15)),
        )
        ok(repository.saveSemesterConfig(semester.id, 1, draft("改名后", changed)))
        val refreshed = ok(details.until { it is RepoResult.Ok && it.value?.startTime == LocalTime.of(9, 0) })!!
        assertEquals("改名后", refreshed.semesterDisplayName)
        assertEquals(LocalTime.of(10, 35), refreshed.endTime)

        save(request(semester.id, listOf(row(0, "新课程"))))
        assertNull(ok(details.until { it is RepoResult.Ok && it.value == null }))
        details.cancel()
    }

    @Test fun reopenedDatabaseRestoresConfigurationDetailsAndLatestImportStatus() = runBlocking {
        val semester = ok(repository.createSemester(draft()))
        ok(repository.setActiveSemester(semester.id))
        save(request(semester.id, listOf(row(0, "范围A"))))
        val latest = save(request(semester.id, listOf(row(0, "范围B"))))
        save(request(semester.id, listOf(row(0, "其他通道")), "other-dom"))
        database.close()
        database = AppDatabase.open(context, dbName)
        repository = OfflineScheduleRepository(database)

        val active = ok(repository.observeActiveWeek(1).first())!!
        assertEquals(semester.id, active.semester.id)
        assertEquals(setOf("范围B", "其他通道"), active.schedule.arrangements.map { it.name }.toSet())
        assertEquals(monday, active.semester.config!!.firstWeekMonday)
        assertEquals(times(), ok(repository.observeSemesterConfig(semester.id).first())!!.sectionTimes)
        val detail = ok(repository.observeCourseDetail(active.schedule.arrangements.first().id).first())!!
        assertEquals(LocalTime.of(8, 0), detail.startTime)
        val status = ok(repository.observeImportStatus(semester.id).first())
        assertEquals(2, status.importedArrangementCount)
        assertEquals(2, status.sources.size)
        val dom = status.sources.single { it.scope.sourceId == "dom" }
        assertEquals(latest.batchId, dom.latestBatch!!.id)
        assertEquals(1, dom.currentArrangementCount)
        err(repository.observeImportStatus(999).first(), DataErrorCode.SEMESTER_NOT_FOUND)
    }
}
