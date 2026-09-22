package com.example.mobileschedule.data.repository

import com.example.mobileschedule.data.local.dao.CourseDao
import com.example.mobileschedule.data.local.entity.toModel
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class OfflineCourseRepository @Inject constructor(private val courseDao: CourseDao) : CourseRepository {
    override fun observeCourses() = courseDao.observeCourses().map { rows -> rows.map { it.toModel() } }
}
