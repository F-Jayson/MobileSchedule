package com.example.mobileschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.mobileschedule.data.local.entity.CourseWithWeeks
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Transaction
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startSection, id")
    fun observeCourses(): Flow<List<CourseWithWeeks>>

    @Insert suspend fun insertCourses(courses: List<CourseEntity>): List<Long>
    @Insert suspend fun insertWeeks(weeks: List<CourseWeekEntity>)

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :arrangementId")
    suspend fun getCourse(arrangementId: Long): CourseWithWeeks?

    @Transaction
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startSection, endSection, id")
    fun observeSemesterCourses(semesterId: Long): Flow<List<CourseWithWeeks>>
}
