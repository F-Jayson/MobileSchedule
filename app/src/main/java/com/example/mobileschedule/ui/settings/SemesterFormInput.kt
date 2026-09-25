package com.example.mobileschedule.ui.settings

import com.example.mobileschedule.data.model.RuleField
import com.example.mobileschedule.data.model.RuleIssue
import com.example.mobileschedule.data.model.RuleIssueCode
import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class SectionTimeText(val start: String = "", val end: String = "")

/** Raw editor values remain intact when validation or persistence fails. */
data class SemesterFormInput(
    val displayName: String = "",
    val firstWeekDate: LocalDate? = null,
    val totalWeeks: String = "",
    val totalSections: String = "",
    val timesEnabled: Boolean = false,
    val sectionTimes: Map<Int, SectionTimeText> = emptyMap(),
) {
    val suggestedMonday: LocalDate?
        get() = firstWeekDate?.takeIf { it.dayOfWeek != DayOfWeek.MONDAY }
            ?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun confirmSuggestedMonday(): SemesterFormInput = copy(firstWeekDate = suggestedMonday ?: firstWeekDate)

    fun validate(): SemesterFormValidation {
        val errors = linkedMapOf<String, String>()
        val weeks = totalWeeks.toIntOrNull().also {
            if (it == null) errors["weeks"] = "请输入整数周数"
        }
        val sections = totalSections.toIntOrNull().also {
            if (it == null) errors["sections"] = "请输入整数节数"
        }
        if (firstWeekDate == null) errors["monday"] = "请选择第1周周一"

        val times = mutableListOf<SectionTime>()
        if (timesEnabled && sections != null && sections in 1..99) {
            for (section in 1..sections) {
                val row = sectionTimes[section] ?: SectionTimeText()
                val start = parseTime(row.start)
                val end = parseTime(row.end)
                if (start == null || end == null) {
                    errors["time_$section"] = "请填写第${section}节完整的 HH:mm 起止时间"
                } else {
                    times += SectionTime(section, start, end)
                }
            }
        }

        // Only syntactic gaps use UI checks. All calendar, range and overlap rules stay with AI-C.
        val probe = SemesterConfigDraft(
            displayName.trim(), firstWeekDate ?: LocalDate.of(2000, 1, 3),
            weeks ?: 1, sections ?: 1,
            if (timesEnabled && errors.keys.none { it.startsWith("time_") }) times else emptyList(),
        )
        ScheduleRules.validateConfig(probe).forEach { issue ->
            val key = issue.fieldKey()
            if (key !in errors) errors[key] = issue.message(suggestedMonday)
        }
        return SemesterFormValidation(if (errors.isEmpty()) probe else null, errors)
    }

    private fun parseTime(raw: String): LocalTime? {
        val value = raw.trim()
        if (!TIME_PATTERN.matches(value)) return null
        return runCatching { LocalTime.parse(value) }.getOrNull()
    }

    companion object {
        private val TIME_PATTERN = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
    }
}

data class SemesterFormValidation(val draft: SemesterConfigDraft?, val errors: Map<String, String>)

internal fun RuleIssue.fieldKey(): String = when (field) {
    RuleField.DISPLAY_NAME -> "name"
    RuleField.FIRST_WEEK_MONDAY -> "monday"
    RuleField.TOTAL_WEEKS -> "weeks"
    RuleField.TOTAL_SECTIONS -> "sections"
    RuleField.SECTION_TIMES -> section?.let { "time_$it" } ?: "times"
    else -> "form"
}

internal fun RuleIssue.message(suggestedMonday: LocalDate? = null): String = when (code) {
    RuleIssueCode.DISPLAY_NAME_REQUIRED -> "请输入学期显示名称"
    RuleIssueCode.FIRST_DAY_NOT_MONDAY -> "必须使用周一；该周周一是 ${suggestedMonday ?: "未知"}，请确认后选择"
    RuleIssueCode.TOTAL_WEEKS_OUT_OF_RANGE -> "总周数须为 1–60"
    RuleIssueCode.TOTAL_SECTIONS_OUT_OF_RANGE -> "节次数量须为 1–99"
    RuleIssueCode.TIMES_INCOMPLETE -> "启用节次时间后须填写每节的起止时间"
    RuleIssueCode.SECTION_ORDER_INVALID -> "节次时间必须从第1节按顺序填写"
    RuleIssueCode.TIME_NOT_MINUTE_PRECISE -> "时间须精确到分钟"
    RuleIssueCode.TIME_RANGE_INVALID -> "开始时间必须早于结束时间，且不能跨午夜"
    RuleIssueCode.TIME_OVERLAP -> "与上一节时间重叠"
    else -> "配置不符合规则，请检查此字段"
}
