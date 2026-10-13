package com.example.mobileschedule.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mobileschedule.data.model.DownloadMirror

@Composable
fun DownloadMirrorDialog(
    currentMirror: DownloadMirror,
    currentCustomPrefix: String,
    currentBlogOrigin: String = DownloadMirror.DEFAULT_BLOG_ORIGIN,
    onDismiss: () -> Unit,
    onConfirm: (DownloadMirror, String, String) -> Unit,
) {
    var selectedMirror by remember { mutableStateOf(currentMirror) }
    var customPrefix by remember { mutableStateOf(currentCustomPrefix) }
    var blogOrigin by remember { mutableStateOf(currentBlogOrigin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.testTag("download_mirror_dialog"),
        title = {
            Text("选择更新下载源", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "若所处网络环境访问 GitHub 较慢或连接失败，可切换为国内加速镜像源：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                DownloadMirror.entries.forEach { mirror ->
                    val isSelected = mirror == selectedMirror
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMirror = mirror }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedMirror = mirror },
                            modifier = Modifier.testTag("mirror_radio_${mirror.key}"),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                mirror.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                mirror.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (selectedMirror == DownloadMirror.BLOG) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = blogOrigin,
                        onValueChange = { blogOrigin = it },
                        label = { Text("博客基址 URL") },
                        placeholder = { Text(DownloadMirror.DEFAULT_BLOG_ORIGIN) },
                        supportingText = { Text("默认: ${DownloadMirror.DEFAULT_BLOG_ORIGIN}，用于检查更新与获取高速下载直链") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mirror_blog_origin_input"),
                    )
                }

                if (selectedMirror == DownloadMirror.CUSTOM) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customPrefix,
                        onValueChange = { customPrefix = it },
                        label = { Text("镜像前缀 URL") },
                        placeholder = { Text("https://your-mirror-proxy.com/") },
                        supportingText = { Text("加速反向代理前缀，将拼接到 GitHub 直链前") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mirror_custom_prefix_input"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalOrigin = if (blogOrigin.isBlank()) DownloadMirror.DEFAULT_BLOG_ORIGIN else blogOrigin.trim()
                    onConfirm(selectedMirror, customPrefix.trim(), finalOrigin)
                },
                modifier = Modifier.testTag("mirror_dialog_confirm"),
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("mirror_dialog_dismiss"),
            ) {
                Text("取消")
            }
        },
    )
}
