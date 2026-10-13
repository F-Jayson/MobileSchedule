package com.example.mobileschedule

import android.webkit.CookieManager
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.ui.importentry.ZhengfangOnlineReadScreen
import com.example.mobileschedule.ui.importentry.OnlineImportPreviewState
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import com.example.mobileschedule.data.importer.toPreviewSummary
import com.example.mobileschedule.data.model.ImportReceipt
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.data.rules.ImportValidator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineImportReadTest {
    @get:Rule val compose = createComposeRule()

    private fun quickSummary() = ZhengfangOnlineRead.withQuickImportAcceptance(
        ZhengfangOnlineRead.parse("""{"xsxx":{"XNM":"2026","XQM":"3"},"sjkList":[],"kbList":[{"kcmc":"合成课程","jc":"1-2节","xqj":"2","zcd":"1-3周"}]}""",
            7, ZhengfangOnlineRead.selectTerm("2026", 1)!!))!!.let { parsed ->
        parsed.toPreviewSummary(ImportValidator.buildPreview("quick-ui", parsed.request,
            SemesterConfig(LocalDate.of(2026, 9, 7), 20, 8, emptyList(), 1), null, 0))
    }

    @Test fun quickImportPrefillsCurrentAcademicTermAndAllowsYearCorrection() {
        val today = LocalDate.now()
        val academicYear = if (today.monthValue >= 9) today.year else today.year - 1
        val academicTerm = if (today.monthValue in 2..8) 2 else 1
        compose.setContent { MobileScheduleTheme {
            ZhengfangOnlineReadScreen(7, {}, initialUrl = "about:blank", quickImport = true,
                previewState = OnlineImportPreviewState.Idle, onPreparePreview = {}, onInvalidatePreview = {})
        } }
        compose.onNodeWithTag("online_year").assertTextContains(academicYear.toString())
        compose.onNodeWithTag("online_term_$academicTerm").assertIsSelected()
        saveSyntheticScreenshot("quick-import-read-entry.png",
            compose.onNodeWithTag("online_read_page").captureToImage().asAndroidBitmap())
        compose.onNodeWithTag("online_year").performTextReplacement("2025")
        compose.onNodeWithTag("online_year").assertTextContains("2025")
        compose.onNodeWithTag("online_read_button").assertIsNotEnabled()
    }

    @Test fun quickImportResumesSavingWithProgressInsteadOfCourseCountConfirmation() {
        compose.setContent { MobileScheduleTheme {
            ZhengfangOnlineReadScreen(7, {}, initialUrl = "about:blank", quickImport = true,
                previewState = OnlineImportPreviewState.Saving(quickSummary()),
                onPreparePreview = {}, onInvalidatePreview = {})
        } }
        compose.onNodeWithTag("quick_import_progress").assertIsDisplayed()
        compose.onNodeWithTag("preview_confirm").assertDoesNotExist()
        saveSyntheticScreenshot("quick-import-progress.png",
            compose.onNodeWithTag("quick_import_progress").captureToImage().asAndroidBitmap())
    }

    @Test fun quickImportCompletedStateReturnsToScheduleOnceAfterScreenRecreation() {
        val summary = quickSummary()
        var opens = 0
        compose.setContent { MobileScheduleTheme {
            ZhengfangOnlineReadScreen(7, {}, initialUrl = "about:blank", quickImport = true,
                previewState = OnlineImportPreviewState.Saved(summary,
                    ImportReceipt("quick-ui-batch", summary.replacementScope!!, 7, 0, 1)),
                onPreparePreview = {}, onInvalidatePreview = {}, onOpenSchedule = { opens++ })
        } }
        compose.waitUntil(timeoutMillis = 10_000) { opens == 1 }
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithTag("preview_confirm").assertDoesNotExist()
    }

    @Test fun quickImportActivationFailureShowsSavedResultForRecovery() {
        val summary = quickSummary()
        var opens = 0
        compose.setContent { MobileScheduleTheme {
            ZhengfangOnlineReadScreen(7, {}, initialUrl = "about:blank", quickImport = true,
                previewState = OnlineImportPreviewState.Saved(summary,
                    ImportReceipt("quick-ui-batch", summary.replacementScope!!, 7, 0, 1), "请在设置中选择该学期。"),
                onPreparePreview = {}, onInvalidatePreview = {}, onOpenSchedule = { opens++ })
        } }
        compose.onNodeWithTag("preview_saved").assertIsDisplayed().assertTextContains("写入 1 条", substring = true)
        compose.runOnIdle { assertEquals(0, opens) }
    }

    @Test fun readRequiresASchoolPageAndBackLeavesWithoutSaving() {
        var exits = 0
        compose.setContent { MobileScheduleTheme {
            ZhengfangOnlineReadScreen(targetSemesterId = 7, onExit = { exits++ },
                initialUrl = "about:blank", previewState = OnlineImportPreviewState.Idle,
                onPreparePreview = {}, onInvalidatePreview = {})
        } }

        compose.onNodeWithTag("online_read_page").assertIsDisplayed()
        compose.onNodeWithTag("online_read_button").assertIsNotEnabled()
        compose.onNodeWithTag("online_back").performClick()
        assertEquals(1, exits)
    }

    @Test fun leavingImportClearsTemporaryWebCookiesBeforeReturningToLocalSchedule() {
        val cookies = CookieManager.getInstance()
        val dummySite = "https://cleanup.invalid/"
        cookies.setCookie(dummySite, "synthetic_session=temporary; Path=/")
        cookies.flush()
        assertTrue(cookies.getCookie(dummySite)?.contains("synthetic_session=temporary") == true)
        var exits = 0
        compose.setContent { MobileScheduleTheme {
            ZhengfangOnlineReadScreen(targetSemesterId = 7, onExit = { exits++ },
                initialUrl = "about:blank", previewState = OnlineImportPreviewState.Idle,
                onPreparePreview = {}, onInvalidatePreview = {})
        } }

        compose.onNodeWithTag("online_back").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { cookies.getCookie(dummySite).isNullOrBlank() }
        compose.runOnIdle { assertEquals(1, exits) }
    }
}
