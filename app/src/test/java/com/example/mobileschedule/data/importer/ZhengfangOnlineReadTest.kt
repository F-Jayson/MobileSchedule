package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ImportValidator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ZhengfangOnlineReadTest {
    @Test fun sourceSelectionUsesObservedScheduleRequestFields() {
        val first = ZhengfangOnlineRead.selectTerm("2026", 1)!!
        val second = ZhengfangOnlineRead.selectTerm("2026", 2)!!

        assertEquals("xnm=2026&xqm=3", ZhengfangOnlineRead.postBody(first))
        assertEquals("xnm=2026&xqm=12", ZhengfangOnlineRead.postBody(second))
        assertNull(ZhengfangOnlineRead.selectTerm("26", 1))
        assertNull(ZhengfangOnlineRead.selectTerm("2026", 3))
        assertNull(ZhengfangOnlineRead.selectTerm("2026-2027", 1))
    }

    @Test fun endpointCheckRequiresTheExactSchoolHostAndSchedulePath() {
        assertTrue(ZhengfangOnlineRead.isScheduleEndpoint(ZhengfangOnlineRead.SCHEDULE_URL))
        assertFalse(ZhengfangOnlineRead.isScheduleEndpoint(
            "https://jwglxt.fjnu.edu.cn.evil.example/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151"))
        assertFalse(ZhengfangOnlineRead.isScheduleEndpoint(
            "https://jwglxt.fjnu.edu.cn/jwglxt/xtgl/login_slogin.html"))
        assertFalse(ZhengfangOnlineRead.isScheduleEndpoint(
            "http://jwglxt.fjnu.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151"))
    }

    @Test fun responseGoesThroughExistingParserWithoutInventingCompleteness() {
        val selected = ZhengfangOnlineRead.selectTerm("2026", 1)!!
        val raw = """{"kbList":[{"kcmc":"测试课","jc":"1-2","xqj":1,"zcd":"1-4周"}],"sjkList":[]}"""
        val parsed = ZhengfangOnlineRead.parse(raw, targetSemesterId = 7, selected)

        assertEquals(1, parsed.parsedRowCount)
        assertEquals(1, parsed.request.sourceObservedCount)
        assertEquals(setOf(1, 2, 3, 4), parsed.request.candidates.single().weeks)
        assertEquals("fjnu", parsed.request.schoolId)
        assertNull(parsed.request.sourceTermId)
        assertEquals(CompletenessStatus.UNKNOWN, parsed.request.completeness.status)
    }

    @Test fun schoolTermEchoBecomesVerifiedSourceIdentityButNotFullCoverage() {
        val selected = ZhengfangOnlineRead.selectTerm("2026", 1)!!
        val raw = """{"xsxx":{"XNM":"2026","XQM":"3","XNMC":"2026-2027","XQMMC":"1"},"sjkList":[],"kbList":[{"kcmc":"合成课程","jc":"1-2节","xqj":"2","zcd":"1-4周","xnm":"2026","xqm":"3","pageable":true,"queryModel":{"totalPage":0,"totalCount":0}}]}"""

        val parsed = ZhengfangOnlineRead.parse(raw, targetSemesterId = 7, selected)

        assertEquals("xnm=2026;xqm=3", parsed.request.sourceTermId)
        assertEquals("2026-2027 学年第1学期", parsed.request.sourceTermLabel)
        assertEquals(parsed.request.sourceTermId, parsed.request.completeness.selectedSourceTermId)
        assertEquals(CompletenessStatus.UNKNOWN, parsed.request.completeness.status)
        assertEquals(1, parsed.request.sourceObservedCount)
    }

    @Test fun schoolTermEchoOrCourseRowMismatchRejectsTheResponse() {
        val selected = ZhengfangOnlineRead.selectTerm("2026", 1)!!
        val mismatch = """{"xsxx":{"XNM":"2026","XQM":"12"},"sjkList":[],"kbList":[{"kcmc":"合成课程","jc":"1-2节","xqj":"2","zcd":"1周"}]}"""
        assertEquals(ZhengfangParseErrorCode.SOURCE_TERM_MISMATCH,
            assertThrows(ZhengfangParseException::class.java) {
                ZhengfangOnlineRead.parse(mismatch, targetSemesterId = 7, selected)
            }.diagnostic.code)

        val rowMismatch = """{"xsxx":{"XNM":"2026","XQM":"3"},"sjkList":[],"kbList":[{"kcmc":"合成课程","jc":"1-2节","xqj":"2","zcd":"1周","xnm":"2025","xqm":"3"}]}"""
        assertEquals(ZhengfangParseErrorCode.SOURCE_TERM_MISMATCH,
            assertThrows(ZhengfangParseException::class.java) {
                ZhengfangOnlineRead.parse(rowMismatch, targetSemesterId = 7, selected)
            }.diagnostic.code)
    }

    @Test fun officialScheduleComparisonOnlyConfirmsTheSameCompleteReadBatch() {
        val selected = ZhengfangOnlineRead.selectTerm("2026", 1)!!
        val raw = """{"xsxx":{"XNM":"2026","XQM":"3"},"sjkList":[],"kbList":[{"kcmc":"合成课程","jc":"1-2节","xqj":"2","zcd":"1-4周","xnm":"2026","xqm":"3"}]}"""
        val parsed = ZhengfangOnlineRead.parse(raw, targetSemesterId = 7, selected)

        assertNull(ZhengfangOnlineRead.withUserConfirmedFullCoverage(parsed, matchedCount = 0))
        assertNull(ZhengfangOnlineRead.withUserConfirmedFullCoverage(parsed, matchedCount = 2))
        assertEquals(CompletenessStatus.UNKNOWN, parsed.request.completeness.status)

        val confirmed = ZhengfangOnlineRead.withUserConfirmedFullCoverage(parsed, matchedCount = 1)!!
        assertEquals(CompletenessStatus.VERIFIED_FULL, confirmed.request.completeness.status)
        assertEquals("xnm=2026;xqm=3", confirmed.request.completeness.selectedSourceTermId)
        assertEquals(listOf(0), confirmed.request.completeness.pageIndicesRead)
        assertNull(confirmed.request.completeness.expectedPageCount)
        assertNull(confirmed.request.completeness.reportedSourceTotalCount)
        assertTrue(confirmed.request.completeness.basis.contains("user"))
        assertEquals(CompletenessStatus.UNKNOWN, parsed.request.completeness.status)
        assertNull(ZhengfangOnlineRead.withUserConfirmedFullCoverage(
            parsed.copy(request = parsed.request.copy(sourceTermId = null)), matchedCount = 1))
        val config = SemesterConfig(LocalDate.of(2026, 9, 7), 20, 8, emptyList(), 1)
        assertFalse(ImportValidator.buildPreview("unknown", parsed.request, config, null, 0).canCommit)
        assertTrue(ImportValidator.buildPreview("confirmed", confirmed.request, config, null, 0).canCommit)
    }

    @Test fun webViewResultIsDecodedOnceAndInvalidResultIsRejected() {
        assertEquals("{\"kbList\":[]}",
            ZhengfangOnlineRead.decodeWebViewText("\"{\\\"kbList\\\":[]}\""))
        assertNull(ZhengfangOnlineRead.decodeWebViewText("null"))
        assertNull(ZhengfangOnlineRead.decodeWebViewText("{}"))
    }
}
