package com.example.mobileschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mobileschedule.data.local.entity.CourseTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseTaskDao {
    @Query("SELECT * FROM course_tasks WHERE courseId = :courseId ORDER BY isCompleted ASC, deadlineEpochMillis ASC, id ASC")
    fun observeTasksForCourse(courseId: Long): Flow<List<CourseTaskEntity>>

    @Query("SELECT * FROM course_tasks WHERE courseId = :courseId ORDER BY isCompleted ASC, deadlineEpochMillis ASC, id ASC")
    suspend fun getTasksForCourse(courseId: Long): List<CourseTaskEntity>

    @Query("""
        SELECT t.* FROM course_tasks AS t
        INNER JOIN courses AS c ON t.courseId = c.id
        WHERE c.semesterId = :semesterId AND t.isCompleted = 0
        ORDER BY t.deadlineEpochMillis ASC, t.id ASC
    """)
    fun observeUnfinishedTasksForSemester(semesterId: Long): Flow<List<CourseTaskEntity>>

    @Query("""
        SELECT t.* FROM course_tasks AS t
        INNER JOIN courses AS c ON t.courseId = c.id
        WHERE c.semesterId = :semesterId
        ORDER BY t.isCompleted ASC, t.deadlineEpochMillis ASC, t.id ASC
    """)
    fun observeAllTasksForSemester(semesterId: Long): Flow<List<CourseTaskEntity>>

    @Query("SELECT * FROM course_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): CourseTaskEntity?

    @Query("SELECT * FROM course_tasks WHERE id = :taskId LIMIT 1")
    fun observeTaskById(taskId: Long): Flow<CourseTaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: CourseTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<CourseTaskEntity>): List<Long>

    @Update
    suspend fun updateTask(task: CourseTaskEntity): Int

    @Delete
    suspend fun deleteTask(task: CourseTaskEntity): Int

    @Query("DELETE FROM course_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long): Int

    @Query("UPDATE course_tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun updateTaskCompletion(taskId: Long, isCompleted: Boolean): Int

    @Query("DELETE FROM course_tasks WHERE courseId = :courseId")
    suspend fun deleteTasksForCourse(courseId: Long): Int

    @Query("SELECT COUNT(*) FROM course_tasks")
    suspend fun countAllTasks(): Int

    @Query("SELECT COUNT(*) FROM course_tasks WHERE courseId = :courseId AND isCompleted = 0")
    fun observeUnfinishedCountForCourse(courseId: Long): Flow<Int>
}
