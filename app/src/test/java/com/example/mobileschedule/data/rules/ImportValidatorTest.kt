package com.example.mobileschedule.data.rules

import com.example.mobileschedule.data.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ImportValidatorTest {
    private val config = SemesterConfig(LocalDate.of(2026, 9, 7), 12, 4, emptyList(), 1)
    private fun row(index: Int, id: String? = null, section: Int = 1) = ImportCandidate(
        index, id, " 同名课程 ", " ", null, 2, section, section, setOf(1, 3, 9),
    )
    private fun request(rows: List<ImportCandidate>, count: Int = rows.size) = ImportRequest(
        1, "fjnu", "fjnu-zhengfang-dom", "term-1", "合成学期", count, count, rows, emptyList(),
        CompletenessEvidence(CompletenessStatus.VERIFIED_FULL, "term-1", listOf(0, 1), 2, count, "synthetic complete-page fixture"),
    )

    @Test fun completeSnapshotDeduplicatesExactRowsButKeepsDifferentArrangements() {
        val preview = ImportValidator.buildPreview("token", request(listOf(row(0), row(1, section = 2), row(2))), config, null, 0)
        assertTrue(preview.canCommit)
        assertEquals(2, preview.validCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(0, preview.errorCount)
        assertEquals(listOf(0, 1), preview.normalizedArrangements.map { it.sourceIndex })
        assertEquals(setOf(1, 3, 9), preview.normalizedArrangements.first().weeks)
        assertNull(preview.normalizedArrangements.first().teacher)
        assertEquals(1, preview.normalizedArrangements.map { it.name }.distinct().size)
    }

    @Test fun sameSourceIdWithDifferentFieldsBlocksSnapshotWithoutDiscardingEitherRow() {
        val preview = ImportValidator.buildPreview("token", request(listOf(row(0, "x"), row(1, "x", 2))), config, null, 0)
        assertFalse(preview.canCommit)
        assertEquals(1, preview.validCount)
        assertEquals(1, preview.errorCount)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.DUPLICATE_SOURCE_ID && it.sourceIndex == 1 })
    }

    @Test fun missingIdentityCountOrPageCoverageCannotAuthorizeReplacement() {
        val source = request(listOf(row(0)))
        val unknownTerm = ImportValidator.buildPreview("a", source.copy(sourceTermId = null), config, null, 0)
        assertNull(unknownTerm.scope)
        assertFalse(unknownTerm.canCommit)
        assertTrue(unknownTerm.issues.any { it.code == ImportIssueCode.SOURCE_TERM_UNVERIFIED })

        val incomplete = ImportValidator.buildPreview("b", source.copy(sourceObservedCount = null), config, null, 0)
        assertFalse(incomplete.canCommit)
        assertTrue(incomplete.issues.any { it.code == ImportIssueCode.RAW_COUNT_UNKNOWN })

        val missingPage = ImportValidator.buildPreview("c", source.copy(completeness = source.completeness.copy(pageIndicesRead = listOf(0))), config, null, 0)
        assertFalse(missingPage.canCommit)
        assertTrue(missingPage.issues.any { it.code == ImportIssueCode.PARTIAL_SOURCE })
    }

    @Test fun explicitlyUnpaginatedFullSourceMayOmitExpectedPageCount() {
        val source = request(listOf(row(0)))
        val preview = ImportValidator.buildPreview("token", source.copy(completeness = source.completeness.copy(
            expectedPageCount = null, pageIndicesRead = listOf(0), basis = "synthetic unpaginated complete page",
        )), config, null, 0)
        assertTrue(preview.issues.toString(), preview.canCommit)
    }

    @Test fun invalidAndPartialNodesAreCountedOnceEachAndBlockCommit() {
        val source = request(listOf(row(0), row(1, section = 8)), 3).copy(
            parseIssues = listOf(ImportIssue(ImportIssueStage.PARSE, ImportIssueCode.INVALID_ARRANGEMENT, 2)),
        )
        val preview = ImportValidator.buildPreview("token", source, config, null, 0)
        assertFalse(preview.canCommit)
        assertEquals(1, preview.validCount)
        assertEquals(2, preview.errorCount)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.INVALID_ARRANGEMENT && it.sourceIndex == 1 })
    }

    @Test fun anyUnparsedNodeBlocksCommitEvenIfReportedIssueIsOnlyWarning() {
        val source = request(listOf(row(0)), 2).copy(parseIssues = listOf(
            ImportIssue(ImportIssueStage.WARNING, ImportIssueCode.LOCATION_MISSING, 1),
        ))
        val preview = ImportValidator.buildPreview("token", source, config, null, 0)
        assertEquals(1, preview.validCount)
        assertEquals(1, preview.errorCount)
        assertTrue(preview.issues.any { it.code == ImportIssueCode.INVALID_ARRANGEMENT && it.sourceIndex == 1 })
        assertFalse(preview.canCommit)
    }

    @Test fun zeroRowsWrongSchoolOrConflictingBindingBlockCommit() {
        val empty = ImportValidator.buildPreview("a", request(emptyList()), config, null, 0)
        assertFalse(empty.canCommit)
        assertTrue(empty.issues.any { it.code == ImportIssueCode.EMPTY_SNAPSHOT })

        val wrongSchool = ImportValidator.buildPreview("b", request(listOf(row(0))).copy(schoolId = "other"), config, null, 0)
        assertFalse(wrongSchool.canCommit)
        assertTrue(wrongSchool.issues.any { it.code == ImportIssueCode.SOURCE_SCHOOL_INVALID })

        val boundElsewhere = ImportValidator.buildPreview("c", request(listOf(row(0))), config, 999, 1)
        assertFalse(boundElsewhere.canCommit)
        assertTrue(boundElsewhere.issues.any { it.code == ImportIssueCode.SCOPE_CONFLICT })
    }
}
