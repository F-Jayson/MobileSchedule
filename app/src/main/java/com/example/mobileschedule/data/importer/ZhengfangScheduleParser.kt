package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessEvidence
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.ImportCandidate
import com.example.mobileschedule.data.model.ImportIssue
import com.example.mobileschedule.data.model.ImportIssueCode
import com.example.mobileschedule.data.model.ImportIssueStage
import com.example.mobileschedule.data.model.ImportRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Caller-supplied source facts; parsing alone cannot establish a selected term or full snapshot. */
data class ZhengfangParseContext(
    val targetSemesterId: Long,
    val sourceId: String,
    val sourceTermId: String? = null,
    val sourceTermLabel: String? = null,
    val reportedSourceTotalCount: Int? = null,
    val completeness: CompletenessEvidence = CompletenessEvidence(
        CompletenessStatus.UNKNOWN, null, emptyList(), null, null,
        "Offline source; selected term and completeness not verified",
    ),
)

enum class ZhengfangParseErrorCode {
    LOGIN_REQUIRED, NON_SCHEDULE_RESPONSE, MALFORMED_JSON, EMPTY_SCHEDULE,
    UNSUPPORTED_EXTRA_COURSES, MALFORMED_ROW, MISSING_REQUIRED_FIELD,
    INVALID_FIELD_TYPE, INVALID_SECTION_FORMAT, INVALID_SECTION_SEQUENCE,
    INVALID_WEEK_FORMAT, INVALID_WEEK_RANGE,
}

/** Contains no raw response value or personal data; sourceIndex is the original zero-based row. */
data class ZhengfangParseDiagnostic(
    val code: ZhengfangParseErrorCode,
    val sourceIndex: Int? = null,
    val field: String? = null,
)

/** A source-level failure has no import preview. Row failures are returned as diagnostics instead. */
class ZhengfangParseException(val diagnostic: ZhengfangParseDiagnostic) :
    IllegalArgumentException("Zhengfang parse error: ${diagnostic.code}")

data class ParsedZhengfangSchedule(
    val request: ImportRequest,
    val parsedRowCount: Int,
    val diagnostics: List<ZhengfangParseDiagnostic> = emptyList(),
)

/** Offline conversion of known reference shapes; no login, network or database access. */
object ZhengfangScheduleParser {
    private val sectionPattern = Regex("""^(\d+)\s*[-－—~～]\s*(\d+)\s*节?$""")
    private val singleSectionPattern = Regex("""^(\d+)\s*节?$""")
    private val weekPattern = Regex("""^(\d+)(?:\s*[-－—~～]\s*(\d+))?\s*周(?:\s*[（(]\s*(单|双)\s*[）)])?$""")

    /** Parses a captured Zhengfang-style response containing kbList. Other lists remain unverified. */
    fun parseApiResponse(rawJson: String, context: ZhengfangParseContext): ParsedZhengfangSchedule {
        val root = parseJson(rawJson) as? JsonObject
            ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        val rows = root["kbList"] as? JsonArray
            ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        val extra = root["sjkList"]
        if (extra != null && extra != JsonNull && (extra !is JsonArray || extra.isNotEmpty())) {
            fail(ZhengfangParseErrorCode.UNSUPPORTED_EXTRA_COURSES)
        }
        if (rows.isEmpty()) fail(ZhengfangParseErrorCode.EMPTY_SCHEDULE)
        val (candidates, diagnostics) = parseRows(rows) { index, row ->
            val sections = parseSections(row.requiredText("jc", index), index)
            ImportCandidate(
                index, null, row.requiredText("kcmc", index), row.optionalText("xm", index),
                row.optionalText("cdmc", index), row.requiredInt("xqj", index),
                sections.first, sections.second,
                parseWeeks(row.requiredText("zcd", index), index),
            )
        }
        return result(candidates, diagnostics, context, rows.size)
    }

