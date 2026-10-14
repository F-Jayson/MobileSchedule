package com.example.mobileschedule.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.data.model.ArrangementDraft
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.SemesterConfig
import com.example.mobileschedule.ui.theme.CourseColorStore
import com.example.mobileschedule.ui.theme.CourseColorTheme
import com.example.mobileschedule.ui.theme.LocalIsDarkTheme
import kotlin.math.abs

private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

sealed interface CourseFormState {
    data object Closed : CourseFormState
    data class Create(
        val semesterId: Long,
        val config: SemesterConfig,
        val defaultDay: Int = 1,
        val defaultWeek: Int = 1,
    ) : CourseFormState
    data class Edit(
        val arrangement: CourseArrangement,
        val config: SemesterConfig,
    ) : CourseFormState
    data class Copy(
        val arrangement: CourseArrangement,
        val config: SemesterConfig,
    ) : CourseFormState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseFormSheet(
    formState: CourseFormState,
    onDismiss: () -> Unit,
    onSave: (ArrangementDraft, Int?) -> Unit,
    onCheckConflict: suspend (ArrangementDraft, Long?) -> List<CourseArrangement>,
) {
    if (formState == CourseFormState.Closed) return

    val config = when (formState) {
        is CourseFormState.Create -> formState.config
        is CourseFormState.Edit -> formState.config
        is CourseFormState.Copy -> formState.config
        CourseFormState.Closed -> return
    }

    val initialArrangement = when (formState) {
        is CourseFormState.Create -> null
        is CourseFormState.Edit -> formState.arrangement
        is CourseFormState.Copy -> formState.arrangement
        CourseFormState.Closed -> null
    }

    val isEdit = formState is CourseFormState.Edit
    val isCopy = formState is CourseFormState.Copy
    val excludingId = if (isEdit) initialArrangement?.id else null

    var name by remember(formState) {
        mutableStateOf(initialArrangement?.name.orEmpty())
    }
    var location by remember(formState) {
        mutableStateOf(initialArrangement?.location.orEmpty())
    }
    var teacher by remember(formState) {
        mutableStateOf(initialArrangement?.teacher.orEmpty())
    }
    var dayOfWeek by remember(formState) {
        mutableIntStateOf(
            initialArrangement?.dayOfWeek ?: when (formState) {
                is CourseFormState.Create -> formState.defaultDay
                else -> 1
            }
        )
    }
    var startSection by remember(formState) {
        mutableIntStateOf(initialArrangement?.startSection?.coerceIn(1, config.totalSections) ?: 1)
    }
    var endSection by remember(formState) {
        mutableIntStateOf(
            initialArrangement?.endSection?.coerceIn(startSection, config.totalSections) ?: minOf(startSection + 1, config.totalSections)
        )
    }
    var selectedWeeks by remember(formState) {
        val initialWeeks = initialArrangement?.weeks ?: when (formState) {
            is CourseFormState.Create -> setOf(formState.defaultWeek.coerceIn(1, config.totalWeeks))
            else -> (1..config.totalWeeks).toSet()
        }
        mutableStateOf(initialWeeks.filter { it in 1..config.totalWeeks }.toSet())
    }

    val initialColorIndex = remember(formState) {
        initialArrangement?.name?.let { CourseColorStore.getColorIndex(it) ?: (abs(it.hashCode()) % 8) } ?: 0
    }
    var selectedColorIndex by remember(formState) { mutableIntStateOf(initialColorIndex) }

    var showErrors by remember { mutableStateOf(false) }
    var conflicts by remember { mutableStateOf<List<CourseArrangement>>(emptyList()) }

    // Check conflicts whenever schedule position or weeks change
    LaunchedEffect(dayOfWeek, startSection, endSection, selectedWeeks) {
        if (selectedWeeks.isNotEmpty() && startSection <= endSection) {
            val draft = ArrangementDraft(
                name = name,
                teacher = teacher.takeIf(String::isNotBlank),
                location = location.takeIf(String::isNotBlank),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                endSection = endSection,
                weeks = selectedWeeks,
            )
            conflicts = onCheckConflict(draft, excludingId)
        } else {
            conflicts = emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("course_form_sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        when {
                            isEdit -> "编辑课程"
                            isCopy -> "复制为手动课程"
                            else -> "添加课程"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("course_form_title"),
                    )
                    Text(
                        "填写课程信息、上课时间与周次安排",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("course_form_cancel")) {
                    Text("取消")
                }
            }

            // Scrollable Content
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Course Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称 *") },
                    placeholder = { Text("例如：高等数学、大学物理") },
                    isError = showErrors && name.isBlank(),
                    supportingText = if (showErrors && name.isBlank()) {
                        { Text("课程名称不能为空", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_form_name"),
                )

                // Location & Teacher
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("教室 / 地点") },
                        placeholder = { Text("如：旗山A101") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("course_form_location"),
                    )
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("授课教师") },
                        placeholder = { Text("如：张老师") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("course_form_teacher"),
                    )
                }

                // Day of Week Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "上课星期",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag("course_form_day_picker"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        weekdays.forEachIndexed { index, dayName ->
                            val currentDay = index + 1
                            val isSelected = dayOfWeek == currentDay
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable { dayOfWeek = currentDay }
                                    .testTag("course_form_day_$currentDay"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        dayName.replace("周", ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Range (Start section & End section)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "节次区间",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionSelector(
                            label = "起始节次",
                            selected = startSection,
                            range = 1..config.totalSections,
                            onSelect = {
                                startSection = it
                                if (endSection < it) endSection = it
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("course_form_start_section"),
                        )
                        Text("至", style = MaterialTheme.typography.bodyMedium)
                        SectionSelector(
                            label = "结束节次",
                            selected = endSection,
                            range = startSection..config.totalSections,
                            onSelect = { endSection = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("course_form_end_section"),
                        )
                    }
                }

                // Week Multi-Selector
                Column(
                    modifier = Modifier.testTag("course_form_weeks_selector"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "上课周次（已选 ${selectedWeeks.size} 周）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Quick Select Buttons
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuickWeekButton("全部周", "course_form_weeks_all") {
                            selectedWeeks = (1..config.totalWeeks).toSet()
                        }
                        QuickWeekButton("单周", "course_form_weeks_odd") {
                            selectedWeeks = (1..config.totalWeeks).filter { it % 2 == 1 }.toSet()
                        }
                        QuickWeekButton("双周", "course_form_weeks_even") {
                            selectedWeeks = (1..config.totalWeeks).filter { it % 2 == 0 }.toSet()
                        }
                        QuickWeekButton("清空", "course_form_weeks_clear") {
                            selectedWeeks = emptySet()
                        }
                    }

                    if (showErrors && selectedWeeks.isEmpty()) {
                        Text(
                            "请至少选择一个上课周次",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("course_form_weeks_error"),
                        )
                    }

                    // Flow of week chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        (1..config.totalWeeks).forEach { weekNum ->
                            val isChecked = weekNum in selectedWeeks
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                border = if (isChecked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                else null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        selectedWeeks = if (isChecked) selectedWeeks - weekNum
                                        else selectedWeeks + weekNum
                                    }
                                    .testTag("course_form_week_$weekNum"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "$weekNum",
                                        fontSize = 11.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Course Color Palette Picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "卡片配色主题",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val isDark = LocalIsDarkTheme.current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CourseColorTheme.PaletteNames.forEachIndexed { index, paletteName ->
                            val palette = CourseColorTheme.palette(index, isDark)
                            val isChosen = selectedColorIndex == index
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { selectedColorIndex = index }
                                    .testTag("course_form_color_$index"),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(palette.container)
                                        .border(
                                            width = if (isChosen) 2.5.dp else 1.dp,
                                            color = if (isChosen) palette.accent else palette.accent.copy(alpha = 0.4f),
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isChosen) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(palette.accent)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    paletteName,
                                    fontSize = 10.sp,
                                    color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                // Conflict Warning Card
                if (conflicts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_form_conflict_warning"),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "⚠️ 时段重叠提醒",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            val conflictDescriptions = conflicts.map {
                                "• ${it.name}（第${it.startSection}–${it.endSection}节）"
                            }.distinct().joinToString("\n")
                            Text(
                                "所选时间与已有课程存在重叠：\n$conflictDescriptions\n保存后课程卡片将在同格内分栏显示。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Save Action Button
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        if (name.isBlank() || selectedWeeks.isEmpty()) {
                            showErrors = true
                            return@Button
                        }
                        val draft = ArrangementDraft(
                            name = name.trim(),
                            teacher = teacher.trim().takeIf(String::isNotEmpty),
                            location = location.trim().takeIf(String::isNotEmpty),
                            dayOfWeek = dayOfWeek,
                            startSection = startSection,
                            endSection = endSection,
                            weeks = selectedWeeks,
                        )
                        onSave(draft, selectedColorIndex)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("course_form_save"),
                ) {
                    Text(
                        when {
                            isEdit -> "保存修改"
                            isCopy -> "保存为手动课程"
                            else -> "保存课程"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickWeekButton(label: String, tag: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.testTag(tag),
    ) {
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun SectionSelector(
    label: String,
    selected: Int,
    range: IntRange,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("第 $selected 节", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            range.forEach { sec ->
                DropdownMenuItem(
                    text = { Text("第 $sec 节", fontWeight = if (sec == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onSelect(sec)
                        expanded = false
                    },
                )
            }
        }
    }
}
