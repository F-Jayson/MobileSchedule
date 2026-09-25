package com.example.mobileschedule.ui.settings

import com.example.mobileschedule.MainDispatcherRule
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.repository.ScheduleRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsEditorViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val monday = LocalDate.of(2026, 9, 21)

    @Test fun newSemesterIsCreatedAndActivatedOnlyAfterValidSave() = runTest {
        val repository = FakeRepository()
        val vm = SettingsEditorViewModel(repository)
        vm.open(null)
        vm.save()
        runCurrent()
        assertEquals(0, repository.createCalls)
        assertTrue((vm.state.value as ConfigEditorState.Editing).showErrors)

        vm.updateInput { SemesterFormInput("合成秋季学期", monday, "12", "4") }
        vm.save()
        runCurrent()
        assertEquals(1, repository.createCalls)
        assertEquals(1, repository.activateCalls)
        assertEquals(ConfigEditorState.Saved, vm.state.value)
        assertEquals("合成秋季学期", repository.active.value!!.displayName)
    }

    @Test fun editingUsesRevisionAndConflictKeepsInputWithoutOverwriting() = runTest {
        val repository = FakeRepository(initial = semester())
        val vm = SettingsEditorViewModel(repository)
        vm.open(7)
        runCurrent()
        val initial = vm.state.value as ConfigEditorState.Editing
        assertEquals("已有学期", initial.input.displayName)
        assertEquals(3L, initial.revision)
        vm.updateInput { it.copy(totalWeeks = "8") }
        vm.save()
        runCurrent()
        assertTrue((vm.state.value as ConfigEditorState.Editing).impactPending)
        assertEquals(0, repository.saveCalls)
        repository.saveResult = RepoResult.Err(DataError(DataErrorCode.CONFIG_CONFLICT, affectedCount = 2))
        vm.confirmImpactAndSave()
        runCurrent()
        val conflict = vm.state.value as ConfigEditorState.Editing
        assertEquals("8", conflict.input.totalWeeks)
        assertTrue(conflict.saveError!!.contains("2 条课程安排"))
        assertEquals(3L, repository.lastExpectedRevision)
        assertEquals(12, repository.active.value!!.config!!.totalWeeks)
    }

    @Test fun changingOnlyNameSavesWithoutImpactDialogAndFlowCanRefreshReaders() = runTest {
        val repository = FakeRepository(initial = semester())
        val vm = SettingsEditorViewModel(repository)
        vm.open(7)
        runCurrent()
        vm.updateInput { it.copy(displayName = "新名称") }
        vm.save()
        runCurrent()
        assertEquals(1, repository.saveCalls)
        assertEquals(ConfigEditorState.Saved, vm.state.value)
        assertEquals("新名称", repository.active.value!!.displayName)
        assertEquals(4L, repository.active.value!!.config!!.revision)
    }

    @Test fun activationRetryDoesNotCreateDuplicateSemester() = runTest {
        val repository = FakeRepository()
        repository.activationFailures = 1
        val vm = SettingsEditorViewModel(repository)
        vm.open(null)
        vm.updateInput { SemesterFormInput("合成秋季学期", monday, "12", "4") }
        vm.save()
        runCurrent()
        val failed = vm.state.value as ConfigEditorState.Editing
        assertEquals(11L, failed.semesterId)
        assertTrue(failed.saveError!!.contains("已创建"))
        assertEquals(1, repository.createCalls)
        vm.save()
        runCurrent()
        assertEquals(ConfigEditorState.Saved, vm.state.value)
        assertEquals(1, repository.createCalls)
        assertEquals(2, repository.activateCalls)
    }

    private fun semester() = Semester(7, "已有学期", emptySet(),
        SemesterConfig(monday, 12, 4, emptyList(), 3))

    private class FakeRepository(initial: Semester? = null) : ScheduleRepository {
        val active = MutableStateFlow(initial)
        val semesters = MutableStateFlow(initial?.let { listOf(it) } ?: emptyList())
        var createCalls = 0
        var saveCalls = 0
        var activateCalls = 0
        var lastExpectedRevision = -1L
        var saveResult: RepoResult<SemesterConfig>? = null
        var activationFailures = 0

        override fun observeSemesters(): Flow<RepoResult<List<Semester>>> = flowOf(RepoResult.Ok(semesters.value))
        override fun observeActiveSemester(): Flow<RepoResult<Semester?>> = flowOf(RepoResult.Ok(active.value))
        override fun observeSemesterConfig(semesterId: Long): Flow<RepoResult<SemesterConfig?>> =
            flowOf(RepoResult.Ok(semesters.value.firstOrNull { it.id == semesterId }?.config))
        override suspend fun createSemester(draft: SemesterConfigDraft): RepoResult<Semester> {
            createCalls++
            val created = Semester(11, draft.displayName, emptySet(),
                SemesterConfig(draft.firstWeekMonday, draft.totalWeeks, draft.totalSections, draft.sectionTimes, 1))
            semesters.value = listOf(created)
            return RepoResult.Ok(created)
        }
        override suspend fun saveSemesterConfig(semesterId: Long, expectedRevision: Long,
            draft: SemesterConfigDraft): RepoResult<SemesterConfig> {
            saveCalls++
            lastExpectedRevision = expectedRevision
            saveResult?.let { return it }
            val config = SemesterConfig(draft.firstWeekMonday, draft.totalWeeks, draft.totalSections,
                draft.sectionTimes, expectedRevision + 1)
            val changed = requireNotNull(semesters.value.firstOrNull { it.id == semesterId })
                .copy(displayName = draft.displayName, config = config)
            semesters.value = listOf(changed)
            active.value = changed
            return RepoResult.Ok(config)
        }
        override suspend fun setActiveSemester(semesterId: Long): RepoResult<Unit> {
            activateCalls++
            if (activationFailures-- > 0) return RepoResult.Err(DataError(DataErrorCode.STORAGE_WRITE_FAILED))
            active.value = semesters.value.first { it.id == semesterId }
            return RepoResult.Ok(Unit)
        }
        override fun observeWeek(semesterId: Long, week: Int): Flow<RepoResult<WeekSchedule>> = error("unused")
        override fun observeActiveWeek(selectedWeek: Int): Flow<RepoResult<ActiveWeek?>> = error("unused")
        override fun observeCourseDetail(arrangementId: Long): Flow<RepoResult<CourseDetail?>> = error("unused")
        override fun observeImportStatus(semesterId: Long): Flow<RepoResult<SemesterImportStatus>> = error("unused")
        override suspend fun weekPosition(semesterId: Long, today: LocalDate): RepoResult<WeekPosition> = error("unused")
        override suspend fun prepareImport(request: ImportRequest): RepoResult<ImportPreview> = error("unused")
        override suspend fun commitImport(previewId: String, confirmation: ImportConfirmation): RepoResult<ImportReceipt> = error("unused")
        override suspend fun discardImport(previewId: String): RepoResult<Unit> = error("unused")
    }
}