    /** Parses the successful JSON result of the user-provided zfn_api adapter. */
    fun parseNormalizedApiResult(rawJson: String, context: ZhengfangParseContext): ParsedZhengfangSchedule {
        val root = parseJson(rawJson) as? JsonObject
            ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        when ((root["code"] as? JsonPrimitive)?.intOrNull) {
            1006 -> fail(ZhengfangParseErrorCode.LOGIN_REQUIRED)
            1005 -> fail(ZhengfangParseErrorCode.EMPTY_SCHEDULE)
            1000 -> Unit
            else -> fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        }
        val data = root["data"] as? JsonObject
            ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        val count = (data["count"] as? JsonPrimitive)?.intOrNull
            ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        if (count < 0) fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        val extra = data["extra_courses"] as? JsonArray
            ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        if (extra.isNotEmpty()) fail(ZhengfangParseErrorCode.UNSUPPORTED_EXTRA_COURSES)
        val rows = data["courses"] as? JsonArray
            ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        if (rows.isEmpty()) fail(ZhengfangParseErrorCode.EMPTY_SCHEDULE)
        val (candidates, diagnostics) = parseRows(rows) { index, row ->
            val sections = row.requiredIntList("list_sessions", index)
            if (sections.isEmpty() || sections.zipWithNext().any { (left, right) -> right != left + 1 }) {
                fail(ZhengfangParseErrorCode.INVALID_SECTION_SEQUENCE, index, "list_sessions")
            }
            ImportCandidate(
                index, null, row.requiredText("title", index), row.optionalText("teacher", index),
                row.optionalText("place", index), row.requiredInt("weekday", index),
                sections.first(), sections.last(), row.requiredIntList("list_weeks", index).toSet(),
            )
        }
        // count reflects source kbList nodes; courses is the adapter's output after its display fix.
        // ImportValidator detects a mismatch and blocks saving if any source node went missing.
        // The adapter computes count from kbList.size; it is not a server-reported semester total.
        return result(candidates, diagnostics, context, count)
    }

