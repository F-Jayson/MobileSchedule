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

    /** Counts each affected arrangement once, including a row with both week and section violations. */
    @Query("""
        SELECT COUNT(*) FROM courses AS c WHERE c.semesterId = :semesterId AND (
            c.dayOfWeek NOT BETWEEN 1 AND 7 OR c.startSection < 1 OR
            c.endSection < c.startSection OR c.endSection > :totalSections OR
            NOT EXISTS (SELECT 1 FROM course_weeks AS w WHERE w.courseId = c.id) OR
            EXISTS (SELECT 1 FROM course_weeks AS w WHERE w.courseId = c.id AND (w.week < 1 OR w.week > :totalWeeks))
        )
    """)
    suspend fun countOutsideConfig(semesterId: Long, totalWeeks: Int, totalSections: Int): Int
}
