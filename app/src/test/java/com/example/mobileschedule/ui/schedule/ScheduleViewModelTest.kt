package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.MainDispatcherRule
import com.example.mobileschedule.data.model.Course
import com.example.mobileschedule.data.repository.CourseRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun emptyDatabaseThenNewDataUpdatesObservedState() = runTest {
        val courses = MutableStateFlow<List<Course>>(emptyList())
        val viewModel = ScheduleViewModel(repository(courses))
        assertEquals(ScheduleUiState.Loading, viewModel.uiState.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(ScheduleUiState.Ready(emptyList()), viewModel.uiState.value)

        val course = Course(7, "软件工程", "老师", "A101", 2, 1, 2, setOf(1, 3, 5))
        courses.value = listOf(course)
        runCurrent()
        assertEquals(ScheduleUiState.Ready(listOf(course)), viewModel.uiState.value)
    }

    @Test
    fun repositoryFailureBecomesVisibleErrorInsteadOfEmptyTimetable() = runTest {
        val viewModel = ScheduleViewModel(repository(flow { throw IOException("Read failed") }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(ScheduleUiState.Error, viewModel.uiState.value)
    }

    private fun repository(courses: Flow<List<Course>>) = object : CourseRepository {
        override fun observeCourses() = courses
    }
}
