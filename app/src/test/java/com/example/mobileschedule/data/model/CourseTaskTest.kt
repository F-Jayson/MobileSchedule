package com.example.mobileschedule.data.model

import com.example.mobileschedule.data.local.entity.CourseTaskEntity
import com.example.mobileschedule.data.local.entity.toEntity
import com.example.mobileschedule.data.local.entity.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTaskTest {

    @Test
    fun domainModelDefaultsAndTypeParsing() {
        val now = System.currentTimeMillis()
        val task = CourseTask(
            id = 10L,
            courseId = 101L,
            title = "实验报告一",
            deadlineEpochMillis = now + 86400000L
        )

        assertEquals(10L, task.id)
        assertEquals(101L, task.courseId)
        assertEquals("实验报告一", task.title)
        assertNull(task.content)
        assertEquals(CourseTaskType.HOMEWORK, task.type)
        assertFalse(task.isCompleted)

        // Type parsing test
        assertEquals(CourseTaskType.EXAM, CourseTaskType.fromString("EXAM"))
        assertEquals(CourseTaskType.EXPERIMENT, CourseTaskType.fromString("experiment"))
        assertEquals(CourseTaskType.CUSTOM, CourseTaskType.fromString("Custom"))
        assertEquals(CourseTaskType.HOMEWORK, CourseTaskType.fromString("unknown_type"))
    }

    @Test
    fun entityToModelAndBackConversion() {
        val created = 1729300000000L
        val deadline = 1729400000000L
        val entity = CourseTaskEntity(
            id = 5L,
            courseId = 42L,
            title = "期末大作业答辩",
            content = "准备 10 分钟 PPT",
            deadlineEpochMillis = deadline,
            type = CourseTaskEntity.TYPE_EXAM,
            isCompleted = true,
            createdAt = created
        )

        val model = entity.toModel()
        assertEquals(5L, model.id)
        assertEquals(42L, model.courseId)
        assertEquals("期末大作业答辩", model.title)
        assertEquals("准备 10 分钟 PPT", model.content)
        assertEquals(deadline, model.deadlineEpochMillis)
        assertEquals(CourseTaskType.EXAM, model.type)
        assertTrue(model.isCompleted)
        assertEquals(created, model.createdAt)

        val convertedBack = model.toEntity()
        assertEquals(entity, convertedBack)
    }
}
