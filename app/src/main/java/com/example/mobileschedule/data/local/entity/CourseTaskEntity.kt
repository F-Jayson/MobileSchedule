package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mobileschedule.data.model.CourseTask
import com.example.mobileschedule.data.model.CourseTaskType

@Entity(
    tableName = "course_tasks",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("courseId"),
        Index("deadlineEpochMillis")
    ]
)
data class CourseTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val courseId: Long,
    val title: String,
    val content: String? = null,
    val deadlineEpochMillis: Long,
    val type: String = TYPE_HOMEWORK,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_HOMEWORK = "HOMEWORK"
        const val TYPE_EXAM = "EXAM"
        const val TYPE_EXPERIMENT = "EXPERIMENT"
        const val TYPE_CUSTOM = "CUSTOM"
    }
}

fun CourseTaskEntity.toModel(): CourseTask = CourseTask(
    id = id,
    courseId = courseId,
    title = title,
    content = content,
    deadlineEpochMillis = deadlineEpochMillis,
    type = CourseTaskType.fromString(type),
    isCompleted = isCompleted,
    createdAt = createdAt
)

fun CourseTask.toEntity(): CourseTaskEntity = CourseTaskEntity(
    id = id,
    courseId = courseId,
    title = title,
    content = content,
    deadlineEpochMillis = deadlineEpochMillis,
    type = type.name,
    isCompleted = isCompleted,
    createdAt = createdAt
)
