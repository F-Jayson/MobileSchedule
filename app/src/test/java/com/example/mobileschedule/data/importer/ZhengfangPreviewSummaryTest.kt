package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessEvidence
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.ImportIssueCode
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.model.SourceScope
import com.example.mobileschedule.data.rules.ImportValidator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhengfangPreviewSummaryTest {
    private val config = SemesterConfig(LocalDate.of(2026, 9, 7), 8, 6, emptyList(), 1)
    private val scope = SourceScope("fjnu", "fjnu-zhengfang-api", "source-term-example")
    private val verifiedContext = ZhengfangParseContext(
        targetSemesterId = 42,
        sourceId = scope.sourceId,
        sourceTermId = scope.sourceTermId,
        sourceTermLabel = "合成来源学期",
        completeness = CompletenessEvidence(
            CompletenessStatus.VERIFIED_FULL, scope.sourceTermId, listOf(0), 1, null,
            "Synthetic single-page fixture; not verified school evidence",
        ),
    )

    // Synthetic normalized adapter rows. Same title with changed time, place or weeks is distinct.
    private val rows = listOf(
        """{"title":"课程甲","teacher":"教师甲","place":"教室甲","weekday":2,"list_sessions":[1,2],"list_weeks":[1,3]}""",
        """{"title":"课程甲","teacher":"教师甲","place":"教室甲","weekday":2,"list_sessions":[1,2],"list_weeks":[3,1]}""",
        """{"title":"课程甲","teacher":"教师甲","place":"教室甲","weekday":2,"list_sessions":[3,4],"list_weeks":[1,3]}""",
        """{"title":"课程甲","teacher":"教师甲","place":"教室乙","weekday":2,"list_sessions":[1,2],"list_weeks":[1,3]}""",
        """{"title":"课程甲","teacher":"教师甲","place":"教室甲","weekday":2,"list_sessions":[1,2],"list_weeks":[1,5]}""",
    )

    private fun parse(rows: List<String>, count: Int = rows.size, context: ZhengfangParseContext = verifiedContext) =
        ZhengfangScheduleParser.parseNormalizedApiResult(
            """{"code":1000,"data":{"count":$count,"courses":[${rows.joinToString()}],"extra_courses":[]}}""",
            context,
        )

    @Test fun previewCountsExactDuplicateOnceAndKeepsSameNameDifferentArrangements() {
        val parsed = parse(rows)
        val preview = ImportValidator.buildPreview("p1", parsed.request, config, null, 3)
        val summary = parsed.toPreviewSummary(preview)

        assertEquals(5, summary.readCount)
        assertEquals(5, summary.parsedCount)
        assertEquals(4, summary.validCount)
        assertEquals(1, summary.duplicateCount)
        assertEquals(0, summary.errorCount)
        assertEquals(4, summary.pendingSaveCount)
        assertEquals(5, summary.accountedCount)
        assertEquals(true, summary.countMatchesRead)
        assertEquals(listOf(0, 2, 3, 4), summary.arrangements.map { it.sourceIndex })
        assertEquals(setOf(1, 3), summary.arrangements[0].weeks)
        assertEquals(setOf(1, 5), summary.arrangements[3].weeks)
        assertEquals(3, summary.replaceCount)
        assertEquals(scope, summary.replacementScope)
        assertEquals("p1", summary.previewId)
        assertEquals(42, summary.targetSemesterId)
        assertEquals("source-term-example", summary.sourceTermId)
        assertEquals("合成来源学期", summary.sourceTermLabel)
        assertEquals(CompletenessStatus.VERIFIED_FULL, summary.completeness.status)
        assertTrue(summary.canCommit)
    }

    @Test fun malformedSourceRowCountsAsOneErrorAndBlocksTheWholeBatch() {
        val parsed = parse(rows + """{"place":"教室甲","weekday":2,"list_sessions":[1,2],"list_weeks":[1]}""")
        val preview = ImportValidator.buildPreview("p2", parsed.request, config, null, 3)
        val summary = parsed.toPreviewSummary(preview)

        assertEquals(6, summary.readCount)
        assertEquals(5, summary.parsedCount)
        assertEquals(4, summary.validCount)
        assertEquals(1, summary.duplicateCount)
        assertEquals(1, summary.errorCount)
        assertEquals(4, summary.pendingSaveCount)
        assertEquals(6, summary.accountedCount)
        assertEquals(true, summary.countMatchesRead)
        assertEquals(listOf(0, 2, 3, 4), summary.arrangements.map { it.sourceIndex })
        assertTrue(summary.issues.any { it.code == ImportIssueCode.INVALID_ARRANGEMENT && it.sourceIndex == 5 })
        assertFalse(summary.canCommit)
    }

    @Test fun unknownRawCountAndUnverifiedSourceRemainUnknownInPreview() {
        val parsed = ZhengfangScheduleParser.parseDomBridgeRows(
            """[{"name":"课程甲","day":2,"startSection":1,"endSection":2,"weeks":[1,3]}]""",
            ZhengfangParseContext(42, "fjnu-zhengfang-dom"),
        )
        val preview = ImportValidator.buildPreview("p3", parsed.request, config, null, null)
        val summary = parsed.toPreviewSummary(preview)

        assertNull(summary.readCount)
        assertNull(summary.countMatchesRead)
        assertNull(summary.replacementScope)
        assertNull(summary.replaceCount)
        assertNull(summary.sourceTermId)
        assertEquals(CompletenessStatus.UNKNOWN, summary.completeness.status)
        assertEquals(1, summary.pendingSaveCount)
        assertFalse(summary.canCommit)
        assertTrue(summary.issues.any { it.code == ImportIssueCode.RAW_COUNT_UNKNOWN })
    }

    @Test fun observedCountMismatchIsVisibleAndCannotAuthorizeReplacement() {
        val parsed = parse(rows, count = 6)
        val preview = ImportValidator.buildPreview("p4", parsed.request, config, null, 3)
        val summary = parsed.toPreviewSummary(preview)

        assertEquals(6, summary.readCount)
        assertEquals(5, summary.accountedCount)
        assertEquals(false, summary.countMatchesRead)
        assertTrue(summary.issues.any { it.code == ImportIssueCode.RAW_COUNT_MISMATCH })
        assertFalse(summary.canCommit)
    }
}