    /** Parses the stage-1 DOM bridge array, whose original DOM node count was not preserved. */
    fun parseDomBridgeRows(rawJson: String, context: ZhengfangParseContext): ParsedZhengfangSchedule {
        val root = parseJson(rawJson)
        val rows = when (root) {
            is JsonArray -> root
            is JsonObject -> root["arrangements"] as? JsonArray
                ?: fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
            else -> fail(ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        }
        if (rows.isEmpty()) fail(ZhengfangParseErrorCode.EMPTY_SCHEDULE)
        val (candidates, diagnostics) = parseRows(rows) { index, row ->
            ImportCandidate(
                index, null, row.requiredText("name", index), row.optionalText("teacher", index),
                row.optionalText("position", index), row.requiredInt("day", index),
                row.requiredInt("startSection", index), row.requiredInt("endSection", index),
                row.requiredIntList("weeks", index).toSet(),
            )
        }
        return result(candidates, diagnostics, context, null)
    }

    private fun parseRows(
        rows: JsonArray,
        mapper: (Int, JsonObject) -> ImportCandidate,
    ): Pair<List<ImportCandidate>, List<ZhengfangParseDiagnostic>> {
        val candidates = mutableListOf<ImportCandidate>()
        val diagnostics = mutableListOf<ZhengfangParseDiagnostic>()
        rows.forEachIndexed { index, element ->
            try {
                val row = element as? JsonObject
                    ?: fail(ZhengfangParseErrorCode.MALFORMED_ROW, index)
                candidates += mapper(index, row)
            } catch (error: ZhengfangParseException) {
                diagnostics += error.diagnostic
            }
        }
        return candidates to diagnostics
    }

    private fun result(
        candidates: List<ImportCandidate>, diagnostics: List<ZhengfangParseDiagnostic>,
        context: ZhengfangParseContext, observed: Int?,
    ) =
        ParsedZhengfangSchedule(
            ImportRequest(
                context.targetSemesterId, "fjnu", context.sourceId, context.sourceTermId,
                context.sourceTermLabel, observed, context.reportedSourceTotalCount, candidates,
                diagnostics.mapNotNull { diagnostic -> diagnostic.sourceIndex?.let { index ->
                    ImportIssue(ImportIssueStage.PARSE, ImportIssueCode.INVALID_ARRANGEMENT, index)
                } }, context.completeness,
            ), candidates.size, diagnostics,
        )

    private fun parseJson(rawJson: String): JsonElement {
        val trimmed = rawJson.trimStart { it.isWhitespace() || it == '\uFEFF' }
        if (trimmed.startsWith("<")) {
            val login = rawJson.contains("login_slogin", ignoreCase = true) ||
                rawJson.contains("用户登录") ||
                rawJson.contains("<form", ignoreCase = true) && rawJson.contains("login", ignoreCase = true)
            fail(if (login) ZhengfangParseErrorCode.LOGIN_REQUIRED else ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE)
        }
        return try {
            Json.parseToJsonElement(trimmed)
        } catch (_: Exception) {
            fail(ZhengfangParseErrorCode.MALFORMED_JSON)
        }
    }

    private fun fail(code: ZhengfangParseErrorCode, index: Int? = null, field: String? = null): Nothing =
        throw ZhengfangParseException(ZhengfangParseDiagnostic(code, index, field))

    private fun JsonObject.requiredText(key: String, index: Int): String {
        if (this[key] == null || this[key] == JsonNull) {
            fail(ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD, index, key)
        }
        return optionalText(key, index)
            ?: fail(ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD, index, key)
    }

    private fun JsonObject.optionalText(key: String, index: Int): String? {
        val value = this[key] ?: return null
        if (value == JsonNull) return null
        val primitive = value as? JsonPrimitive
            ?: fail(ZhengfangParseErrorCode.INVALID_FIELD_TYPE, index, key)
        if (!primitive.isString) fail(ZhengfangParseErrorCode.INVALID_FIELD_TYPE, index, key)
        return primitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.requiredInt(key: String, index: Int): Int {
        val value = this[key] ?: fail(ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD, index, key)
        if (value == JsonNull) fail(ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD, index, key)
        return (value as? JsonPrimitive)?.intOrNull
            ?: fail(ZhengfangParseErrorCode.INVALID_FIELD_TYPE, index, key)
    }

    private fun JsonObject.requiredIntList(key: String, index: Int): List<Int> {
        val value = this[key] ?: fail(ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD, index, key)
        if (value == JsonNull) fail(ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD, index, key)
        val values = value as? JsonArray
            ?: fail(ZhengfangParseErrorCode.INVALID_FIELD_TYPE, index, key)
        return values.map { (it as? JsonPrimitive)?.intOrNull
            ?: fail(ZhengfangParseErrorCode.INVALID_FIELD_TYPE, index, key) }
    }

    private fun parseSections(raw: String, index: Int): Pair<Int, Int> {
        sectionPattern.matchEntire(raw)?.let {
            val start = it.groupValues[1].toIntOrNull()
            val end = it.groupValues[2].toIntOrNull()
            if (start != null && end != null) return start to end
        }
        singleSectionPattern.matchEntire(raw)?.let {
            it.groupValues[1].toIntOrNull()?.let { section -> return section to section }
        }
        fail(ZhengfangParseErrorCode.INVALID_SECTION_FORMAT, index, "jc")
    }

    private fun parseWeeks(raw: String, index: Int): Set<Int> {
        val weeks = sortedSetOf<Int>()
        raw.split(Regex("""[,，、;；]""")).forEach { fragment ->
            val match = weekPattern.matchEntire(fragment.trim())
                ?: fail(ZhengfangParseErrorCode.INVALID_WEEK_FORMAT, index, "zcd")
            val start = match.groupValues[1].toIntOrNull()
                ?: fail(ZhengfangParseErrorCode.INVALID_WEEK_FORMAT, index, "zcd")
            val endText = match.groupValues[2]
            val end = if (endText.isEmpty()) start else endText.toIntOrNull()
                ?: fail(ZhengfangParseErrorCode.INVALID_WEEK_FORMAT, index, "zcd")
            if (start <= 0 || start > end || end > 100) {
                fail(ZhengfangParseErrorCode.INVALID_WEEK_RANGE, index, "zcd")
            }
            for (week in start..end) {
                if (match.groupValues[3].isEmpty() ||
                    match.groupValues[3] == "单" && week % 2 == 1 ||
                    match.groupValues[3] == "双" && week % 2 == 0) weeks += week
            }
        }
        if (weeks.isEmpty()) fail(ZhengfangParseErrorCode.INVALID_WEEK_RANGE, index, "zcd")
        return weeks
    }
}
