package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessEvidence
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.ImportCandidate
import com.example.mobileschedule.data.model.ImportRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

data class ParsedZhengfangSchedule(val request: ImportRequest, val parsedRowCount: Int)

/** Offline conversion of two known reference shapes; no login, network or database access. */
object ZhengfangScheduleParser {
    private val sectionPattern = Regex("""^(\d+)\s*[-－—~～]\s*(\d+)\s*节?$""")
    private val singleSectionPattern = Regex("""^(\d+)\s*节?$""")
    private val weekPattern = Regex("""^(\d+)(?:\s*[-－—~～]\s*(\d+))?\s*周(?:\s*[（(]\s*(单|双)\s*[）)])?$""")

    /** Parses a captured Zhengfang-style response containing kbList. Other lists remain unverified. */
    fun parseApiResponse(rawJson: String, context: ZhengfangParseContext): ParsedZhengfangSchedule {
        val root = parseJson(rawJson) as? JsonObject
            ?: throw IllegalArgumentException("API response must be a JSON object")
        val rows = root["kbList"] as? JsonArray
            ?: throw IllegalArgumentException("API response must contain a kbList array")
        val extra = root["sjkList"]
        require(extra == null || extra is JsonArray && extra.isEmpty()) {
            "API sjkList is not supported by this parser"
        }
        val candidates = rows.mapIndexed { index, element ->
            val row = element as? JsonObject
                ?: throw IllegalArgumentException("kbList[$index] must be an object")
            val sections = parseSections(row.requiredText("jc", index), index)
            ImportCandidate(
                index, null, row.requiredText("kcmc", index), row.optionalText("xm", index),
                row.optionalText("cdmc", index), row.requiredInt("xqj", index),
                sections.first, sections.second,
                parseWeeks(row.requiredText("zcd", index), index),
            )
        }
        return result(candidates, context, rows.size)
    }

    /** Parses the successful JSON result of the user-provided zfn_api adapter. */
    fun parseNormalizedApiResult(rawJson: String, context: ZhengfangParseContext): ParsedZhengfangSchedule {
        val root = parseJson(rawJson) as? JsonObject
            ?: throw IllegalArgumentException("Normalized API result must be a JSON object")
        require(root.requiredInt("code", -1) == 1000) { "Normalized API result is not successful" }
        val data = root["data"] as? JsonObject
            ?: throw IllegalArgumentException("Normalized API result must contain data")
        val count = data.requiredInt("count", -1)
        require(count >= 0) { "Normalized API count must be non-negative" }
        require(context.reportedSourceTotalCount == null || context.reportedSourceTotalCount == count) {
            "Normalized API count disagrees with caller-provided count"
        }
        val extra = data["extra_courses"] as? JsonArray
            ?: throw IllegalArgumentException("Normalized API result must contain extra_courses")
        require(extra.isEmpty()) { "Normalized API extra_courses are not supported by this parser" }
        val rows = data["courses"] as? JsonArray
            ?: throw IllegalArgumentException("Normalized API result must contain courses")
        val candidates = rows.mapIndexed { index, element ->
            val row = element as? JsonObject
                ?: throw IllegalArgumentException("courses[$index] must be an object")
            val sections = row.requiredIntList("list_sessions", index)
            require(sections.isNotEmpty() && sections.zipWithNext().all { (left, right) -> right == left + 1 }) {
                "courses[$index].list_sessions must be a non-empty consecutive section list"
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
        return result(candidates, context, count)
    }

    /** Parses the stage-1 DOM bridge array, whose original DOM node count was not preserved. */
    fun parseDomBridgeRows(rawJson: String, context: ZhengfangParseContext): ParsedZhengfangSchedule {
        val root = parseJson(rawJson)
        val rows = when (root) {
            is JsonArray -> root
            is JsonObject -> root["arrangements"] as? JsonArray
                ?: throw IllegalArgumentException("DOM bridge must contain an arrangements array")
            else -> throw IllegalArgumentException("DOM bridge must be an array or object")
        }
        val candidates = rows.mapIndexed { index, element ->
            val row = element as? JsonObject
                ?: throw IllegalArgumentException("arrangements[$index] must be an object")
            val weeks = row["weeks"] as? JsonArray
                ?: throw IllegalArgumentException("arrangements[$index].weeks must be an array")
            ImportCandidate(
                index, null, row.requiredText("name", index), row.optionalText("teacher", index),
                row.optionalText("position", index), row.requiredInt("day", index),
                row.requiredInt("startSection", index), row.requiredInt("endSection", index),
                weeks.map { (it as? JsonPrimitive)?.intOrNull
                    ?: throw IllegalArgumentException("arrangements[$index].weeks contains a non-integer") }.toSet(),
            )
        }
        return result(candidates, context, null)
    }

    private fun result(candidates: List<ImportCandidate>, context: ZhengfangParseContext, observed: Int?) =
        ParsedZhengfangSchedule(
            ImportRequest(
                context.targetSemesterId, "fjnu", context.sourceId, context.sourceTermId,
                context.sourceTermLabel, observed, context.reportedSourceTotalCount, candidates,
                emptyList(), context.completeness,
            ), candidates.size,
        )

    private fun parseJson(rawJson: String): JsonElement = try {
        Json.parseToJsonElement(rawJson)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid schedule JSON", error)
    }

    private fun JsonObject.requiredText(key: String, index: Int): String =
        optionalText(key, index)
            ?: throw IllegalArgumentException("row[$index].$key must be a non-blank string")

    private fun JsonObject.optionalText(key: String, index: Int): String? {
        val value = this[key] ?: return null
        val primitive = value as? JsonPrimitive
            ?: throw IllegalArgumentException("row[$index].$key must be a string")
        if (!primitive.isString && primitive.contentOrNull != null) {
            throw IllegalArgumentException("row[$index].$key must be a string")
        }
        return primitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.requiredInt(key: String, index: Int): Int =
        (this[key] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("row[$index].$key must be an integer")

    private fun JsonObject.requiredIntList(key: String, index: Int): List<Int> {
        val values = this[key] as? JsonArray
            ?: throw IllegalArgumentException("row[$index].$key must be an array")
        return values.map { (it as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("row[$index].$key contains a non-integer") }
    }

    private fun parseSections(raw: String, index: Int): Pair<Int, Int> {
        sectionPattern.matchEntire(raw)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        singleSectionPattern.matchEntire(raw)?.let {
            val section = it.groupValues[1].toInt()
            return section to section
        }
        throw IllegalArgumentException("row[$index].jc has an unsupported section format")
    }

    private fun parseWeeks(raw: String, index: Int): Set<Int> {
        val weeks = sortedSetOf<Int>()
        raw.split(Regex("""[,，、;；]""")).forEach { fragment ->
            val match = weekPattern.matchEntire(fragment.trim())
                ?: throw IllegalArgumentException("row[$index].zcd has an unsupported week format")
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: start
            require(start <= end && start > 0) { "row[$index].zcd has an invalid week range" }
            require(end <= 100) { "row[$index].zcd exceeds the supported week range" }
            for (week in start..end) {
                if (match.groupValues[3].isEmpty() ||
                    match.groupValues[3] == "单" && week % 2 == 1 ||
                    match.groupValues[3] == "双" && week % 2 == 0) weeks += week
            }
        }
        require(weeks.isNotEmpty()) { "row[$index].zcd resolved to no weeks" }
        return weeks
    }
}
