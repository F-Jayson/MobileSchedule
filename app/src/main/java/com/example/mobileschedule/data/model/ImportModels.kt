package com.example.mobileschedule.data.model

data class ImportRequest(
    val targetSemesterId: Long,
    val schoolId: String,
    val sourceId: String,
    val sourceTermId: String?,
    val sourceTermLabel: String?,
    val sourceObservedCount: Int?,
    val reportedSourceTotalCount: Int?,
    val candidates: List<ImportCandidate>,
    val parseIssues: List<ImportIssue>,
    val completeness: CompletenessEvidence,
)

data class ImportCandidate(
    val sourceIndex: Int,
    val sourceEntryId: String?,
    val name: String?,
    val teacher: String?,
    val location: String?,
    val dayOfWeek: Int?,
    val startSection: Int?,
    val endSection: Int?,
    val weeks: Set<Int>?,
)

enum class CompletenessStatus { UNKNOWN, PARTIAL, VERIFIED_FULL }

data class CompletenessEvidence(
    val status: CompletenessStatus,
    val selectedSourceTermId: String?,
    val pageIndicesRead: List<Int>,
    val expectedPageCount: Int?,
    val reportedSourceTotalCount: Int?,
    /** Non-sensitive description of how the source proved complete. */
    val basis: String,
)

enum class ImportIssueStage { PARSE, VALIDATION, COMPLETENESS, WARNING }

enum class ImportIssueCode {
    SOURCE_SCHOOL_INVALID, SOURCE_ID_INVALID, SOURCE_TERM_UNVERIFIED,
    COMPLETENESS_UNKNOWN, PARTIAL_SOURCE, RAW_COUNT_UNKNOWN, RAW_COUNT_MISMATCH,
    EMPTY_SNAPSHOT, INVALID_ARRANGEMENT, DUPLICATE_SOURCE_ID,
    CONFIG_REQUIRED, SCOPE_CONFLICT, TARGET_SEMESTER_NOT_FOUND,
    TEACHER_MISSING, LOCATION_MISSING,
}

data class ImportIssue(
    val stage: ImportIssueStage,
    val code: ImportIssueCode,
    val sourceIndex: Int? = null,
    val ruleIssues: List<RuleIssue> = emptyList(),
)

data class PreviewArrangement(
    val sourceIndex: Int,
    val sourceEntryId: String?,
    val name: String,
    val teacher: String?,
    val location: String?,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: Set<Int>,
)

data class ImportPreview(
    val previewId: String,
    val targetSemesterId: Long,
    val scope: SourceScope?,
    val sourceObservedCount: Int?,
    val validCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
    val replaceCount: Int?,
    val normalizedArrangements: List<PreviewArrangement>,
    val issues: List<ImportIssue>,
    val canCommit: Boolean,
)

data class ImportConfirmation(
    val previewId: String,
    val scope: SourceScope,
    val replaceCount: Int,
    val validCount: Int,
)

data class ImportReceipt(
    val batchId: String,
    val scope: SourceScope,
    val semesterId: Long,
    val removedCount: Int,
    val savedCount: Int,
)
