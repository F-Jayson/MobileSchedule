package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.MainDispatcherRule
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.repository.ScheduleRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    @Test fun observesActiveWeekAndReceivesSavedCourses() = runTest {
        val semester = Semester(1, "测试学期", emptySet(), SemesterConfig(LocalDate.of(2026, 9, 21), 20, 10, emptyList(), 1))
        val empty = WeekSchedule(1, 1, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27), emptyList())
        val repository = FakeScheduleRepository(semester, empty)
        val viewModel = ScheduleViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(ScheduleUiState.Ready(ActiveWeek(semester, empty)), viewModel.uiState.value)
        assertEquals(1, repository.requestedWeek)

        val course = CourseArrangement(7, 1, "软件工程", null, "A101", 2, 1, 2, setOf(1, 3), CourseOrigin.Manual)
        repository.activeWeek.value = RepoResult.Ok(ActiveWeek(semester, empty.copy(arrangements = listOf(course))))
        runCurrent()
        assertEquals(1, (viewModel.uiState.value as ScheduleUiState.Ready).week.schedule.arrangements.size)
    }

    @Test fun noActiveSemesterIsAnEmptyState() = runTest {
        val repository = FakeScheduleRepository(null, null)
        val viewModel = ScheduleViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(ScheduleUiState.NoSemester, viewModel.uiState.value)
    }

    private class FakeScheduleRepository(initialSemester: Semester?, initialWeek: WeekSchedule?) : ScheduleRepository {
        val semester = MutableStateFlow<RepoResult<Semester?>>(RepoResult.Ok(initialSemester))
        val activeWeek = MutableStateFlow<RepoResult<ActiveWeek?>>(RepoResult.Ok(
            if (initialSemester != null && initialWeek != null) ActiveWeek(initialSemester, initialWeek) else null,
        ))
        var requestedWeek: Int? = null
        override fun observeActiveSemester(): Flow<RepoResult<Semester?>> = semester
        override fun observeActiveWeek(selectedWeek: Int): Flow<RepoResult<ActiveWeek?>> {
            requestedWeek = selectedWeek
            return activeWeek
        }
        override suspend fun weekPosition(semesterId: Long, today: LocalDate): RepoResult<WeekPosition> =
            RepoResult.Ok(WeekPosition.InSemester(1))
        override fun observeSemesters(): Flow<RepoResult<List<Semester>>> = flowOf(RepoResult.Ok(emptyList()))
        override fun observeSemesterConfig(semesterId: Long): Flow<RepoResult<SemesterConfig?>> = flowOf(RepoResult.Ok(null))
        override fun observeWeek(semesterId: Long, week: Int): Flow<RepoResult<WeekSchedule>> = error("unused")
        override fun observeCourseDetail(arrangementId: Long): Flow<RepoResult<CourseDetail?>> = error("unused")
        override fun observeImportStatus(semesterId: Long): Flow<RepoResult<SemesterImportStatus>> = error("unused")
        override suspend fun createSemester(draft: SemesterConfigDraft): RepoResult<Semester> = error("unused")
        override suspend fun saveSemesterConfig(semesterId: Long, expectedRevision: Long, draft: SemesterConfigDraft): RepoResult<SemesterConfig> = error("unused")
        override suspend fun setActiveSemester(semesterId: Long): RepoResult<Unit> = error("unused")
        override suspend fun prepareImport(request: ImportRequest): RepoResult<ImportPreview> = error("unused")
        override suspend fun commitImport(previewId: String, confirmation: ImportConfirmation): RepoResult<ImportReceipt> = error("unused")
        override suspend fun discardImport(previewId: String): RepoResult<Unit> = error("unused")
    }
}
