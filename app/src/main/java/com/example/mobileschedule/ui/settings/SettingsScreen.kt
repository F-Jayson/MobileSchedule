package com.example.mobileschedule.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.mobileschedule.R
import com.example.mobileschedule.ui.common.FoundationPage

@Composable
fun SettingsScreen() {
    FoundationPage(
        title = stringResource(R.string.settings_title),
        description = stringResource(R.string.settings_body),
        modifier = Modifier.testTag("settings_page"),
    )
}
