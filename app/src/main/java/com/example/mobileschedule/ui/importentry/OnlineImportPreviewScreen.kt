package com.example.mobileschedule.ui.importentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mobileschedule.data.importer.ZhengfangImportPreviewSummary
import com.example.mobileschedule.data.importer.ZhengfangParseDiagnostic
import com.example.mobileschedule.data.importer.ZhengfangParseErrorCode
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.ImportIssue
import com.example.mobileschedule.data.model.ImportIssueCode
import com.example.mobileschedule.data.model.ImportIssueStage
import com.example.mobileschedule.data.model.PreviewArrangement
import com.example.mobileschedule.data.model.RuleIssueCode

/** The page displays only the repository's normalized preview; no course is written here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineImportPreviewScreen(
    state: OnlineImportPreviewState,
    onBack: () -> Unit,
    onReadAgain: () -> Unit,
    onConfirm: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
) {
    var confirmationOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state !is OnlineImportPreviewState.Ready && state !is OnlineImportPreviewState.SaveFailed)
            confirmationOpen = false
    }
    val summary = when (state) {
        is OnlineImportPreviewState.Ready -> state.summary
        is OnlineImportPreviewState.Saving -> state.summary
        is OnlineImportPreviewState.Saved -> state.summary
        is OnlineImportPreviewState.SaveFailed -> state.summary
        else -> null
    }
    val canAskConfirmation = when (state) {
        is OnlineImportPreviewState.Ready -> state.summary.canCommit
        is OnlineImportPreviewState.SaveFailed -> state.retryable && state.summary.canCommit
        else -> false
    }
    if (confirmationOpen && canAskConfirmation && summary?.replacementScope != null &&
        summary.replaceCount != null) {
        AlertDialog(onDismissRequest = { confirmationOpen = false },
            title = { Text("确认替换并保存课表") },
            text = { Text("学校：福建师范大学\n来源学期：${summary.sourceTermLabel ?: summary.sourceTermId} " +
                "（${summary.replacementScope.sourceTermId}）\n将替换此学校、此来源学期已有的 " +
                "${summary.replaceCount} 条课程安排，并保存 ${summary.pendingSaveCount} 条；" +
                "其他来源和学期不受影响。") },
            confirmButton = { TextButton(onClick = {
                confirmationOpen = false
                onConfirm()
            }, modifier = Modifier.testTag("preview_dialog_confirm")) { Text("确认替换并保存") } },
            dismissButton = { TextButton(onClick = { confirmationOpen = false },
                modifier = Modifier.testTag("preview_dialog_cancel")) { Text("取消") } },
            modifier = Modifier.testTag("preview_confirm_dialog"))
    }
    Scaffold(modifier = Modifier.fillMaxSize().testTag("online_import_preview"),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("导入预览") }, windowInsets = WindowInsets(0),
            navigationIcon = { TextButton(onClick = if (state is OnlineImportPreviewState.Saved)
                onOpenSchedule else onBack, enabled = state !is OnlineImportPreviewState.Saving,
                modifier = Modifier.testTag("preview_back")) {
                Text(if (state is OnlineImportPreviewState.Saved) "完成" else "返回")
            } }) },
        bottomBar = {
            if (summary != null) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(when (state) {
                        is OnlineImportPreviewState.Saving -> "正在事务保存，请勿重复操作。"
                        is OnlineImportPreviewState.Saved -> "已保存 ${state.receipt.savedCount} 条课程安排。"
                        is OnlineImportPreviewState.SaveFailed -> state.message
                        else -> if (summary.canCommit) "校验通过，请核对替换范围后确认保存。"
                            else "校验未通过，不能确认保存。旧课表保持不变。"
                    },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = when (state) {
                        is OnlineImportPreviewState.Saved -> onOpenSchedule
                        else -> { { confirmationOpen = true } }
                    }, enabled = canAskConfirmation || state is OnlineImportPreviewState.Saved,
                        modifier = Modifier.fillMaxWidth().testTag("preview_confirm")) {
                        Text(when (state) {
                            is OnlineImportPreviewState.Saved -> "查看周课表"
                            is OnlineImportPreviewState.SaveFailed -> "再次确认保存 ${summary.pendingSaveCount} 条课程安排"
                            else -> "确认保存 ${summary.pendingSaveCount} 条课程安排"
                        })
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                OnlineImportPreviewState.Idle -> Text("本次读取结果已失效，请返回重新读取。")
                OnlineImportPreviewState.Loading -> {
                    CircularProgressIndicator()
                    Text("正在校验课程安排与本地替换范围…")
                }
                is OnlineImportPreviewState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("preview_error"))
                    TextButton(onClick = onReadAgain) { Text("返回重新读取") }
                }
                is OnlineImportPreviewState.Saving -> {
                    CircularProgressIndicator(modifier = Modifier.testTag("preview_saving"))
                    Text("正在保存课程安排并更新活动学期…")
                    PreviewSummaryContent(state.summary, onReadAgain = null)
                }
                is OnlineImportPreviewState.Saved -> {
                    Text("保存成功：写入 ${state.receipt.savedCount} 条课程安排，" +
                        "替换旧安排 ${state.receipt.removedCount} 条。",
                        modifier = Modifier.testTag("preview_saved"),
                        color = MaterialTheme.colorScheme.primary)
                    state.activationError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("preview_activation_error")) }
                    PreviewSummaryContent(state.summary, onReadAgain = null)
                }
                is OnlineImportPreviewState.SaveFailed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("preview_save_error"))
                    PreviewSummaryContent(state.summary, onReadAgain)
                }
                is OnlineImportPreviewState.Ready -> PreviewSummaryContent(state.summary, onReadAgain)
            }
        }
    }
}

@Composable
private fun PreviewSummaryContent(summary: ZhengfangImportPreviewSummary, onReadAgain: (() -> Unit)?) {
                    Text("福建师范大学 · 正方教务", modifier = Modifier.testTag("preview_school"),
                        style = MaterialTheme.typography.titleMedium)
                    Text("来源学期：${summary.sourceTermLabel ?: "未确认"}" +
                        "（${summary.sourceTermId ?: "学校未回显标识"}）",
                        modifier = Modifier.testTag("preview_source_term"))
                    Text("本地目标学期 ID：${summary.targetSemesterId}")
                    Text(completenessText(summary.completeness.status),
                        modifier = Modifier.testTag("preview_block_reason"),
                        color = if (summary.completeness.status == CompletenessStatus.VERIFIED_FULL)
                            MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    Text("读取 ${summary.readCount?.toString() ?: "未知"} · 解析 ${summary.parsedCount} · " +
                        "有效 ${summary.validCount} · 重复 ${summary.duplicateCount} · " +
                        "错误 ${summary.errorCount} · 待保存 ${summary.pendingSaveCount} 条课程安排",
                        modifier = Modifier.testTag("preview_counts"))
                    Text(replacementText(summary), modifier = Modifier.testTag("preview_replacement"))
                    HorizontalDivider()
                    Text("校验与提示", style = MaterialTheme.typography.titleSmall)
                    Text(if (summary.issues.isEmpty()) "无校验问题" else
                        summary.issues.joinToString("\n") { issueText(it, summary.parseDiagnostics) },
                        modifier = Modifier.testTag("preview_issues"),
                        color = if (summary.issues.any { it.stage != ImportIssueStage.WARNING })
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text("课程安排列表（${summary.arrangements.size}）", style = MaterialTheme.typography.titleSmall)
                    if (summary.arrangements.isEmpty()) Text("没有可预览的有效课程安排。")
                    summary.arrangements.forEach { row -> PreviewCourseRow(row) }
                    if (onReadAgain != null) TextButton(onClick = onReadAgain,
                        modifier = Modifier.testTag("preview_read_again")) {
                        Text("返回重新读取")
                    }
}

@Composable
private fun PreviewCourseRow(row: PreviewArrangement) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
        .testTag("preview_course_${row.sourceIndex}"),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(row.name, style = MaterialTheme.typography.titleSmall)
        Text("星期${"一二三四五六日"[row.dayOfWeek - 1]} · 第${row.startSection}–${row.endSection}节 · ${formatWeeks(row.weeks)}",
            style = MaterialTheme.typography.bodyMedium)
        Text("教师：${row.teacher ?: "未提供"} · 地点：${row.location ?: "未提供"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
    }
}

private fun completenessText(status: CompletenessStatus): String = when (status) {
    CompletenessStatus.UNKNOWN -> "无法确认已读取完整学期；请核实学校来源总数或完整分页，当前禁止保存。"
    CompletenessStatus.PARTIAL -> "只读取到部分课表或来源数量不一致，当前禁止保存。"
    CompletenessStatus.VERIFIED_FULL -> "来源课表完整性已核实。"
}

private fun replacementText(summary: ZhengfangImportPreviewSummary): String {
    val scope = summary.replacementScope ?: return "待替换范围：未知；旧课程安排数量：未知。"
    val oldCount = summary.replaceCount?.let { "$it 条课程安排" } ?: "未知"
    return "待替换范围：福建师范大学 / ${scope.sourceId} / ${scope.sourceTermId}；" +
        "仅替换该来源学期已有的 $oldCount，其他来源与学期不变。"
}

private fun issueText(issue: ImportIssue, diagnostics: List<ZhengfangParseDiagnostic>): String {
    val index = issue.sourceIndex?.let { "第 ${it + 1} 条课程安排：" } ?: ""
    val message = when (issue.code) {
        ImportIssueCode.SOURCE_SCHOOL_INVALID -> "来源学校不受支持"
        ImportIssueCode.SOURCE_ID_INVALID -> "来源标识无效"
        ImportIssueCode.SOURCE_TERM_UNVERIFIED -> "学校未回显可核对的来源学期标识"
        ImportIssueCode.COMPLETENESS_UNKNOWN -> "无法确认完整学期覆盖"
        ImportIssueCode.PARTIAL_SOURCE -> "只读取部分课表或来源总数不一致"
        ImportIssueCode.RAW_COUNT_UNKNOWN -> "原始课程安排数量未知"
        ImportIssueCode.RAW_COUNT_MISMATCH -> "读取数量与有效、重复、错误数量不一致"
        ImportIssueCode.EMPTY_SNAPSHOT -> "零条有效课程安排，禁止覆盖旧课表"
        ImportIssueCode.INVALID_ARRANGEMENT -> {
            val parserReason = if (issue.stage == ImportIssueStage.PARSE)
                diagnostics.firstOrNull { it.sourceIndex == issue.sourceIndex }?.let(::parseDiagnosticText)
            else null
            parserReason ?: issue.ruleIssues.map(::ruleIssueText).distinct().takeIf { it.isNotEmpty() }
                ?.joinToString("、") ?: "必需字段、周次或节次无效"
        }
        ImportIssueCode.DUPLICATE_SOURCE_ID -> "同一来源安排标识对应不同内容"
        ImportIssueCode.CONFIG_REQUIRED -> "请先配置本地目标学期与节次"
        ImportIssueCode.SCOPE_CONFLICT -> "该学校来源学期已绑定其他本地学期"
        ImportIssueCode.TARGET_SEMESTER_NOT_FOUND -> "本地目标学期不存在"
        ImportIssueCode.TEACHER_MISSING -> "教师未提供，可继续预览"
        ImportIssueCode.LOCATION_MISSING -> "地点未提供，可继续预览"
    }
    return "$index$message"
}

private fun parseDiagnosticText(diagnostic: ZhengfangParseDiagnostic): String {
    val field = when (diagnostic.field) {
        "kcmc", "title", "name" -> "课程名称"
        "xm", "teacher" -> "教师"
        "cdmc", "place", "position" -> "地点"
        "xqj", "weekday", "day" -> "星期"
        "jc", "list_sessions", "startSection", "endSection" -> "节次"
        "zcd", "list_weeks", "weeks" -> "周次"
        else -> "必需字段"
    }
    return when (diagnostic.code) {
        ZhengfangParseErrorCode.MISSING_REQUIRED_FIELD -> "${field}缺失"
        ZhengfangParseErrorCode.INVALID_FIELD_TYPE -> "${field}类型无效"
        ZhengfangParseErrorCode.INVALID_SECTION_FORMAT -> "节次格式无效"
        ZhengfangParseErrorCode.INVALID_SECTION_SEQUENCE -> "节次不连续"
        ZhengfangParseErrorCode.INVALID_WEEK_FORMAT -> "周次格式无效"
        ZhengfangParseErrorCode.INVALID_WEEK_RANGE -> "周次超出允许范围"
        ZhengfangParseErrorCode.MALFORMED_ROW -> "课程安排结构无效"
        else -> "课程安排无效"
    }
}

private fun ruleIssueText(issue: com.example.mobileschedule.data.model.RuleIssue): String =
    when (issue.code) {
        RuleIssueCode.ARRANGEMENT_NAME_REQUIRED -> "课程名称缺失"
        RuleIssueCode.DAY_OUT_OF_RANGE -> "星期超出范围"
        RuleIssueCode.SECTION_RANGE_INVALID -> "节次区间无效或超出本地配置"
        RuleIssueCode.WEEKS_REQUIRED -> "周次缺失"
        RuleIssueCode.WEEK_OUT_OF_RANGE -> "周次超出本地学期范围"
        else -> "安排校验失败"
    }

private fun formatWeeks(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return "周次未知"
    val chunks = mutableListOf<String>()
    var first = sorted.first()
    var last = first
    for (week in sorted.drop(1)) {
        if (week == last + 1) last = week
        else {
            chunks += if (first == last) "$first" else "$first–$last"
            first = week
            last = week
        }
    }
    chunks += if (first == last) "$first" else "$first–$last"
    return chunks.joinToString("、") + "周"
}
