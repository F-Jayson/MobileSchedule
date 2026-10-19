package com.example.mobileschedule.ui.schedule.export

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.rules.ScheduleRules
import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleExportSheet(
    isOpen: Boolean,
    activeWeek: ActiveWeek?,
    today: LocalDate = LocalDate.now(),
    displayOptions: ScheduleDisplayOptions = ScheduleDisplayOptions(),
    onDismiss: () -> Unit,
) {
    if (!isOpen || activeWeek == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTheme by remember { mutableStateOf(ExportTheme.LIGHT) }
    var selectedResolution by remember { mutableStateOf(ExportResolution.SUPER_2X) }
    var includeWeekend by remember { mutableStateOf(displayOptions.showWeekend) }
    var includeNonCurrentWeek by remember { mutableStateOf(displayOptions.showNonCurrentWeekCourses) }
    var showWatermark by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    val config = activeWeek.semester.config ?: return
    val maxSections = config.totalSections.coerceAtLeast(1)

    // 计算当前配置下的尺寸预估
    val daysCount = if (includeWeekend) 7 else 5
    val dimensions = remember(selectedResolution, daysCount, maxSections, showWatermark) {
        ExportDimensions.calculate(
            scale = selectedResolution.scale,
            daysCount = daysCount,
            maxSections = maxSections,
            showWatermark = showWatermark,
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isExporting) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("schedule_export_sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 标题栏
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "导出课表长图",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "生成适合分享或设为手机壁纸的高清长图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isExporting,
                    modifier = Modifier.testTag("export_dismiss_button"),
                ) {
                    Text("取消")
                }
            }

            Spacer(Modifier.height(16.dp))

            // 规格预览卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("export_preview_summary"),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "${activeWeek.semester.displayName} · 第 ${activeWeek.schedule.week} 周",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "预计分辨率: ${dimensions.totalWidth} × ${dimensions.totalHeight} px · ${selectedTheme.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("export_dimensions_text"),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            selectedResolution.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 1. 壁纸色彩与主题选择
            Text(
                "长图样式与色彩",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExportTheme.entries.forEach { theme ->
                    val isSelected = selectedTheme == theme
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isExporting) { selectedTheme = theme }
                            .testTag("export_theme_${theme.name.lowercase()}"),
                    ) {
                        Column(
                            Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                theme.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 2. 超采样画质选择
            Text(
                "超清画质倍率",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExportResolution.entries.forEach { res ->
                    val isSelected = selectedResolution == res
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isExporting) { selectedResolution = res }
                            .testTag("export_res_${res.scale.toInt()}x"),
                    ) {
                        Column(
                            Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                res.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 3. 内容选项开关
            Text(
                "内容与版式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))

            // 包含周末开关
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("包含周末 (周六日)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("关闭时仅渲染周一至周五的 5 列课表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = includeWeekend,
                    onCheckedChange = { includeWeekend = it },
                    enabled = !isExporting,
                    modifier = Modifier.testTag("export_switch_weekend"),
                )
            }

            // 包含非本周课程开关
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("包含非本周课程", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("以 45% 透明度与虚线边框灰显渲染非本周课程", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = includeNonCurrentWeek,
                    onCheckedChange = { includeNonCurrentWeek = it },
                    enabled = !isExporting,
                    modifier = Modifier.testTag("export_switch_non_current_week"),
                )
            }

            // 底部小标与水印
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("底部校名水印小标", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("在长图最下方添加“福建师范大学移动课表”标识", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = showWatermark,
                    onCheckedChange = { showWatermark = it },
                    enabled = !isExporting,
                    modifier = Modifier.testTag("export_switch_watermark"),
                )
            }

            Spacer(Modifier.height(24.dp))

            // 导出操作按钮组
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 保存到相册
                Button(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            try {
                                val arrangements = if (includeNonCurrentWeek) {
                                    activeWeek.schedule.allArrangements
                                } else {
                                    activeWeek.schedule.arrangements
                                }
                                val options = ExportOptions(
                                    theme = selectedTheme,
                                    resolution = selectedResolution,
                                    showWeekend = includeWeekend,
                                    firstDayOfWeek = displayOptions.firstDayOfWeek,
                                    showNonCurrentWeekCourses = includeNonCurrentWeek,
                                    showLocation = displayOptions.showLocation,
                                    showTeacher = displayOptions.showTeacher,
                                    showWatermark = showWatermark,
                                )
                                val bitmap = ScheduleImageExporter.renderBitmap(
                                    semesterName = activeWeek.semester.displayName,
                                    schoolName = "福建师范大学",
                                    weekNum = activeWeek.schedule.week,
                                    firstWeekMonday = config.firstWeekMonday,
                                    maxSections = maxSections,
                                    sectionTimes = config.sectionTimes.ifEmpty { ScheduleRules.defaultSectionTimes },
                                    arrangements = arrangements,
                                    today = today,
                                    options = options,
                                )
                                val filename = "MobileSchedule_Week${activeWeek.schedule.week}_${System.currentTimeMillis()}.png"
                                val uri = ScheduleImageExporter.saveToGallery(context, bitmap, filename)
                                if (uri != null) {
                                    Toast.makeText(context, "课表长图已成功保存到系统相册！", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "导出异常: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("export_save_to_gallery"),
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("正在生成…")
                    } else {
                        Text("保存到相册", fontWeight = FontWeight.Bold)
                    }
                }

                // 直接系统分享
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            try {
                                val arrangements = if (includeNonCurrentWeek) {
                                    activeWeek.schedule.allArrangements
                                } else {
                                    activeWeek.schedule.arrangements
                                }
                                val options = ExportOptions(
                                    theme = selectedTheme,
                                    resolution = selectedResolution,
                                    showWeekend = includeWeekend,
                                    firstDayOfWeek = displayOptions.firstDayOfWeek,
                                    showNonCurrentWeekCourses = includeNonCurrentWeek,
                                    showLocation = displayOptions.showLocation,
                                    showTeacher = displayOptions.showTeacher,
                                    showWatermark = showWatermark,
                                )
                                val bitmap = ScheduleImageExporter.renderBitmap(
                                    semesterName = activeWeek.semester.displayName,
                                    schoolName = "福建师范大学",
                                    weekNum = activeWeek.schedule.week,
                                    firstWeekMonday = config.firstWeekMonday,
                                    maxSections = maxSections,
                                    sectionTimes = config.sectionTimes.ifEmpty { ScheduleRules.defaultSectionTimes },
                                    arrangements = arrangements,
                                    today = today,
                                    options = options,
                                )
                                val shareIntent = ScheduleImageExporter.createShareIntent(context, bitmap, activeWeek.schedule.week)
                                val chooser = Intent.createChooser(shareIntent, "分享本周课表长图").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(chooser)
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "调起分享失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("export_share_button"),
                ) {
                    Text("直接分享", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
