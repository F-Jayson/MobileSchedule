package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.MainDispatcherRule
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.repository.ScheduleRepository
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val monday = LocalDate.of(2026, 9, 21)

    @Test fun browsingWeekIsIndependentOfRealWeekAndCanReturn() = runTest {
        val repository = FakeScheduleRepository(semester())
        val viewModel = ScheduleViewModel(repository)
        viewModel.refreshToday(monday.plusDays(9)) // Wednesday of week 2
        collect(viewModel)
        runCurrent()
        assertEquals(2, ready(viewModel).week.schedule.week)
        assertEquals(WeekPosition.InSemester(2), ready(viewModel).currentPosition)

        viewModel.selectWeek(4)
        runCurrent()
        assertEquals(4, ready(viewModel).week.schedule.week)
        assertEquals(WeekPosition.InSemester(2), ready(viewModel).currentPosition)
        viewModel.nextWeek()
        runCurrent()
        assertEquals(5, ready(viewModel).week.schedule.week)
        viewModel.previousWeek()
        runCurrent()
        assertEquals(4, ready(viewModel).week.schedule.week)
        viewModel.returnToCurrentWeek()
        runCurrent()
        assertEquals(2, ready(viewModel).week.schedule.week)
    }

    @Test fun semesterEdgesDoNotCreateAnImaginaryCurrentWeek() = runTest {
        val repository = FakeScheduleRepository(semester())
        val viewModel = ScheduleViewModel(repository)
        viewModel.refreshToday(monday.minusDays(1))
        collect(viewModel)
        runCurrent()
        assertEquals(WeekPosition.BeforeSemester, ready(viewModel).currentPosition)
        assertEquals(1, ready(viewModel).week.schedule.week)
        viewModel.previousWeek()
        runCurrent()
        assertEquals(1, ready(viewModel).week.schedule.week)
        viewModel.selectWeek(20)
        runCurrent()
        assertEquals(20, ready(viewModel).week.schedule.week)
        viewModel.returnToCurrentWeek()
        runCurrent()
        assertEquals(1, ready(viewModel).week.schedule.week)

        viewModel.refreshToday(monday.plusWeeks(20))
        runCurrent()
        assertEquals(WeekPosition.AfterSemester, ready(viewModel).currentPosition)
        assertEquals(20, ready(viewModel).week.schedule.week)
        viewModel.nextWeek()
        runCurrent()
        assertEquals(20, ready(viewModel).week.schedule.week)
    }

    @Test fun explicitBrowsingSurvivesDateChangeAndClampsAfterConfigEdit() = runTest {
        val repository = FakeScheduleRepository(semester())
        val viewModel = ScheduleViewModel(repository)
        viewModel.refreshToday(monday.plusDays(8))
        collect(viewModel)
        runCurrent()
        viewModel.selectWeek(4)
        runCurrent()
        viewModel.refreshToday(monday.plusDays(15))
        runCurrent()
        assertEquals(WeekPosition.InSemester(3), ready(viewModel).currentPosition)
        assertEquals(4, ready(viewModel).week.schedule.week)

        repository.activeSemester.value = RepoResult.Ok(semester().copy(config = semester().config!!.copy(totalWeeks = 3)))
        runCurrent()
        assertEquals(3, ready(viewModel).week.schedule.week)
        repository.activeSemester.value = RepoResult.Ok(semester(2))
        runCurrent()
        assertEquals(2L, ready(viewModel).week.semester.id)
        assertEquals(3, ready(viewModel).week.schedule.week)
    }

    @Test fun activeWeekUpdatesAfterSaveAndNoSemesterIsSeparateFromError() = runTest {
        val repository = FakeScheduleRepository(semester())
        val viewModel = ScheduleViewModel(repository)
        viewModel.refreshToday(monday)
        collect(viewModel)
        runCurrent()
        assertTrue(ready(viewModel).week.schedule.arrangements.isEmpty())
        repository.arrangements.value = listOf(course(7))
        runCurrent()
        assertEquals(7L, ready(viewModel).week.schedule.arrangements.single().id)
        repository.activeSemester.value = RepoResult.Ok(null)
        runCurrent()
        assertEquals(ScheduleUiState.NoSemester, viewModel.uiState.value)
        repository.activeSemester.value = RepoResult.Err(DataError(DataErrorCode.STORAGE_READ_FAILED))
        runCurrent()
        assertEquals(ScheduleUiState.Error, viewModel.uiState.value)
    }

    @Test fun detailTracksSourceLabelThenBecomesMissingAfterReplacement() = runTest {
        val source = SourceScope("fjnu", "zhengfang", "2026-2027-1")
        val arrangement = course(7).copy(origin = CourseOrigin.SchoolImport(source, "batch-1", "entry-1"))
        val repository = FakeScheduleRepository(semester())
        repository.detail.value = RepoResult.Ok(CourseDetail(arrangement, "本地秋季学期", LocalTime.of(8, 0), LocalTime.of(9, 35)))
        repository.importStatus.value = RepoResult.Ok(SemesterImportStatus(1, listOf(
            ImportSourceStatus(source, "2026—2027学年第一学期", null, 1),
        )))
        val viewModel = ScheduleViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.detailState.collect {} }
        viewModel.showCourseDetail(7)
        runCurrent()
        val ready = viewModel.detailState.value as CourseDetailUiState.Ready
        assertEquals("2026—2027学年第一学期", ready.sourceTermLabel)
        assertEquals(7L, ready.detail.arrangement.id)
        repository.detail.value = RepoResult.Ok(null)
        runCurrent()
        assertEquals(CourseDetailUiState.Missing, viewModel.detailState.value)
        repository.detail.value = RepoResult.Err(DataError(DataErrorCode.STORAGE_READ_FAILED))
        runCurrent()
        assertEquals(CourseDetailUiState.Error, viewModel.detailState.value)
        viewModel.closeCourseDetail()
        runCurrent()
        assertEquals(CourseDetailUiState.Closed, viewModel.detailState.value)
    }

    @Test fun retryResubscribesAfterReadErrorWithoutTreatingItAsEmpty() = runTest {
        val repository = FakeScheduleRepository(semester())
        repository.failFirstSubscription = true
        val viewModel = ScheduleViewModel(repository)
        viewModel.refreshToday(monday)
        collect(viewModel)
        runCurrent()
        assertEquals(ScheduleUiState.Error, viewModel.uiState.value)
        viewModel.retry()
        runCurrent()
        assertEquals(1, ready(viewModel).week.schedule.week)
        assertEquals(2, repository.subscriptionCount)
    }

    private fun TestScope.collect(viewModel: ScheduleViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun ready(viewModel: ScheduleViewModel) = viewModel.uiState.value as ScheduleUiState.Ready
    private fun semester(id: Long = 1) = Semester(id, "本地秋季学期", emptySet(),
        SemesterConfig(monday, 20, 10, emptyList(), 1))
    private fun course(id: Long) = CourseArrangement(id, 1, "软件工程", "教师", "A101", 2, 1, 2,
        setOf(1, 2, 3), CourseOrigin.Manual)

    private class FakeScheduleRepository(initialSemester: Semester) : ScheduleRepository {
        val activeSemester = MutableStateFlow<RepoResult<Semester?>>(RepoResult.Ok(initialSemester))
        val arrangements = MutableStateFlow<List<CourseArrangement>>(emptyList())
        val detail = MutableStateFlow<RepoResult<CourseDetail?>>(RepoResult.Ok(null))
        val importStatus = MutableStateFlow<RepoResult<SemesterImportStatus>>(RepoResult.Ok(SemesterImportStatus(1, emptyList())))
        var failFirstSubscription = false
        var subscriptionCount = 0
        override fun observeActiveSemester(): Flow<RepoResult<Semester?>> = flow {
            subscriptionCount++
            if (failFirstSubscription && subscriptionCount == 1) emit(RepoResult.Err(DataError(DataErrorCode.STORAGE_READ_FAILED)))
            else emitAll(activeSemester)
        }
        override fun observeActiveWeek(selectedWeek: Int): Flow<RepoResult<ActiveWeek?>> =
            combine(activeSemester, arrangements) { result, courses ->
                val semester = (result as? RepoResult.Ok)?.value
                if (semester == null) RepoResult.Ok(null)
                else RepoResult.Ok(ActiveWeek(semester, ScheduleRules.weekSchedule(
                    semester.id, requireNotNull(semester.config), selectedWeek, courses)))
            }
        override suspend fun weekPosition(semesterId: Long, today: LocalDate): RepoResult<WeekPosition> {
            val semester = (activeSemester.value as? RepoResult.Ok)?.value ?: return RepoResult.Err(DataError(DataErrorCode.SEMESTER_NOT_FOUND))
            return RepoResult.Ok(ScheduleRules.weekPosition(requireNotNull(semester.config), today))
        }
        override fun observeCourseDetail(arrangementId: Long): Flow<RepoResult<CourseDetail?>> = detail
        override fun observeImportStatus(semesterId: Long): Flow<RepoResult<SemesterImportStatus>> = importStatus
        override fun observeSemesters(): Flow<RepoResult<List<Semester>>> = flowOf(RepoResult.Ok(emptyList()))
        override fun observeSemesterConfig(semesterId: Long): Flow<RepoResult<SemesterConfig?>> = flowOf(RepoResult.Ok(null))
        override fun observeWeek(semesterId: Long, week: Int): Flow<RepoResult<WeekSchedule>> = error("unused")
        override suspend fun createSemester(draft: SemesterConfigDraft): RepoResult<Semester> = error("unused")
        override suspend fun saveSemesterConfig(semesterId: Long, expectedRevision: Long, draft: SemesterConfigDraft): RepoResult<SemesterConfig> = error("unused")
        override suspend fun setActiveSemester(semesterId: Long): RepoResult<Unit> = error("unused")
        override suspend fun prepareImport(request: ImportRequest): RepoResult<ImportPreview> = error("unused")
        override suspend fun commitImport(previewId: String, confirmation: ImportConfirmation): RepoResult<ImportReceipt> = error("unused")
        override suspend fun discardImport(previewId: String): RepoResult<Unit> = error("unused")
    }
}
