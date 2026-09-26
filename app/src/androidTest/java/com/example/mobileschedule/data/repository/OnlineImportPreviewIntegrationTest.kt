package com.example.mobileschedule.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import com.example.mobileschedule.data.importer.toPreviewSummary
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.model.CompletenessEvidence
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.ImportCandidate
import com.example.mobileschedule.data.model.ImportConfirmation
import com.example.mobileschedule.data.model.ImportRequest
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.SemesterConfigDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineImportPreviewIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "online-preview-integration-test.db"
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

    private fun <T> ok(value: RepoResult<T>): T = (value as RepoResult.Ok).value

    @Test fun schoolFieldsMapToPreviewAndOnlyMatchingOldScopeIsCountedWithoutSaving() = runBlocking {
        val target = ok(repository.createSemester(SemesterConfigDraft(
            "合成本地学期", LocalDate.of(2026, 9, 7), 20, 8, emptyList())))
        val scopeTerm = "xnm=2026;xqm=3"
        val prior = ImportRequest(target.id, "fjnu", "fjnu-zhengfang-web", scopeTerm,
            "合成来源学期", 1, 1,
            listOf(ImportCandidate(0, null, "旧合成课程", null, null, 2, 1, 2, setOf(1))),
            emptyList(), CompletenessEvidence(CompletenessStatus.VERIFIED_FULL, scopeTerm,
                listOf(0), 1, 1, "synthetic complete single-page fixture"))
        val oldPreview = ok(repository.prepareImport(prior))
        assertTrue(oldPreview.canCommit)
        ok(repository.commitImport(oldPreview.previewId, ImportConfirmation(oldPreview.previewId,
            oldPreview.scope!!, oldPreview.replaceCount!!, oldPreview.validCount)))

        val raw = """{"xsxx":{"XNM":"2026","XQM":"3","XNMC":"2026-2027","XQMMC":"1"},"sjkList":[],"kbList":[{"kcmc":"同名合成课","xm":"教师甲","cdmc":"教室甲","jc":"3-4节","xqj":"2","zcd":"1-2周,5周","xnm":"2026","xqm":"3"},{"kcmc":"同名合成课","xm":"教师乙","cdmc":"教室乙","jc":"5-6节","xqj":"5","zcd":"3周,7周","xnm":"2026","xqm":"3"},{"kcmc":"同名合成课","xm":"教师甲","cdmc":"教室甲","jc":"3-4节","xqj":"2","zcd":"1-2周,5周","xnm":"2026","xqm":"3"}]}"""
        val parsed = ZhengfangOnlineRead.parse(raw, target.id, ZhengfangOnlineRead.selectTerm("2026", 1)!!)
        val preview = ok(repository.prepareImport(parsed.request))
        val summary = parsed.toPreviewSummary(preview)

        assertEquals("2026-2027 学年第1学期", summary.sourceTermLabel)
        assertEquals(scopeTerm, summary.replacementScope!!.sourceTermId)
        assertEquals(1, summary.replaceCount)
        assertEquals(3, summary.readCount)
        assertEquals(2, summary.validCount)
        assertEquals(1, summary.duplicateCount)
        assertEquals(0, summary.errorCount)
        assertEquals(listOf("同名合成课", "同名合成课"), summary.arrangements.map { it.name })
        assertEquals(listOf(2, 5), summary.arrangements.map { it.dayOfWeek })
        assertEquals(listOf(3, 5), summary.arrangements.map { it.startSection })
        assertEquals(listOf(setOf(1, 2, 5), setOf(3, 7)), summary.arrangements.map { it.weeks })
        assertEquals(CompletenessStatus.UNKNOWN, summary.completeness.status)
        assertFalse(summary.canCommit)
        assertEquals(listOf("旧合成课程"), ok(repository.observeWeek(target.id, 1).first())
            .arrangements.map { it.name })
        ok(repository.discardImport(summary.previewId))
    }
}
