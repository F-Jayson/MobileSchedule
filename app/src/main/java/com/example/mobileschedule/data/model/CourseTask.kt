package com.example.mobileschedule.data.model

/**
 * Domain model representing a learning task, exam, or deadline associated with a course.
 */
data class CourseTask(
    val id: Long = 0L,
    val courseId: Long,
    val title: String,
    val content: String? = null,
    val deadlineEpochMillis: Long,
    val type: CourseTaskType = CourseTaskType.HOMEWORK,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Supported task types for courses.
 */
enum class CourseTaskType {
    HOMEWORK,
    EXAM,
    EXPERIMENT,
    CUSTOM;

    companion object {
        fun fromString(value: String): CourseTaskType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HOMEWORK
    }
}
