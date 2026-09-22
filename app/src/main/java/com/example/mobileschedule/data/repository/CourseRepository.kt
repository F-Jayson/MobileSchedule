package com.example.mobileschedule.data.repository

import com.example.mobileschedule.data.model.Course
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun observeCourses(): Flow<List<Course>>
}
