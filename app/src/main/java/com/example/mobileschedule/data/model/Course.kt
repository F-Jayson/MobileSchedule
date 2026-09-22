package com.example.mobileschedule.data.model

/** One recurring arrangement. Import identity and semester rules will extend this foundation. */
data class Course(
    val id: Long,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: Set<Int>,
)
