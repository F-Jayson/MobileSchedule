package com.example.mobileschedule

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.View
import android.widget.DatePicker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import org.hamcrest.Matcher
import com.example.mobileschedule.ui.settings.ConfigEditorState
import com.example.mobileschedule.ui.settings.SectionTimeText
import com.example.mobileschedule.ui.settings.SemesterFormInput
import com.example.mobileschedule.ui.settings.SettingsConfigScreen
import com.example.mobileschedule.ui.settings.SettingsHomeScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsConfigUiTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 9, 21)

    @Test fun emptySettingsOffersLocalSemesterCreation() {
        var create = 0
        compose.setContent {
            TestHost {
                SettingsHomeScreen(SettingsHomeUiState.Ready(emptyList(), null),
                    onCreate = { create++ }, onEdit = {}, onActivate = {})
            }
        }
        compose.onNodeWithTag("settings_create").assertIsDisplayed().performClick()
        assertEquals(1, create)
    }

    @Test fun datePickerWritesTheUserSelectedMonday() {
        compose.setContent {
            var input by remember { mutableStateOf(SemesterFormInput()) }
            TestHost {
                SettingsConfigScreen(ConfigEditorState.Editing(null, 0, SemesterFormInput(), input),
                    onInputChange = { input = it }, onSave = {}, onConfirmImpact = {},
                    onDismissImpact = {}, onBack = {})
            }
        }
        compose.onNodeWithTag("config_monday").performClick()
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Choose 2026-09-21"
            override fun perform(uiController: UiController, view: View) {
                (view as DatePicker).updateDate(2026, 8, 21)
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithTag("config_monday").assertTextContains("2026-09-21", substring = true)
    }

    @Test fun invalidFieldsAreShownBesideInputsAndKeyboardDoesNotHideSave() {
        compose.setContent {
            var input by remember { mutableStateOf(SemesterFormInput()) }
            var attempted by remember { mutableStateOf(false) }
            TestHost {
                SettingsConfigScreen(
                    state = ConfigEditorState.Editing(null, 0, SemesterFormInput(), input, showErrors = attempted),
                    onInputChange = { input = it }, onSave = { attempted = true },
                    onConfirmImpact = {}, onDismissImpact = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("save_config").performClick()
        compose.onNodeWithTag("config_error_name").assertIsDisplayed()
        compose.onNodeWithTag("config_error_monday").assertIsDisplayed()
        compose.onNodeWithTag("config_error_weeks").assertIsDisplayed()
        compose.onNodeWithTag("config_error_sections").assertIsDisplayed()
        compose.onNodeWithTag("config_name").performTextInput("测试学期")
        compose.onNodeWithTag("config_weeks").performTextInput("12")
        compose.onNodeWithTag("config_sections").performTextInput("2")
        compose.onNodeWithTag("times_toggle").performClick()
        compose.onNodeWithTag("config_fields").performScrollToIndex(6)
        compose.onNodeWithTag("time_start_2").performClick()
        compose.onNodeWithTag("save_config").assertIsDisplayed()
        compose.onNodeWithTag("config_error_time_1").performScrollTo().assertIsDisplayed()
        saveScreenshot("settings-keyboard-fixture.png")
    }

    @Test fun nonMondayShowsExplicitCorrectionAndImpactDialogNamesTheRisk() {
        val initial = SemesterFormInput("已有学期", monday, "12", "2", true,
            mapOf(1 to SectionTimeText("08:00", "08:45"), 2 to SectionTimeText("08:50", "09:35")))
        compose.setContent {
            var input by remember { mutableStateOf(initial.copy(firstWeekDate = monday.plusDays(2))) }
            var pending by remember { mutableStateOf(false) }
            TestHost {
                SettingsConfigScreen(
                    state = ConfigEditorState.Editing(7, 3, initial, input,
                        showErrors = true, impactPending = pending),
                    onInputChange = { input = it }, onSave = { pending = true },
                    onConfirmImpact = { pending = false }, onDismissImpact = { pending = false }, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("config_error_monday").assertTextContains("周一", substring = true)
        compose.onNodeWithTag("use_suggested_monday").performClick()
        compose.runOnIdle { /* recomposition completed */ }
        compose.onNodeWithTag("config_error_monday").assertDoesNotExist()
        compose.onNodeWithTag("config_weeks").performTextReplacement("8")
        compose.onNodeWithTag("save_config").performClick()
        compose.onNodeWithTag("impact_dialog").assertIsDisplayed()
        compose.onNodeWithTag("impact_message").assertTextContains("已有课程", substring = true)
        compose.onNodeWithTag("impact_confirm").performClick()
    }

    @Test fun dirtyBackRequiresDiscardConfirmation() {
        val initial = SemesterFormInput("已有学期", monday, "12", "2")
        var exits = 0
        compose.setContent {
            var input by remember { mutableStateOf(initial.copy(displayName = "未保存名称")) }
            TestHost {
                SettingsConfigScreen(
                    state = ConfigEditorState.Editing(7, 3, initial, input),
                    onInputChange = { input = it }, onSave = {}, onConfirmImpact = {}, onDismissImpact = {},
                    onBack = { exits++ },
                )
            }
        }
        compose.onNodeWithTag("config_back").performClick()
        compose.onNodeWithTag("discard_dialog").assertIsDisplayed()
        compose.onNodeWithTag("discard_keep").performClick()
        assertEquals(0, exits)
        compose.onNodeWithTag("config_back").performClick()
        compose.onNodeWithTag("discard_confirm").performClick()
        assertEquals(1, exits)
    }

    @Test fun savingKeepsEditorOpenAndDisablesLeaving() {
        var exits = 0
        val input = SemesterFormInput("已有学期", monday, "12", "2")
        compose.setContent {
            TestHost {
                SettingsConfigScreen(
                    state = ConfigEditorState.Editing(7, 3, input, input, saving = true),
                    onInputChange = {}, onSave = {}, onConfirmImpact = {}, onDismissImpact = {},
                    onBack = { exits++ },
                )
            }
        }
        compose.onNodeWithTag("config_back").assertIsNotEnabled()
        compose.onNodeWithTag("save_config").assertIsNotEnabled()
        compose.onNodeWithTag("config_name").assertIsNotEnabled()
        assertEquals(0, exits)
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileScheduleTest")
        }) ?: error("Cannot create screenshot")
        resolver.openOutputStream(uri)?.use { output ->
            (instrumentation.uiAutomation.takeScreenshot() ?: error("Cannot capture screen"))
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Cannot write screenshot")
    }

    @Composable
    private fun TestHost(content: @Composable () -> Unit) {
        MaterialTheme {
            Scaffold { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
        }
    }
}
