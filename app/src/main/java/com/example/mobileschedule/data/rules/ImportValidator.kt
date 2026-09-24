package com.example.mobileschedule.data.rules

import com.example.mobileschedule.data.model.*

/** Validates normalized input only. A VERIFIED_FULL flag is never invented here; B must supply evidence. */
object ImportValidator {
    fun buildPreview(
        previewId: String,
        request: ImportRequest,
        config: SemesterConfig?,
        boundSemesterId: Long?,
        replaceCount: Int?,
    ): ImportPreview {
        val issues = request.parseIssues.toMutableList()
        val sourceTerm = request.sourceTermId
        if (request.schoolId != "fjnu") issues += issue(ImportIssueCode.SOURCE_SCHOOL_INVALID)
        if (request.sourceId.isBlank() || request.sourceId != request.sourceId.trim()) {
            issues += issue(ImportIssueCode.SOURCE_ID_INVALID)
        }
        if (sourceTerm.isNullOrBlank() || sourceTerm != sourceTerm.trim() ||
            sourceTerm != request.completeness.selectedSourceTermId) {
            issues += issue(ImportIssueCode.SOURCE_TERM_UNVERIFIED)
        }
        val scope = if (request.schoolId == "fjnu" && request.sourceId.isNotBlank() &&
            request.sourceId == request.sourceId.trim() && !sourceTerm.isNullOrBlank() &&
            sourceTerm == sourceTerm.trim() && sourceTerm == request.completeness.selectedSourceTermId) {
            SourceScope(request.schoolId, request.sourceId, sourceTerm)
        } else null

        if (config == null) issues += issue(ImportIssueCode.CONFIG_REQUIRED)
        if (scope != null && boundSemesterId != null && boundSemesterId != request.targetSemesterId) {
            issues += issue(ImportIssueCode.SCOPE_CONFLICT)
        }
        val evidence = request.completeness
        when (evidence.status) {
            CompletenessStatus.UNKNOWN -> issues += issue(ImportIssueCode.COMPLETENESS_UNKNOWN, ImportIssueStage.COMPLETENESS)
            CompletenessStatus.PARTIAL -> issues += issue(ImportIssueCode.PARTIAL_SOURCE, ImportIssueStage.COMPLETENESS)
            CompletenessStatus.VERIFIED_FULL -> {
                val pageCount = evidence.expectedPageCount
                val covered = if (pageCount == null) {
                    // B explicitly verified an unpaginated source; one logical page is represented as index 0.
                    evidence.pageIndicesRead == listOf(0)
                } else pageCount > 0 && evidence.pageIndicesRead.size == pageCount &&
                    evidence.pageIndicesRead.toSet().size == pageCount &&
                    evidence.pageIndicesRead.all { it in 0 until pageCount }
                if (evidence.basis.isBlank() || !covered) {
                    issues += issue(ImportIssueCode.PARTIAL_SOURCE, ImportIssueStage.COMPLETENESS)
                }
            }
        }

        val observed = request.sourceObservedCount
        if (observed == null) issues += issue(ImportIssueCode.RAW_COUNT_UNKNOWN, ImportIssueStage.COMPLETENESS)
        val reported = listOfNotNull(request.reportedSourceTotalCount, evidence.reportedSourceTotalCount)
        if (reported.any { it < 0 || it != observed } ||
            (request.reportedSourceTotalCount != null && evidence.reportedSourceTotalCount != null &&
                request.reportedSourceTotalCount != evidence.reportedSourceTotalCount)) {
            issues += issue(ImportIssueCode.PARTIAL_SOURCE, ImportIssueStage.COMPLETENESS)
        }

        val rawIndices = request.candidates.map { it.sourceIndex } + request.parseIssues.mapNotNull { it.sourceIndex }
        val represented = rawIndices.toSet()
        if (observed != null && (observed < 0 || rawIndices.any { it !in 0 until observed } ||
            represented.size != observed ||
            request.candidates.groupingBy { it.sourceIndex }.eachCount().any { it.value > 1 })) {
            issues += issue(ImportIssueCode.RAW_COUNT_MISMATCH, ImportIssueStage.COMPLETENESS)
        }

        val parseErrorIndices = request.parseIssues.mapNotNull { it.sourceIndex }.toSet()
        request.parseIssues.filter { it.stage == ImportIssueStage.WARNING && it.sourceIndex != null }.forEach {
            // A parse issue means this raw node did not yield a saveable candidate, even if mislabeled as a warning.
            issues += ImportIssue(ImportIssueStage.VALIDATION, ImportIssueCode.INVALID_ARRANGEMENT, it.sourceIndex)
        }
        val errored = parseErrorIndices.toMutableSet()
        val normalized = mutableListOf<PreviewArrangement>()
        val sourceIds = mutableMapOf<String, PreviewArrangement>()
        val noIdRows = mutableSetOf<RowKey>()
        var duplicates = 0
        for (candidate in request.candidates.sortedBy { it.sourceIndex }.distinctBy { it.sourceIndex }) {
            val index = candidate.sourceIndex
            if (index in parseErrorIndices) continue
            val draft = ArrangementDraft(candidate.name.orEmpty(), candidate.teacher, candidate.location,
                candidate.dayOfWeek ?: 0, candidate.startSection ?: 0, candidate.endSection ?: 0,
                candidate.weeks.orEmpty())
            val ruleIssues = if (config != null) ScheduleRules.validateArrangement(config, draft) else emptyList()
            if (config == null || candidate.name == null || candidate.dayOfWeek == null ||
                candidate.startSection == null || candidate.endSection == null || candidate.weeks == null ||
                ruleIssues.isNotEmpty()) {
                issues += ImportIssue(ImportIssueStage.VALIDATION, ImportIssueCode.INVALID_ARRANGEMENT, index, ruleIssues)
                errored += index
                continue
            }
            val row = PreviewArrangement(index, candidate.sourceEntryId?.trim()?.takeIf { it.isNotEmpty() },
                draft.name.trim(), draft.teacher?.trim()?.takeIf { it.isNotEmpty() },
                draft.location?.trim()?.takeIf { it.isNotEmpty() }, draft.dayOfWeek, draft.startSection,
                draft.endSection, draft.weeks.toSortedSet())
            val key = RowKey(row)
            val prior = row.sourceEntryId?.let { sourceIds[it] }
            when {
                prior != null && RowKey(prior) != key -> {
                    issues += ImportIssue(ImportIssueStage.VALIDATION, ImportIssueCode.DUPLICATE_SOURCE_ID, index)
                    errored += index
                }
                prior != null || (row.sourceEntryId == null && key in noIdRows) -> duplicates++
                else -> {
                    normalized += row
                    if (row.sourceEntryId != null) sourceIds[row.sourceEntryId] = row else noIdRows += key
                    if (row.teacher == null) issues += ImportIssue(ImportIssueStage.WARNING, ImportIssueCode.TEACHER_MISSING, index)
                    if (row.location == null) issues += ImportIssue(ImportIssueStage.WARNING, ImportIssueCode.LOCATION_MISSING, index)
                }
            }
        }
        if (normalized.isEmpty()) issues += issue(ImportIssueCode.EMPTY_SNAPSHOT)
        val errorCount = errored.size
        if (observed != null && observed != normalized.size + duplicates + errorCount) {
            issues += issue(ImportIssueCode.RAW_COUNT_MISMATCH, ImportIssueStage.COMPLETENESS)
        }
        return ImportPreview(previewId, request.targetSemesterId, scope, observed, normalized.size,
            duplicates, errorCount, if (scope == null) null else replaceCount, normalized.toList(),
            issues.distinct(), errorCount == 0 && issues.none { it.stage != ImportIssueStage.WARNING } && normalized.isNotEmpty())
    }

    private fun issue(code: ImportIssueCode, stage: ImportIssueStage = ImportIssueStage.VALIDATION) =
        ImportIssue(stage, code)

    private data class RowKey(
        val name: String, val teacher: String?, val location: String?, val day: Int,
        val start: Int, val end: Int, val weeks: Set<Int>,
    ) {
        constructor(row: PreviewArrangement) : this(row.name, row.teacher, row.location,
            row.dayOfWeek, row.startSection, row.endSection, row.weeks)
    }
}
