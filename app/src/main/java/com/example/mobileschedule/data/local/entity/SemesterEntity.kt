package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "semesters")
data class SemesterEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val displayName: String)
