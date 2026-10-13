package com.example.mobileschedule.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.ui.importentry.OnlineImportPreviewSession
import com.example.mobileschedule.ui.importentry.OnlineImportPreviewState
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickImportPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "quick-import-persistence-test.db"
    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineScheduleRepository

    @Before fun open() {
        context.deleteDatabase(databaseName)
        reopen()
    }

    private fun reopen() {
        database = AppDatabase.open(context, databaseName)
        repository = OfflineScheduleRepository(database)
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun <T> ok(result: RepoResult<T>): T = (result as RepoResult.Ok).value

    @Test fun quickImportReplacesMatchingSourceKeepsOtherCoursesAndSurvivesReopen() = runBlocking {
        val target = ok(repository.createSemester(SemesterConfigDraft("快速导入合成学期",
            LocalDate.of(2026, 9, 7), 20, 8, emptyList())))
        val other = ok(repository.createSemester(SemesterConfigDraft("保留合成学期",
            LocalDate.of(2026, 9, 7), 20, 8, emptyList())))
        ok(repository.setActiveSemester(other.id))
        val manualId = database.courseDao().insertCourses(listOf(CourseEntity(name = "手动保留课程",
            teacher = null, location = null, dayOfWeek = 1, startSection = 3, endSection = 4,
            semesterId = target.id, originType = CourseOriginType.MANUAL))).single()
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(manualId, 1)))

        suspend fun import(name: String, termNumber: Int): OnlineImportPreviewState.Saved {
            val term = ZhengfangOnlineRead.selectTerm("2026", termNumber)!!
            val row = """{"kcmc":"$name","jc":"1-2节","xqj":"2","zcd":"1-3周","xnm":"2026","xqm":"${term.xqm}"}"""
            val parsed = ZhengfangOnlineRead.parse("""{"xsxx":{"XNM":"2026","XQM":"${term.xqm}"},"sjkList":[],"kbList":[$row,$row]}""",
                target.id, term)
            val session = OnlineImportPreviewSession(this@runBlocking, repository::prepareImport,
                repository::discardImport, repository::commitImport, repository::setActiveSemester)
            session.show(parsed, quickImport = true)
            val completed = withTimeout(10_000) { session.state.first {
                it is OnlineImportPreviewState.Saved || it is OnlineImportPreviewState.SaveFailed ||
                    it is OnlineImportPreviewState.Error
            } }
            assertTrue("Expected successful quick import, got $completed", completed is OnlineImportPreviewState.Saved)
            return completed as OnlineImportPreviewState.Saved
        }

        assertEquals(1, import("首批合成课程", 1).receipt.savedCount)
        assertEquals(1, import("其他来源学期课程", 2).receipt.savedCount)
        val replacement = import("新批合成课程", 1)
        assertEquals(1, replacement.receipt.removedCount)
        assertEquals(1, replacement.receipt.savedCount)
        assertEquals(1, replacement.summary.duplicateCount)
        assertNull(replacement.activationError)
        assertEquals(target.id, ok(repository.observeActiveSemester().first())!!.id)
        database.close()
        reopen()
        assertEquals(setOf("手动保留课程", "其他来源学期课程", "新批合成课程"),
            ok(repository.observeWeek(target.id, 1).first()).arrangements.map { it.name }.toSet())
        assertEquals(target.id, ok(repository.observeActiveSemester().first())!!.id)
        assertEquals(2, ok(repository.observeWeek(target.id, 3).first()).arrangements.size)
        assertTrue(ok(repository.observeWeek(other.id, 1).first()).arrangements.isEmpty())
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }
}
