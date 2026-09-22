package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.R
import com.example.mobileschedule.ui.common.FoundationPage

@Composable
fun ScheduleRoute(viewModel: ScheduleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(state)
}

@Composable
fun ScheduleScreen(state: ScheduleUiState) {
    when (state) {
        ScheduleUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        ScheduleUiState.Error -> FoundationPage(
            title = stringResource(R.string.schedule_error_title),
            description = stringResource(R.string.schedule_error_body),
            modifier = Modifier.testTag("schedule_error"),
        )
        is ScheduleUiState.Ready -> if (state.courses.isEmpty()) {
            FoundationPage(
                title = stringResource(R.string.schedule_empty_title),
                description = stringResource(R.string.schedule_empty_body),
                modifier = Modifier.testTag("schedule_empty"),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        stringResource(R.string.schedule_foundation_title),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                items(state.courses, key = { it.id }) { course ->
                    ListItem(
                        headlineContent = { Text(course.name) },
                        supportingContent = { Text(course.location) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
