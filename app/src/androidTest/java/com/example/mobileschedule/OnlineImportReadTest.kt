package com.example.mobileschedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.ui.importentry.ZhengfangOnlineReadScreen
import com.example.mobileschedule.ui.importentry.OnlineImportPreviewState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineImportReadTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readRequiresASchoolPageAndBackLeavesWithoutSaving() {
        var exits = 0
        compose.setContent { MaterialTheme {
            ZhengfangOnlineReadScreen(targetSemesterId = 7, onExit = { exits++ },
                initialUrl = "about:blank", previewState = OnlineImportPreviewState.Idle,
                onPreparePreview = {}, onInvalidatePreview = {})
        } }

        compose.onNodeWithTag("online_read_page").assertIsDisplayed()
        compose.onNodeWithTag("online_read_button").assertIsNotEnabled()
        compose.onNodeWithTag("online_back").performClick()
        assertEquals(1, exits)
    }
}
