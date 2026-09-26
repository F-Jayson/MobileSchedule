package com.example.mobileschedule.ui.importentry

import com.example.mobileschedule.data.importer.ParsedZhengfangSchedule
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.DataError
import com.example.mobileschedule.data.model.DataErrorCode
import com.example.mobileschedule.data.model.ImportIssueCode
import com.example.mobileschedule.data.model.ImportPreview
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ImportValidator
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineImportPreviewSessionTest {
    private val config = SemesterConfig(LocalDate.of(2026, 9, 7), 20, 8, emptyList(), 1)

    private fun parsed(term: Int = 1, name: String = "合成课程"): ParsedZhengfangSchedule {
        val selected = ZhengfangOnlineRead.selectTerm("2026", term)!!
        val raw = """{"xsxx":{"XNM":"2026","XQM":"${selected.xqm}","XNMC":"2026-2027","XQMMC":"$term"},"sjkList":[],"kbList":[{"kcmc":"$name","xm":"合成教师","cdmc":"合成教室","jc":"1-2节","xqj":"2","zcd":"1-3周,5周","xnm":"2026","xqm":"${selected.xqm}"}]}"""
        return ZhengfangOnlineRead.parse(raw, 7, selected)
    }

    private fun preview(parsed: ParsedZhengfangSchedule, id: String, replaceCount: Int = 2): ImportPreview =
        ImportValidator.buildPreview(id, parsed.request, config, null, replaceCount)

    @Test fun schoolResponseBecomesBlockedPreviewWithSourceRowsAndReplacementScope() = runTest {
        val source = parsed()
        val session = OnlineImportPreviewSession(this,
            prepare = { RepoResult.Ok(preview(source, "p1")) },
            discard = { RepoResult.Ok(Unit) })

        session.show(source)
        assertEquals(OnlineImportPreviewState.Loading, session.state.value)
        runCurrent()

        val ready = session.state.value as OnlineImportPreviewState.Ready
        assertEquals("xnm=2026;xqm=3", ready.summary.sourceTermId)
        assertEquals("2026-2027 学年第1学期", ready.summary.sourceTermLabel)
        assertEquals("fjnu", ready.summary.replacementScope!!.schoolId)
        assertEquals(2, ready.summary.replaceCount)
        assertEquals(1, ready.summary.readCount)
        assertEquals(1, ready.summary.parsedCount)
        assertEquals(1, ready.summary.validCount)
        assertEquals(setOf(1, 2, 3, 5), ready.summary.arrangements.single().weeks)
        assertEquals("合成课程", ready.summary.arrangements.single().name)
        assertEquals(CompletenessStatus.UNKNOWN, ready.summary.completeness.status)
        assertTrue(ready.summary.issues.any { it.code == ImportIssueCode.COMPLETENESS_UNKNOWN })
        assertFalse(ready.summary.canCommit)
    }

    @Test fun changingSourceTermDiscardsOldPreviewBeforeShowingTheNewOne() = runTest {
        val first = parsed()
        val second = parsed(term = 2, name = "第二学期合成课程")
        val discarded = mutableListOf<String>()
        val session = OnlineImportPreviewSession(this,
            prepare = { request -> RepoResult.Ok(preview(if (request.sourceTermId == first.request.sourceTermId) first else second,
                if (request.sourceTermId == first.request.sourceTermId) "p1" else "p2")) },
            discard = { id -> discarded += id; RepoResult.Ok(Unit) })

        session.show(first)
        runCurrent()
        session.invalidate()
        assertEquals(OnlineImportPreviewState.Idle, session.state.value)
        runCurrent()
        assertEquals(listOf("p1"), discarded)

        session.show(second)
        runCurrent()
        val ready = session.state.value as OnlineImportPreviewState.Ready
        assertEquals("p2", ready.summary.previewId)
        assertEquals("xnm=2026;xqm=12", ready.summary.sourceTermId)
        assertEquals("第二学期合成课程", ready.summary.arrangements.single().name)
    }

    @Test fun lateOldPreparationIsDiscardedAndCannotReplaceTheNewPreview() = runTest {
        val first = parsed()
        val second = parsed(term = 2)
        val waiting = ArrayDeque<CompletableDeferred<RepoResult<ImportPreview>>>()
        val discarded = mutableListOf<String>()
        val session = OnlineImportPreviewSession(this,
            prepare = { waiting.removeFirst().await() },
            discard = { id -> discarded += id; RepoResult.Ok(Unit) })
        val oldResult = CompletableDeferred<RepoResult<ImportPreview>>()
        val newResult = CompletableDeferred<RepoResult<ImportPreview>>()
        waiting.add(oldResult)
        waiting.add(newResult)

        session.show(first)
        runCurrent()
        session.show(second)
        runCurrent()
        newResult.complete(RepoResult.Ok(preview(second, "new")))
        runCurrent()
        oldResult.complete(RepoResult.Ok(preview(first, "old")))
        runCurrent()

        assertEquals("new", (session.state.value as OnlineImportPreviewState.Ready).summary.previewId)
        assertEquals(listOf("old"), discarded)
    }

    @Test fun repositoryReadFailureIsNotShownAsAnEmptyPreview() = runTest {
        val session = OnlineImportPreviewSession(this,
            prepare = { RepoResult.Err(DataError(DataErrorCode.STORAGE_READ_FAILED)) },
            discard = { RepoResult.Ok(Unit) })

        session.show(parsed())
        runCurrent()

        val error = session.state.value as OnlineImportPreviewState.Error
        assertTrue(error.message.contains("本地"))
    }
}
