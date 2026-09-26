package com.example.mobileschedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import com.example.mobileschedule.data.importer.ParsedZhengfangSchedule
import com.example.mobileschedule.data.importer.toPreviewSummary
import com.example.mobileschedule.data.model.CompletenessStatus
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ImportValidator
import com.example.mobileschedule.ui.importentry.OnlineImportPreviewScreen
import com.example.mobileschedule.ui.importentry.OnlineImportPreviewState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineImportPreviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val config = SemesterConfig(LocalDate.of(2026, 9, 7), 20, 8, emptyList(), 1)

    private fun preview(raw: String, oldCount: Int?, completeness: CompletenessStatus =
        CompletenessStatus.UNKNOWN) : OnlineImportPreviewState.Ready {
        val source = ZhengfangOnlineRead.parse(raw, 7, ZhengfangOnlineRead.selectTerm("2026", 1)!!)
        val parsed = ParsedZhengfangSchedule(source.request.copy(completeness =
            source.request.completeness.copy(status = completeness)), source.parsedRowCount, source.diagnostics)
        val common = ImportValidator.buildPreview("local-preview", parsed.request, config, null, oldCount)
        return OnlineImportPreviewState.Ready(parsed.toPreviewSummary(common))
    }

    @Test fun knownScopeShowsSourceRowsCountsAndOldArrangementsWhileConfirmationIsBlocked() {
        val state = preview("""{"xsxx":{"XNM":"2026","XQM":"3","XNMC":"2026-2027","XQMMC":"1"},"sjkList":[],"kbList":[{"kcmc":"合成软件课","xm":"合成教师","cdmc":"合成教室","jc":"3-4节","xqj":"2","zcd":"1-2周,5周","xnm":"2026","xqm":"3"}]}""", 3)
        compose.setContent { MaterialTheme { OnlineImportPreviewScreen(state, {}, {}) } }

        compose.onNodeWithTag("preview_school").assertTextContains("福建师范大学", substring = true)
        compose.onNodeWithTag("preview_source_term").assertTextContains("2026-2027 学年第1学期", substring = true)
        compose.onNodeWithTag("preview_counts").assertTextContains("读取 1", substring = true)
        compose.onNodeWithTag("preview_counts").assertTextContains("有效 1", substring = true)
        compose.onNodeWithTag("preview_counts").assertTextContains("重复 0", substring = true)
        compose.onNodeWithTag("preview_counts").assertTextContains("错误 0", substring = true)
        compose.onNodeWithTag("preview_replacement").assertTextContains("3 条课程安排", substring = true)
        compose.onNodeWithTag("preview_replacement").assertTextContains("xnm=2026;xqm=3", substring = true)
        compose.onNodeWithTag("preview_course_0").assertTextContains("合成软件课", substring = true)
        compose.onNodeWithTag("preview_course_0").assertTextContains("1–2、5周", substring = true)
        compose.onNodeWithTag("preview_block_reason").assertTextContains("完整学期", substring = true)
        compose.onNodeWithTag("preview_confirm").assertIsNotEnabled()
    }

    @Test fun badRowAndUnknownReplacementCountStayVisibleButBlocked() {
        val state = preview("""{"xsxx":{"XNM":"2026","XQM":"3"},"sjkList":[],"kbList":[{"kcmc":"合成课","jc":"1-2节","xqj":"1","zcd":"1周"},{"jc":"3-4节","xqj":"2","zcd":"2周"}]}""", null)
        compose.setContent { MaterialTheme { OnlineImportPreviewScreen(state, {}, {}) } }

        compose.onNodeWithTag("preview_counts").assertTextContains("错误 1", substring = true)
        compose.onNodeWithTag("preview_replacement").assertTextContains("未知", substring = true)
        compose.onNodeWithTag("preview_issues").assertTextContains("第 2 条", substring = true)
        compose.onNodeWithTag("preview_issues").assertTextContains("课程名称缺失", substring = true)
        compose.onNodeWithTag("preview_confirm").assertIsNotEnabled()
    }

    @Test fun partialSourceExplainsTheBlockAndDoesNotEnableSaving() {
        val state = preview("""{"xsxx":{"XNM":"2026","XQM":"3"},"sjkList":[],"kbList":[{"kcmc":"合成课","jc":"1-2节","xqj":"1","zcd":"1周"}]}""", 0,
            completeness = CompletenessStatus.PARTIAL)
        compose.setContent { MaterialTheme { OnlineImportPreviewScreen(state, {}, {}) } }

        compose.onNodeWithTag("preview_block_reason").assertTextContains("部分", substring = true)
        compose.onNodeWithTag("preview_confirm").assertIsNotEnabled()
    }
}
