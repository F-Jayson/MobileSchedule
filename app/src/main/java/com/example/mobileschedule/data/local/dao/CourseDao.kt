package com.example.mobileschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.example.mobileschedule.data.local.entity.CourseWithWeeks
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Transaction
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startSection, id")
    fun observeCourses(): Flow<List<CourseWithWeeks>>
}
