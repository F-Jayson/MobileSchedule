package com.example.mobileschedule.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.mobileschedule.R
import com.example.mobileschedule.ui.common.FoundationPage

@Composable
fun TodayScreen() {
    FoundationPage(
        title = stringResource(R.string.today_title),
        description = stringResource(R.string.today_body),
        modifier = Modifier.testTag("today_page"),
    )
}
