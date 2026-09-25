package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.ImportCandidate
import com.example.mobileschedule.data.model.ImportIssueCode
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ImportValidator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhengfangScheduleParserTest {
    private val context = ZhengfangParseContext(targetSemesterId = 42, sourceId = "fjnu-zhengfang-api")
    private val config = SemesterConfig(LocalDate.of(2026, 9, 7), 8, 6, emptyList(), 1)

    // De-identified from the user's real zfn_api result (2026-09-25). Identity/text values are aliases;
    // weekday, section lists, week lists, row order and source count retain the supplied structure.
    private val realDerivedNormalizedSample = """{"code":1000,"data":{"count":14,"courses":[
      {"course_id":"id_01","title":"title_01","teacher":"teacher_01","place":"place_01","weekday":1,"list_sessions":[3,4],"list_weeks":[1,2,3,4,5,7,8,9,12,13,14,15,16,17,18,19]},
      {"course_id":"id_02","title":"title_02","teacher":"teacher_02","place":"place_02","weekday":1,"list_sessions":[5,6],"list_weeks":[1,2,3,4,5,7,8,9]},
      {"course_id":"id_02","title":"title_02","teacher":"teacher_02","place":"place_03","weekday":1,"list_sessions":[5,6],"list_weeks":[12,13,14,15,16,17,18,19]},
      {"course_id":"id_01","title":"title_01","teacher":"teacher_01","place":"place_04","weekday":1,"list_sessions":[7,8],"list_weeks":[3,4,5,7,8,9,12,13,14,15,16,17]},
      {"course_id":"id_03","title":"title_03","teacher":"teacher_03","place":"place_05","weekday":2,"list_sessions":[1,2],"list_weeks":[1,2,3,4,5,7,8,9,12,13,14,15,16,17,18,19]},
      {"course_id":"id_03","title":"title_03","teacher":"teacher_03","place":"place_06","weekday":2,"list_sessions":[3,4],"list_weeks":[3,4,5,7,8,9,12,13,14,15,16,17]},
      {"course_id":"id_02","title":"title_02","teacher":"teacher_02","place":"place_07","weekday":2,"list_sessions":[5,6],"list_weeks":[2,3,4,5,7,8,9,12,13,14,15,16]},
      {"course_id":"id_04","title":"title_04","teacher":"teacher_04","place":"place_08","weekday":3,"list_sessions":[5,6,7,8],"list_weeks":[8]},
      {"course_id":"id_05","title":"title_05","teacher":"teacher_05","place":"place_09","weekday":4,"list_sessions":[1,2],"list_weeks":[1,2,3,4,6,7,8,11,12,13,14,15,16,17,18,19]},
      {"course_id":"id_05","title":"title_05","teacher":"teacher_05","place":"place_10","weekday":4,"list_sessions":[5,6],"list_weeks":[3,4,6,7,8,9,12,13,14,15,16,17]},
      {"course_id":"id_06","title":"title_06","teacher":"teacher_06","place":"place_11","weekday":5,"list_sessions":[1,2],"list_weeks":[1,2,3,4,6,7,8,9,12,13,14,15,16,17,18,19]},
      {"course_id":"id_06","title":"title_06","teacher":"teacher_06","place":"place_12","weekday":5,"list_sessions":[3,4],"list_weeks":[1,2,3,4,6,7,8,9,12,13,14,15,16,17,18,19]},
      {"course_id":"id_07","title":"title_07","teacher":"teacher_07","place":"place_04","weekday":5,"list_sessions":[5,6],"list_weeks":[3,4,6,7,8,9,12,13,14,15,16,17]},
      {"course_id":"id_07","title":"title_07","teacher":"teacher_07","place":"place_13","weekday":5,"list_sessions":[7,8],"list_weeks":[1,2,3,4,6,7,8,9,12,13,14,15,16,17,18,19]}
    ],"extra_courses":[]}}"""

    // Hand-written synthetic rows follow the field mapping in the local zfn_api get_schedule code.
    // They are not an FJNU response or a claim that the selected semester is complete.
    private val apiSample = """{
        "kbList": [
          {"kcmc":"示例课程","xm":"示例教师","cdmc":"示例楼 203","xqj":"2","jc":"1-2节","zcd":"1-8周(单)"},
          {"kcmc":"示例课程","xm":"示例教师","cdmc":"示例楼 203","xqj":"2","jc":"3-4节","zcd":"2-8周(双)"},
          {"kcmc":"无教师地点示例","xm":"","cdmc":"","xqj":"5","jc":"5-6节","zcd":"3-5周"}
        ]
    }"""

    // Independently copied as a small literal from stage 1.1 synthetic output v1 (SHA in handoff).
    private val domSample = """{
      "fixtureType":"synthetic-reference-dom",
      "arrangements":[
        {"name":"示例课程","day":2,"weeks":[1,3,5,7],"teacher":"示例教师","position":"示例楼 203","startSection":1,"endSection":2},
        {"name":"示例课程","day":2,"weeks":[2,4,6,8],"teacher":"示例教师","position":"示例楼 203","startSection":3,"endSection":4},
        {"name":"无教师地点示例","day":5,"weeks":[3,4,5],"teacher":"","position":"","startSection":5,"endSection":6}
      ]
    }"""

    private val expected = listOf(
        ImportCandidate(0, null, "示例课程", "示例教师", "示例楼 203", 2, 1, 2, setOf(1, 3, 5, 7)),
        ImportCandidate(1, null, "示例课程", "示例教师", "示例楼 203", 2, 3, 4, setOf(2, 4, 6, 8)),
        ImportCandidate(2, null, "无教师地点示例", null, null, 5, 5, 6, setOf(3, 4, 5)),
    )

    @Test fun apiRowsMapEveryScheduleFieldWithoutMergingSameName() {
        val result = ZhengfangScheduleParser.parseApiResponse(apiSample, context)
        assertEquals(3, result.parsedRowCount)
        assertEquals(3, result.request.sourceObservedCount)
        assertEquals("fjnu", result.request.schoolId)
        assertEquals("fjnu-zhengfang-api", result.request.sourceId)
        assertEquals(42, result.request.targetSemesterId)
        assertEquals(expected, result.request.candidates)
        assertTrue(result.request.parseIssues.isEmpty())
        assertNull(result.request.sourceTermId)
        assertEquals(CompletenessStatus.UNKNOWN, result.request.completeness.status)

        val preview = ImportValidator.buildPreview("preview", result.request, config, null, 0)
        assertEquals(3, preview.validCount)
        assertEquals(0, preview.duplicateCount)
        assertFalse(preview.canCommit)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.SOURCE_TERM_UNVERIFIED })
        assertTrue(preview.issues.any { it.code == ImportIssueCode.COMPLETENESS_UNKNOWN })
    }

    @Test fun realDerivedNormalizedRowsKeepAllFourteenArrangementsAndDiscreteWeeks() {
        val result = ZhengfangScheduleParser.parseNormalizedApiResult(realDerivedNormalizedSample, context)
        assertEquals(14, result.parsedRowCount)
        assertEquals(14, result.request.sourceObservedCount)
        assertNull(result.request.reportedSourceTotalCount)
        assertEquals(14, result.request.candidates.size)
        assertEquals("title_02", result.request.candidates[1].name)
        assertEquals("title_02", result.request.candidates[2].name)
        assertEquals("title_02", result.request.candidates[6].name)
        assertEquals(setOf(1, 2, 3, 4, 5, 7, 8, 9), result.request.candidates[1].weeks)
        assertEquals((12..19).toSet(), result.request.candidates[2].weeks)
        assertEquals(3, result.request.candidates[7].dayOfWeek)
        assertEquals(5, result.request.candidates[7].startSection)
        assertEquals(8, result.request.candidates[7].endSection)
        assertEquals(setOf(8), result.request.candidates[7].weeks)
        assertNull(result.request.candidates[1].sourceEntryId)
        assertNull(result.request.sourceTermId)
        assertEquals(CompletenessStatus.UNKNOWN, result.request.completeness.status)

        val preview = ImportValidator.buildPreview("real-derived", result.request,
            config.copy(totalWeeks = 19, totalSections = 8), null, 0)
        assertEquals(14, preview.validCount)
        assertEquals(0, preview.duplicateCount)
        assertEquals(0, preview.errorCount)
        assertFalse(preview.canCommit)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.COMPLETENESS_UNKNOWN })
    }

    @Test fun normalizedCountMismatchCannotBeTreatedAsACompleteSnapshot() {
        val sample = realDerivedNormalizedSample.replace("\"count\":14", "\"count\":15")
        val result = ZhengfangScheduleParser.parseNormalizedApiResult(sample, context)
        val preview = ImportValidator.buildPreview("count-mismatch", result.request,
            config.copy(totalWeeks = 19, totalSections = 8), null, 0)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.RAW_COUNT_MISMATCH })
        assertFalse(preview.canCommit)
    }

    @Test fun stageOneSyntheticDomRowsPreserveDiscreteWeeksAndUnknownRawCount() {
        val result = ZhengfangScheduleParser.parseDomBridgeRows(domSample, context.copy(sourceId = "fjnu-zhengfang-dom"))
        assertEquals(3, result.parsedRowCount)
        assertNull(result.request.sourceObservedCount)
        assertEquals(expected, result.request.candidates)
        val preview = ImportValidator.buildPreview("preview", result.request, config, null, null)
        assertEquals(3, preview.validCount)
        assertFalse(preview.canCommit)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.RAW_COUNT_UNKNOWN })
    }

    @Test fun directBridgeArrayUsesTheSameMappingWithoutInventingSemesterEvidence() {
        val bareArray = domSample.substringAfter("\"arrangements\":").trim().removeSuffix("}").trim()
        val result = ZhengfangScheduleParser.parseDomBridgeRows(bareArray, context.copy(sourceId = "fjnu-zhengfang-dom"))
        assertEquals(expected, result.request.candidates)
        assertNull(result.request.sourceTermId)
        assertEquals(CompletenessStatus.UNKNOWN, result.request.completeness.status)
    }
}
