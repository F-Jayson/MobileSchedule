package com.example.mobileschedule.ui.today.countdown

import java.time.LocalDate

enum class CountdownCategory(val label: String, val icon: String) {
    EXAM("考试", "🎯"),
    HOLIDAY("假期", "🏖️"),
    CUSTOM("目标", "⭐"),
}

data class CountdownTarget(
    val id: String,
    val title: String,
    val shortTitle: String,
    val targetDate: LocalDate,
    val category: CountdownCategory,
    val motto: String,
    val daysRemaining: Long,
    val isCustom: Boolean = false,
    val isPinned: Boolean = false,
) {
    val displayDaysText: String get() = when {
        daysRemaining == 0L -> "今天"
        daysRemaining == 1L -> "明天"
        daysRemaining > 0L -> "${daysRemaining}天"
        else -> "已过"
    }

    val isPast: Boolean get() = daysRemaining < 0L
}

data class CustomCountdown(
    val id: String,
    val title: String,
    val targetDate: LocalDate,
    val note: String? = null,
)
