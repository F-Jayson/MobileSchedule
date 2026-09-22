package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "course_weeks",
    primaryKeys = ["courseId", "week"],
    foreignKeys = [ForeignKey(
        entity = CourseEntity::class,
        parentColumns = ["id"],
        childColumns = ["courseId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CourseWeekEntity(val courseId: Long, val week: Int)
