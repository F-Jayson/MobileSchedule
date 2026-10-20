package com.example.mobileschedule.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobileschedule.data.local.dao.CourseDao
import com.example.mobileschedule.data.local.dao.CourseTaskDao
import com.example.mobileschedule.data.local.dao.SemesterDao
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseTaskEntity
import com.example.mobileschedule.data.local.entity.SemesterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseTaskDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var taskDao: CourseTaskDao
    private lateinit var courseDao: CourseDao
    private lateinit var semesterDao: SemesterDao

    private var semesterId: Long = 0L
    private var courseId1: Long = 0L
    private var courseId2: Long = 0L

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.courseTaskDao()
        courseDao = database.courseDao()
        semesterDao = database.semesterDao()

        // Prepare base semester and courses
        semesterId = semesterDao.insertSemester(SemesterEntity(displayName = "2026年秋季学期"))
        courseId1 = courseDao.insertCourse(
            CourseEntity(
                name = "操作系统",
                teacher = "张教授",
                location = "信息楼A301",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                semesterId = semesterId,
                originType = CourseOriginType.MANUAL
            )
        )
        courseId2 = courseDao.insertCourse(
            CourseEntity(
                name = "计算机网络",
                teacher = "陈老师",
                location = "网络楼B202",
                dayOfWeek = 2,
                startSection = 3,
                endSection = 4,
                semesterId = semesterId,
                originType = CourseOriginType.MANUAL
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryTasksForCourse() = runBlocking {
        val now = 1729300000000L
        val task1 = CourseTaskEntity(
            courseId = courseId1,
            title = "实验一：进程调度",
            content = "提交设计文档与C++代码",
            deadlineEpochMillis = now + 100000L,
            type = CourseTaskEntity.TYPE_EXPERIMENT,
            isCompleted = false
        )
        val task2 = CourseTaskEntity(
            courseId = courseId1,
            title = "期中小测",
            content = "前四章复习",
            deadlineEpochMillis = now + 50000L,
            type = CourseTaskEntity.TYPE_EXAM,
            isCompleted = false
        )

        val id1 = taskDao.insertTask(task1)
        val id2 = taskDao.insertTask(task2)
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        // Query by ID
        val queried1 = taskDao.getTaskById(id1)
        assertNotNull(queried1)
        assertEquals("实验一：进程调度", queried1?.title)
        assertEquals(CourseTaskEntity.TYPE_EXPERIMENT, queried1?.type)

        // Observe by course: should sort by isCompleted ASC, deadline ASC
        val courseTasks = taskDao.observeTasksForCourse(courseId1).first()
        assertEquals(2, courseTasks.size)
        // task2 has earlier deadline (now + 50000) than task1 (now + 100000)
        assertEquals(id2, courseTasks[0].id)
        assertEquals(id1, courseTasks[1].id)

        // Unfinished count
        val unfinishedCount = taskDao.observeUnfinishedCountForCourse(courseId1).first()
        assertEquals(2, unfinishedCount)
    }

    @Test
    fun updateTaskAndCompletionStatus() = runBlocking {
        val taskId = taskDao.insertTask(
            CourseTaskEntity(
                courseId = courseId1,
                title = "课后习题",
                deadlineEpochMillis = 1729400000000L,
                type = CourseTaskEntity.TYPE_HOMEWORK,
                isCompleted = false
            )
        )

        // Mark as completed
        val updatedRows = taskDao.updateTaskCompletion(taskId, true)
        assertEquals(1, updatedRows)

        val afterCompleted = taskDao.getTaskById(taskId)
        assertNotNull(afterCompleted)
        assertTrue(afterCompleted!!.isCompleted)

        // Update title and content
        taskDao.updateTask(afterCompleted.copy(title = "课后习题修订版", content = "增加了第三题"))
        val afterUpdate = taskDao.getTaskById(taskId)
        assertEquals("课后习题修订版", afterUpdate?.title)
        assertEquals("增加了第三题", afterUpdate?.content)
    }

    @Test
    fun observeTasksForSemesterFiltersAndOrdersProperly() = runBlocking {
        val now = 1729300000000L
        val t1 = taskDao.insertTask(CourseTaskEntity(courseId = courseId1, title = "OS 作业", deadlineEpochMillis = now + 2000L, isCompleted = false))
        val t2 = taskDao.insertTask(CourseTaskEntity(courseId = courseId2, title = "计网 实验", deadlineEpochMillis = now + 1000L, isCompleted = false))
        val t3 = taskDao.insertTask(CourseTaskEntity(courseId = courseId1, title = "已完成任务", deadlineEpochMillis = now + 500L, isCompleted = true))

        // observeUnfinishedTasksForSemester should contain only t2 and t1, ordered by deadline (t2 before t1)
        val unfinished = taskDao.observeUnfinishedTasksForSemester(semesterId).first()
        assertEquals(2, unfinished.size)
        assertEquals(t2, unfinished[0].id)
        assertEquals(t1, unfinished[1].id)

        // observeAllTasksForSemester contains t2, t1 (unfinished), and then t3 (completed)
        val all = taskDao.observeAllTasksForSemester(semesterId).first()
        assertEquals(3, all.size)
        assertFalse(all[0].isCompleted)
        assertFalse(all[1].isCompleted)
        assertTrue(all[2].isCompleted)
        assertEquals(t3, all[2].id)
    }

    @Test
    fun deleteTaskAndCascadeDeleteWhenCourseIsDeleted() = runBlocking {
        val taskId1 = taskDao.insertTask(CourseTaskEntity(courseId = courseId1, title = "任务1", deadlineEpochMillis = 1729300000000L))
        val taskId2 = taskDao.insertTask(CourseTaskEntity(courseId = courseId1, title = "任务2", deadlineEpochMillis = 1729400000000L))

        assertEquals(2, taskDao.countAllTasks())

        // Delete single task
        taskDao.deleteTaskById(taskId1)
        assertEquals(1, taskDao.countAllTasks())
        assertNull(taskDao.getTaskById(taskId1))

        // Delete courseId1 -> course_tasks with courseId1 must be cascaded
        courseDao.deleteCourse(courseId1)
        assertEquals(0, taskDao.countAllTasks())
        assertNull(taskDao.getTaskById(taskId2))
    }
}
