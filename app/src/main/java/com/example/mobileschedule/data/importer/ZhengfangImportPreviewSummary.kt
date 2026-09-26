package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessEvidence
import com.example.mobileschedule.data.model.ImportIssue
import com.example.mobileschedule.data.model.ImportPreview
import com.example.mobileschedule.data.model.PreviewArrangement
import com.example.mobileschedule.data.model.SourceScope

/** Display-ready facts from the parser and the authoritative import preview. Counts are arrangements. */
data class ZhengfangImportPreviewSummary(
    val previewId: String,
    val targetSemesterId: Long,
    val sourceTermId: String?,
    val sourceTermLabel: String?,
    val completeness: CompletenessEvidence,
    val replacementScope: SourceScope?,
    val replaceCount: Int?,
    val readCount: Int?,
    val parsedCount: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
    val arrangements: List<PreviewArrangement>,
    val issues: List<ImportIssue>,
    val canCommit: Boolean,
    /** Sanitized parser errors retain their field names for an actionable preview message. */
    val parseDiagnostics: List<ZhengfangParseDiagnostic>,
) {
    /** Candidate arrangements after validation and exact deduplication, even when saving is blocked. */
    val pendingSaveCount: Int get() = validCount
    val accountedCount: Int get() = validCount + duplicateCount + errorCount
    /** Null means the original arrangement-node count is unavailable, not zero. */
    val countMatchesRead: Boolean? get() = readCount?.let { it == accountedCount }
}

/** Pair a parsed response with the preview returned for that same request; never recompute deduplication. */
fun ParsedZhengfangSchedule.toPreviewSummary(preview: ImportPreview): ZhengfangImportPreviewSummary {
    require(preview.targetSemesterId == request.targetSemesterId) { "Preview semester does not match parsed request" }
    require(preview.sourceObservedCount == request.sourceObservedCount) { "Preview raw count does not match parsed request" }
    require(preview.validCount == preview.normalizedArrangements.size) { "Preview valid count does not match rows" }
    preview.scope?.let { scope ->
        require(scope.schoolId == request.schoolId && scope.sourceId == request.sourceId &&
            scope.sourceTermId == request.sourceTermId) { "Preview scope does not match parsed request" }
    }
    return ZhengfangImportPreviewSummary(
        preview.previewId, preview.targetSemesterId,
        request.sourceTermId, request.sourceTermLabel, request.completeness,
        preview.scope, preview.replaceCount, preview.sourceObservedCount, parsedRowCount,
        preview.validCount, preview.duplicateCount, preview.errorCount,
        preview.normalizedArrangements, preview.issues, preview.canCommit, diagnostics,
    )
}
