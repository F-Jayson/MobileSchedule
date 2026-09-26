package com.example.mobileschedule.data.importer

import com.example.mobileschedule.data.model.CompletenessEvidence
import com.example.mobileschedule.data.model.CompletenessStatus
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ZhengfangSourceTerm(val yearStart: Int, val term: Int) {
    val sourceLabel: String get() = "$yearStart 学年起始年 · 第 $term 学期"
    val xqm: Int get() = term * term * 3
}

/** The only school-specific request shape observed in the user's successful local API adapter. */
object ZhengfangOnlineRead {
    const val LOGIN_URL = "https://jwglxt.fjnu.edu.cn/jwglxt/xtgl/login_slogin.html"
    const val SCHEDULE_URL = "https://jwglxt.fjnu.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151"

    fun selectTerm(yearText: String, term: Int): ZhengfangSourceTerm? {
        val year = yearText.trim().takeIf { it.matches(Regex("[0-9]{4}")) }?.toIntOrNull()
            ?: return null
        if (year !in 1900..2098 || term !in 1..2) return null
        return ZhengfangSourceTerm(year, term)
    }

    fun postBody(term: ZhengfangSourceTerm): String = "xnm=${term.yearStart}&xqm=${term.xqm}"

    fun isScheduleEndpoint(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("jwglxt.fjnu.edu.cn", ignoreCase = true) &&
            uri.path == "/jwglxt/kbcx/xskbcx_cxXsKb.html"
    } catch (_: IllegalArgumentException) {
        false
    }

    /** WebView.evaluateJavascript returns a JSON-quoted JavaScript string, not the body itself. */
    fun decodeWebViewText(result: String): String? = try {
        (Json.parseToJsonElement(result) as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    } catch (_: IllegalArgumentException) {
        null
    }

    fun parse(rawBody: String, targetSemesterId: Long, selectedTerm: ZhengfangSourceTerm): ParsedZhengfangSchedule {
        val root = runCatching { Json.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
        val schoolTerm = root?.get("xsxx") as? JsonObject
        val echoedYear = schoolTerm?.text("XNM")
        val echoedTerm = schoolTerm?.text("XQM")
        if (echoedYear != null && echoedYear != selectedTerm.yearStart.toString() ||
            echoedTerm != null && echoedTerm != selectedTerm.xqm.toString()) {
            throw sourceTermMismatch()
        }
        (root?.get("kbList") as? JsonArray)?.forEach { item ->
            val row = item as? JsonObject ?: return@forEach
            if (row.text("xnm")?.let { it != selectedTerm.yearStart.toString() } == true ||
                row.text("xqm")?.let { it != selectedTerm.xqm.toString() } == true) {
                throw sourceTermMismatch()
            }
        }
        val sourceTermId = if (echoedYear != null && echoedTerm != null)
            "xnm=$echoedYear;xqm=$echoedTerm" else null
        val schoolLabel = if (sourceTermId != null) {
            val academicYear = schoolTerm?.text("XNMC")
            val termName = schoolTerm?.text("XQMMC")
            if (academicYear != null && termName != null) "$academicYear 学年第${termName}学期"
            else selectedTerm.sourceLabel
        } else selectedTerm.sourceLabel
        return ZhengfangScheduleParser.parseApiResponse(rawBody, ZhengfangParseContext(
            targetSemesterId = targetSemesterId,
            sourceId = "fjnu-zhengfang-web",
            sourceTermId = sourceTermId,
            sourceTermLabel = schoolLabel,
            completeness = CompletenessEvidence(
                CompletenessStatus.UNKNOWN, sourceTermId, emptyList(), null, null,
                "School term echoed in response when present; pagination and full coverage not verified",
            ),
        ))
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun sourceTermMismatch() = ZhengfangParseException(
        ZhengfangParseDiagnostic(ZhengfangParseErrorCode.SOURCE_TERM_MISMATCH))
}
