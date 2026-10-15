package com.example.mobileschedule.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.BuildConfig
import com.example.mobileschedule.data.model.AppUpdateInfo

@Composable
fun ChangelogRoute(
    onBack: () -> Unit,
    onStartUpdate: (AppUpdateInfo) -> Unit,
    viewModel: ChangelogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChangelogScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::checkRemoteUpdate,
        onStartUpdate = onStartUpdate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    uiState: ChangelogUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartUpdate: (AppUpdateInfo) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("changelog_page"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("更新日志", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("changelog_back"),
                    ) {
                        Text("‹", fontSize = 28.sp, fontWeight = FontWeight.Light)
                    }
                },
                actions = {
                    if (uiState.isChecking) {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("changelog_refresh"),
                        ) {
                            Text("↻", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.isChecking) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("changelog_checking_indicator"),
                )
            }

            if (uiState.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("changelog_error_banner"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("changelog_retry"),
                        ) {
                            Text("重试", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("changelog_list"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                uiState.entries.forEach { entry ->
                    ChangelogCard(
                        entry = entry,
                        currentVersion = uiState.currentVersion,
                        onStartUpdate = onStartUpdate,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogCard(
    entry: ChangelogEntry,
    currentVersion: String,
    onStartUpdate: (AppUpdateInfo) -> Unit,
) {
    var isExpanded by remember(entry.versionName) {
        // By default, expand the newest version if it's remote, or if it's the current version
        mutableStateOf(entry.isRemoteNew || entry.versionName == currentVersion)
    }

    val isCurrentVersion = (entry.versionName == currentVersion)

    val cardBorder = when {
        entry.isRemoteNew -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isCurrentVersion -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }

    val containerColor = when {
        entry.isRemoteNew -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        isCurrentVersion -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = cardBorder,
        shadowElevation = if (entry.isRemoteNew) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { isExpanded = !isExpanded }
            .testTag("changelog_card_${entry.versionName}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header Row: Version + Badges + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "v${entry.versionName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.isRemoteNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )

                    if (entry.isRemoteNew) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("changelog_badge_new"),
                        ) {
                            Text(
                                text = "新版本",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else if (isCurrentVersion) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.testTag("changelog_badge_current"),
                        ) {
                            Text(
                                text = "当前版本",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Text(
                    text = entry.releaseDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Subtitle
            Text(
                text = entry.releaseTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Highlights summary list
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.highlights.forEach { highlight ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.isRemoteNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = highlight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            // Expanded Full Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    Text(
                        text = "详细更新内容：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = entry.fullNotes,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("changelog_full_notes_${entry.versionName}"),
                        )
                    }

                    // Update Button for new remote version
                    if (entry.isRemoteNew && entry.remoteUpdateInfo != null) {
                        Button(
                            onClick = { onStartUpdate(entry.remoteUpdateInfo) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("changelog_update_button")
                                .testTag("changelog_update_button_${entry.versionName}"),
                        ) {
                            Text(
                                text = "立即更新至 v${entry.versionName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Expand / Collapse Footer Hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isExpanded) "收起详情 ▴" else "查看完整更新内容 ▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("changelog_expand_toggle_${entry.versionName}"),
                )
            }
        }
    }
}
