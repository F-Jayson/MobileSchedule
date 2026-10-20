package com.example.mobileschedule.ui.today.countdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownBottomSheet(
    countdowns: List<CountdownTarget>,
    onDismiss: () -> Unit,
    onPinTarget: (String?) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onAddCustom: (String, LocalDate, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("countdown_bottom_sheet"),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "目标倒计时",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "设置心仪的考试或假期，在首页实时追踪",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("countdown_add_button"),
                ) {
                    Text("＋", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("新增", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Countdown Items List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(countdowns, key = { it.id }) { item ->
                    CountdownItemRow(
                        item = item,
                        onPin = {
                            if (item.isPinned) {
                                onPinTarget(null)
                            } else {
                                onPinTarget(item.id)
                            }
                        },
                        onDelete = { onDeleteCustom(item.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCountdownDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, date, note ->
                onAddCustom(title, date, note)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun CountdownItemRow(
    item: CountdownTarget,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPinned) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
        border = if (item.isPinned) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.category.icon, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "已置顶",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                val dateStr = item.targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                Text(
                    text = "$dateStr · ${item.motto}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Right side: Days number
            Column(horizontalAlignment = Alignment.End) {
                if (item.daysRemaining > 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${item.daysRemaining}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 1.dp, start = 1.dp),
                        )
                    }
                } else {
                    Text(
                        text = item.displayDaysText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPin,
                        modifier = Modifier.size(28.dp).testTag("pin_${item.id}"),
                    ) {
                        Text(
                            text = if (item.isPinned) "📌" else "📍",
                            fontSize = 15.sp,
                        )
                    }
                    if (item.isCustom) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).testTag("delete_${item.id}"),
                        ) {
                            Text(
                                text = "🗑️",
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCountdownDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, date: LocalDate, note: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val tomorrow = remember { LocalDate.now().plusDays(1) }
    var year by remember { mutableIntStateOf(tomorrow.year) }
    var month by remember { mutableIntStateOf(tomorrow.monthValue) }
    var day by remember { mutableIntStateOf(tomorrow.dayOfMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义目标") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("目标名称 (如：毕业设计答辩)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_countdown_title"),
                )

                // Date selectors (Year, Month, Day)
                Text(
                    text = "目标日期",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = year.toString(),
                        onValueChange = { it.toIntOrNull()?.let { y -> year = y } },
                        label = { Text("年") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f).testTag("add_countdown_year"),
                    )
                    OutlinedTextField(
                        value = month.toString(),
                        onValueChange = { it.toIntOrNull()?.let { m -> if (m in 1..12) month = m } },
                        label = { Text("月") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_countdown_month"),
                    )
                    OutlinedTextField(
                        value = day.toString(),
                        onValueChange = { it.toIntOrNull()?.let { d -> if (d in 1..31) day = d } },
                        label = { Text("日") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_countdown_day"),
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("鼓励寄语 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_countdown_note"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeDate = try {
                        LocalDate.of(year, month, day)
                    } catch (_: Exception) {
                        LocalDate.now().plusDays(1)
                    }
                    onConfirm(title, safeDate, note)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("add_countdown_confirm"),
            ) {
                Text("确定添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
