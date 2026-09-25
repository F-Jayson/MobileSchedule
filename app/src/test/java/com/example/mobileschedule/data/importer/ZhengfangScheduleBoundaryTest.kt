package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessEvidence
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.ImportIssueCode
import com.example.mobileschedule.data.model.ImportIssueStage
import com.example.mobileschedule.data.model.RuleIssueCode
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ImportValidator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhengfangScheduleBoundaryTest {
    private val config = SemesterConfig(LocalDate.of(2026, 9, 7), 8, 6, emptyList(), 1)
    private val context = ZhengfangParseContext(42, "fjnu-zhengfang-api")
    private val verified = context.copy(
        sourceTermId = "synthetic-term",
        completeness = CompletenessEvidence(
            CompletenessStatus.VERIFIED_FULL, "synthetic-term", listOf(0), null, null,
            "Synthetic single-page fixture only",
        ),
    )

    @Test fun mixedParityAndGapsProduceDiscreteWeeksAndMultiSectionRange() {
        val raw = """{"kbList":[{"kcmc":"示例","xqj":"3","jc":"3-6节","zcd":"1-8周(单),10-14周(双),16周"}]}"""
        val parsed = ZhengfangScheduleParser.parseApiResponse(raw, context)
        assertEquals(1, parsed.parsedRowCount)
        assertEquals(3, parsed.request.candidates.single().startSection)
        assertEquals(6, parsed.request.candidates.single().endSection)
        assertEquals(setOf(1, 3, 5, 7, 10, 12, 14, 16), parsed.request.candidates.single().weeks)
        assertTrue(parsed.diagnostics.isEmpty())
    }

    @Test fun absentOptionalTeacherAndPlaceProduceWarningsButDoNotBlockVerifiedSyntheticPreview() {
        val parsed = ZhengfangScheduleParser.parseNormalizedApiResult(normalizedOne(1), verified)
        val preview = ImportValidator.buildPreview("optional", parsed.request, config, null, 0)
        assertEquals(1, preview.validCount)
        assertEquals(0, preview.errorCount)
        assertTrue(preview.canCommit)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.TEACHER_MISSING && it.stage == ImportIssueStage.WARNING })
        assertTrue(preview.issues.any { it.code == ImportIssueCode.LOCATION_MISSING && it.stage == ImportIssueStage.WARNING })
    }

    @Test fun missingRequiredFieldKeepsOtherRowsAndReportsTheOriginalIndex() {
        val raw = """{"kbList":[
          {"kcmc":"一","xqj":"1","jc":"1-2节","zcd":"1-4周"},
          {"xqj":"2","jc":"3-4节","zcd":"1-4周"},
          {"kcmc":"三","xqj":"3","jc":"5-6节","zcd":"1-4周"}
        ]}"""
        val parsed = ZhengfangScheduleParser.parseApiResponse(raw, verified)
        assertEquals(3, parsed.request.sourceObservedCount)
        assertEquals(2, parsed.parsedRowCount)
        assertEquals(listOf(0, 2), parsed.request.candidates.map { it.sourceIndex })
        assertEquals(ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD, parsed.diagnostics.single().code)
        assertEquals(1, parsed.diagnostics.single().sourceIndex)
        assertEquals("kcmc", parsed.diagnostics.single().field)
        assertEquals(ImportIssueCode.INVALID_ARRANGEMENT, parsed.request.parseIssues.single().code)
        val preview = ImportValidator.buildPreview("required", parsed.request, config, null, 0)
        assertEquals(2, preview.validCount)
        assertEquals(1, preview.errorCount)
        assertFalse(preview.canCommit)
    }

    @Test fun malformedWeekAndSectionTextReportEachBadRowWithoutSilentlyDroppingIt() {
        val raw = """{"kbList":[
          {"kcmc":"一","xqj":"1","jc":"1-2节","zcd":"8-2周"},
          {"kcmc":"二","xqj":"2","jc":"1,3节","zcd":"1-4周"}
        ]}"""
        val parsed = ZhengfangScheduleParser.parseApiResponse(raw, verified)
        assertEquals(2, parsed.request.sourceObservedCount)
        assertEquals(0, parsed.parsedRowCount)
        assertEquals(listOf(ZhengfangParseErrorCode.INVALID_WEEK_RANGE,
            ZhengfangParseErrorCode.INVALID_SECTION_FORMAT), parsed.diagnostics.map { it.code })
        assertEquals(listOf(0, 1), parsed.request.parseIssues.map { it.sourceIndex })
        val preview = ImportValidator.buildPreview("malformed", parsed.request, config, null, 0)
        assertEquals(2, preview.errorCount)
        assertFalse(preview.canCommit)
    }

    @Test fun malformedNormalizedSectionListIsAVisibleRowError() {
        val raw = normalizedOne(1).replace("[3,4]", "[3,5]")
        val parsed = ZhengfangScheduleParser.parseNormalizedApiResult(raw, verified)
        assertEquals(ZhengfangParseErrorCode.INVALID_SECTION_SEQUENCE, parsed.diagnostics.single().code)
        assertEquals(0, parsed.diagnostics.single().sourceIndex)
        assertEquals(1, parsed.request.sourceObservedCount)
        val preview = ImportValidator.buildPreview("section-list", parsed.request, config, null, 0)
        assertEquals(1, preview.errorCount)
        assertFalse(preview.canCommit)
    }

    @Test fun overflowingWeekEndpointCannotFallBackToAValidSingleWeek() {
        val raw = """{"kbList":[{"kcmc":"示例","xqj":"1","jc":"1-2节","zcd":"1-999999999999999999999周"}]}"""
        val parsed = ZhengfangScheduleParser.parseApiResponse(raw, verified)
        assertEquals(0, parsed.parsedRowCount)
        assertEquals(ZhengfangParseErrorCode.INVALID_WEEK_FORMAT, parsed.diagnostics.single().code)
        assertEquals(0, parsed.diagnostics.single().sourceIndex)
        val preview = ImportValidator.buildPreview("week-overflow", parsed.request, config, null, 0)
        assertEquals(1, preview.errorCount)
        assertFalse(preview.canCommit)
    }

    @Test fun numericWeekAndSectionBoundsUseSharedScheduleRules() {
        val raw = """{"kbList":[
          {"kcmc":"超周","xqj":"1","jc":"1-2节","zcd":"9周"},
          {"kcmc":"超节","xqj":"2","jc":"7-8节","zcd":"1周"}
        ]}"""
        val parsed = ZhengfangScheduleParser.parseApiResponse(raw, verified)
        assertTrue(parsed.diagnostics.isEmpty())
        val preview = ImportValidator.buildPreview("bounds", parsed.request, config, null, 0)
        assertEquals(2, preview.errorCount)
        assertTrue(preview.issues.any { it.ruleIssues.any { rule -> rule.code == RuleIssueCode.WEEK_OUT_OF_RANGE } })
        assertTrue(preview.issues.any { it.ruleIssues.any { rule -> rule.code == RuleIssueCode.SECTION_RANGE_INVALID } })
        assertFalse(preview.canCommit)
    }

    @Test fun loginPageAndExpiredApiCodeAreNeverParsedAsEmptySchedule() {
        val html = """<html><title>用户登录</title><form action="/jwglxt/xtgl/login_slogin.html"></form></html>"""
        val expired = """{"code":1006,"msg":"session expired"}"""
        assertSourceError(ZhengfangParseErrorCode.LOGIN_REQUIRED) { ZhengfangScheduleParser.parseApiResponse(html, context) }
        assertSourceError(ZhengfangParseErrorCode.LOGIN_REQUIRED) { ZhengfangScheduleParser.parseNormalizedApiResult(html, context) }
        assertSourceError(ZhengfangParseErrorCode.LOGIN_REQUIRED) { ZhengfangScheduleParser.parseDomBridgeRows(html, context) }
        assertSourceError(ZhengfangParseErrorCode.LOGIN_REQUIRED) { ZhengfangScheduleParser.parseNormalizedApiResult(expired, context) }
        assertSourceError(ZhengfangParseErrorCode.LOGIN_REQUIRED) { ZhengfangScheduleParser.parseApiResponse("\uFEFF" + html, context) }
    }

    @Test fun unrelatedHtmlAndWrongJsonShapeAreNotScheduleResponses() {
        assertSourceError(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE) {
            ZhengfangScheduleParser.parseApiResponse("<html><body>maintenance</body></html>", context)
        }
        assertSourceError(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE) {
            ZhengfangScheduleParser.parseApiResponse("""{"message":"maintenance"}""", context)
        }
        assertSourceError(ZhengfangParseErrorCode.MALFORMED_JSON) {
            ZhengfangScheduleParser.parseApiResponse("{", context)
        }
    }

    @Test fun zeroRowsAndExplicitEmptyApiCodeHaveAnEmptySourceError() {
        assertSourceError(ZhengfangParseErrorCode.EMPTY_SCHEDULE) {
            ZhengfangScheduleParser.parseApiResponse("""{"kbList":[]}""", context)
        }
        assertSourceError(ZhengfangParseErrorCode.EMPTY_SCHEDULE) {
            ZhengfangScheduleParser.parseNormalizedApiResult("""{"code":1000,"data":{"count":0,"courses":[],"extra_courses":[]}}""", context)
        }
        assertSourceError(ZhengfangParseErrorCode.EMPTY_SCHEDULE) {
            ZhengfangScheduleParser.parseNormalizedApiResult("""{"code":1005,"msg":"empty"}""", context)
        }
        assertSourceError(ZhengfangParseErrorCode.EMPTY_SCHEDULE) {
            ZhengfangScheduleParser.parseDomBridgeRows("[]", context)
        }
    }

    @Test fun partialSemesterEvidenceAndObservedCountMismatchCannotBeSaved() {
        val partial = verified.copy(completeness = verified.completeness.copy(status = CompletenessStatus.PARTIAL))
        val parsed = ZhengfangScheduleParser.parseNormalizedApiResult(normalizedOne(1), partial)
        val preview = ImportValidator.buildPreview("partial", parsed.request, config, null, 0)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.PARTIAL_SOURCE })
        assertFalse(preview.canCommit)

        val mismatch = ZhengfangScheduleParser.parseNormalizedApiResult(normalizedOne(2), verified)
        val mismatchPreview = ImportValidator.buildPreview("mismatch", mismatch.request, config, null, 0)
        assertTrue(mismatchPreview.issues.any { it.code == ImportIssueCode.RAW_COUNT_MISMATCH })
        assertFalse(mismatchPreview.canCommit)
    }

    @Test fun unsupportedExtraCoursesCannotBeIgnored() {
        val raw = normalizedOne(1).replace("\"extra_courses\":[]", "\"extra_courses\":[{\"name\":\"lab\"}]")
        assertSourceError(ZhengfangParseErrorCode.UNSUPPORTED_EXTRA_COURSES) {
            ZhengfangScheduleParser.parseNormalizedApiResult(raw, context)
        }
        assertSourceError(ZhengfangParseErrorCode.UNSUPPORTED_EXTRA_COURSES) {
            ZhengfangScheduleParser.parseApiResponse(
                """{"kbList":[{"kcmc":"示例","xqj":"1","jc":"1-2节","zcd":"1周"}],"sjkList":[{}]}""",
                context,
            )
        }
    }

    @Test fun wrongOptionalFieldTypeIsAVisibleRowErrorRatherThanMissingWarning() {
        val raw = """{"kbList":[{"kcmc":"示例","xm":7,"xqj":"1","jc":"1-2节","zcd":"1周"}]}"""
        val parsed = ZhengfangScheduleParser.parseApiResponse(raw, verified)
        assertEquals(ZhengfangParseErrorCode.INVALID_FIELD_TYPE, parsed.diagnostics.single().code)
        assertEquals("xm", parsed.diagnostics.single().field)
        val preview = ImportValidator.buildPreview("type", parsed.request, config, null, 0)
        assertEquals(1, preview.errorCount)
        assertFalse(preview.canCommit)
    }

    private fun normalizedOne(count: Int): String =
        """{"code":1000,"data":{"count":$count,"courses":[{"title":"示例","weekday":2,"list_sessions":[3,4],"list_weeks":[1,3,5]}],"extra_courses":[]}}"""

    private fun assertSourceError(code: ZhengfangParseErrorCode, action: () -> Unit) {
        val error = assertThrows(ZhengfangParseException::class.java) { action() }
        assertEquals(code, error.diagnostic.code)
        assertEquals(null, error.diagnostic.sourceIndex)
    }
}
