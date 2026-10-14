package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.MainDispatcherRule
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.repository.ScheduleRepository
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualCourseOperationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val monday = LocalDate.of(2026, 9, 21)
    private val config = SemesterConfig(monday, 16, 12, emptyList(), 1)
    private val semester = Semester(1, "2026秋季学期", emptySet(), config)

    @Test
    fun validateArrangementEnforcesAllRules() {
        // Valid draft
        val valid = ArrangementDraft("高等数学", "张老师", "A101", 1, 1, 2, setOf(1, 2, 3))
        assertTrue(ScheduleRules.validateArrangement(config, valid).isEmpty())

        // Blank name
        val blankName = valid.copy(name = "   ")
        val nameIssues = ScheduleRules.validateArrangement(config, blankName)
        assertEquals(1, nameIssues.size)
        assertEquals(RuleIssueCode.ARRANGEMENT_NAME_REQUIRED, nameIssues.first().code)

        // Day of week out of range
        val invalidDay = valid.copy(dayOfWeek = 8)
        val dayIssues = ScheduleRules.validateArrangement(config, invalidDay)
        assertEquals(1, dayIssues.size)
        assertEquals(RuleIssueCode.DAY_OUT_OF_RANGE, dayIssues.first().code)

        // Section range invalid: start > end
        val invertedSections = valid.copy(startSection = 3, endSection = 2)
        val sectionIssues = ScheduleRules.validateArrangement(config, invertedSections)
        assertEquals(1, sectionIssues.size)
        assertEquals(RuleIssueCode.SECTION_RANGE_INVALID, sectionIssues.first().code)

        // Section range invalid: end > totalSections
        val overflowSections = valid.copy(startSection = 1, endSection = 13)
        val overflowIssues = ScheduleRules.validateArrangement(config, overflowSections)
        assertEquals(1, overflowIssues.size)
        assertEquals(RuleIssueCode.SECTION_RANGE_INVALID, overflowIssues.first().code)

        // Empty weeks
        val noWeeks = valid.copy(weeks = emptySet())
        val weeksIssues = ScheduleRules.validateArrangement(config, noWeeks)
        assertEquals(1, weeksIssues.size)
        assertEquals(RuleIssueCode.WEEKS_REQUIRED, weeksIssues.first().code)

        // Week out of range
        val overflowWeek = valid.copy(weeks = setOf(1, 17))
        val overflowWeekIssues = ScheduleRules.validateArrangement(config, overflowWeek)
        assertEquals(1, overflowWeekIssues.size)
        assertEquals(RuleIssueCode.WEEK_OUT_OF_RANGE, overflowWeekIssues.first().code)
    }

    @Test
    fun findConflictsDetectsTrueOverlapsAndIgnoresDisjoint() {
        val existing = listOf(
            CourseArrangement(1, 1, "大学物理", "李老师", "B202", 1, 1, 2, setOf(1, 2, 3), CourseOrigin.Manual),
            CourseArrangement(2, 1, "线性代数", "王老师", "C303", 2, 3, 4, setOf(1, 2), CourseOrigin.Manual),
            CourseArrangement(3, 1, "体育", "赵老师", "操场", 1, 3, 4, setOf(1, 2, 3), CourseOrigin.Manual),
        )

        // Exact collision on day 1, sections 1..2, weeks 2..4 (overlaps on week 2 and 3)
        val targetOverlap = ArrangementDraft("计算机网络", null, null, 1, 2, 3, setOf(2, 4))
        val conflicts = ScheduleRules.findConflicts(targetOverlap, existing)
        assertEquals(2, conflicts.size) // conflicts with #1 (sec 2, wk 2) and #3 (sec 3, wk 2)
        assertTrue(conflicts.any { it.id == 1L })
        assertTrue(conflicts.any { it.id == 3L })

        // Self-exclusion when editing: excluding arrangement #1
        val selfConflicts = ScheduleRules.findConflicts(targetOverlap, existing, excludingArrangementId = 1L)
        assertEquals(1, selfConflicts.size)
        assertEquals(3L, selfConflicts.first().id)

        // Disjoint weeks: same day and section, but different weeks
        val disjointWeeks = ArrangementDraft("选修课", null, null, 1, 1, 2, setOf(4, 5))
        val noWeekConflicts = ScheduleRules.findConflicts(disjointWeeks, existing)
        assertEquals(0, noWeekConflicts.size)

        // Disjoint sections: same day and weeks, but sections 5..6
        val disjointSections = ArrangementDraft("选修课", null, null, 1, 5, 6, setOf(1, 2))
        val noSectionConflicts = ScheduleRules.findConflicts(disjointSections, existing)
        assertEquals(0, noSectionConflicts.size)
    }

    @Test
    fun viewModelManualCourseLifecycle() = runTest {
        val repo = TestScheduleRepository(semester)
        val viewModel = ScheduleViewModel(repo)

        // Collect states
        val states = mutableListOf<ScheduleUiState>()
        val forms = mutableListOf<CourseFormState>()
        val details = mutableListOf<CourseDetailUiState>()
        val job1 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { states.add(it) } }
        val job2 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.formState.collect { forms.add(it) } }
        val job3 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.detailState.collect { details.add(it) } }
        runCurrent()

        assertTrue(states.last() is ScheduleUiState.Ready)
        assertEquals(CourseFormState.Closed, forms.last())

        // 1. Open create course
        viewModel.openCreateCourse()
        runCurrent()
        val createForm = forms.last() as CourseFormState.Create
        assertEquals(1L, createForm.semesterId)
        assertEquals(config, createForm.config)

        // 2. Save manual course
        val newDraft = ArrangementDraft("操作系统", "孙老师", "D404", 3, 1, 2, setOf(1, 2, 3))
        var saved = false
        viewModel.saveCourse(newDraft, colorIndex = 2, onSuccess = { saved = true })
        runCurrent()

        assertTrue(saved)
        assertEquals(CourseFormState.Closed, forms.last())
        assertEquals(1, repo.courses.size)
        val savedCourse = repo.courses.first()
        assertEquals("操作系统", savedCourse.name)
        assertEquals(CourseOrigin.Manual, savedCourse.origin)

        // 3. Open edit course
        viewModel.openEditCourse(savedCourse)
        runCurrent()
        val editForm = forms.last() as CourseFormState.Edit
        assertEquals(savedCourse.id, editForm.arrangement.id)

        // 4. Update course
        val updatedDraft = newDraft.copy(name = "高级操作系统", location = "D405")
        var updated = false
        viewModel.saveCourse(updatedDraft, colorIndex = 3, onSuccess = { updated = true })
        runCurrent()

        assertTrue(updated)
        assertEquals(CourseFormState.Closed, forms.last())
        assertEquals("高级操作系统", repo.courses.first().name)
        assertEquals("D405", repo.courses.first().location)

        // 5. Open course detail and delete
        viewModel.showCourseDetail(savedCourse.id)
        runCurrent()
        assertTrue(details.last() is CourseDetailUiState.Ready)

        var deleted = false
        viewModel.deleteCourse(savedCourse.id, onSuccess = { deleted = true })
        runCurrent()

        assertTrue(deleted)
        assertTrue(repo.courses.isEmpty())
        assertEquals(CourseDetailUiState.Closed, details.last())

        job1.cancel()
        job2.cancel()
        job3.cancel()
    }

    @Test
    fun viewModelCopyImportedCourseToManual() = runTest {
        val repo = TestScheduleRepository(semester)
        val viewModel = ScheduleViewModel(repo)

        val importedCourse = CourseArrangement(
            id = 100,
            semesterId = 1,
            name = "软件工程",
            teacher = "陈老师",
            location = "知明1-101",
            dayOfWeek = 2,
            startSection = 3,
            endSection = 4,
            weeks = setOf(1, 2, 3, 4),
            origin = CourseOrigin.SchoolImport(SourceScope("fjnu", "dom", "2026-1"), "batch-1", "entry-1"),
        )
        repo.courses.add(importedCourse)

        val forms = mutableListOf<CourseFormState>()
        val stateJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.formState.collect { forms.add(it) } }
        runCurrent()

        // Copy as manual course
        viewModel.openCopyCourse(importedCourse)
        runCurrent()
        val copyForm = forms.last() as CourseFormState.Copy
        assertEquals(importedCourse.name, copyForm.arrangement.name)

        // Save copied course
        val copyDraft = ArrangementDraft(
            importedCourse.name + "（手动）",
            importedCourse.teacher,
            importedCourse.location,
            importedCourse.dayOfWeek,
            importedCourse.startSection,
            importedCourse.endSection,
            importedCourse.weeks,
        )
        var copied = false
        viewModel.saveCourse(copyDraft, colorIndex = 4, onSuccess = { copied = true })
        runCurrent()

        assertTrue(copied)
        assertEquals(2, repo.courses.size)
        val manualCopy = repo.courses.first { it.name.contains("手动") }
        assertEquals(CourseOrigin.Manual, manualCopy.origin)
        assertEquals(setOf(1, 2, 3, 4), manualCopy.weeks)

        job.cancel()
        stateJob.cancel()
    }

    private class TestScheduleRepository(initialSemester: Semester) : ScheduleRepository {
        val activeSemester = MutableStateFlow<RepoResult<Semester?>>(RepoResult.Ok(initialSemester))
        val courses = mutableListOf<CourseArrangement>()
        val courseFlow = MutableStateFlow<List<CourseArrangement>>(emptyList())

        override fun observeActiveSemester(): Flow<RepoResult<Semester?>> = activeSemester
        override fun observeActiveWeek(selectedWeek: Int): Flow<RepoResult<ActiveWeek?>> =
            combine(activeSemester, courseFlow) { result, list ->
                val sem = (result as? RepoResult.Ok)?.value ?: return@combine RepoResult.Ok(null)
                RepoResult.Ok(ActiveWeek(sem, ScheduleRules.weekSchedule(
                    sem.id, requireNotNull(sem.config), selectedWeek, list
                )))
            }
        override fun observeCourseDetail(arrangementId: Long): Flow<RepoResult<CourseDetail?>> {
            val item = courses.firstOrNull { it.id == arrangementId }
            return flowOf(RepoResult.Ok(item?.let { CourseDetail(it, "2026秋季学期", null, null) }))
        }
        override fun observeImportStatus(semesterId: Long): Flow<RepoResult<SemesterImportStatus>> =
            flowOf(RepoResult.Ok(SemesterImportStatus(semesterId, emptyList())))

        override suspend fun weekPosition(semesterId: Long, today: LocalDate): RepoResult<WeekPosition> =
            RepoResult.Ok(WeekPosition.InSemester(1))

        override suspend fun createManualCourse(semesterId: Long, draft: ArrangementDraft): RepoResult<CourseArrangement> {
            val id = (courses.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = CourseArrangement(
                id, semesterId, draft.name, draft.teacher, draft.location,
                draft.dayOfWeek, draft.startSection, draft.endSection, draft.weeks,
                CourseOrigin.Manual,
            )
            courses.add(created)
            courseFlow.value = courses.toList()
            return RepoResult.Ok(created)
        }

        override suspend fun updateManualCourse(arrangementId: Long, draft: ArrangementDraft): RepoResult<CourseArrangement> {
            val idx = courses.indexOfFirst { it.id == arrangementId }
            if (idx == -1) return RepoResult.Err(DataError(DataErrorCode.COURSE_NOT_FOUND))
            val updated = courses[idx].copy(
                name = draft.name, teacher = draft.teacher, location = draft.location,
                dayOfWeek = draft.dayOfWeek, startSection = draft.startSection, endSection = draft.endSection,
                weeks = draft.weeks,
            )
            courses[idx] = updated
            courseFlow.value = courses.toList()
            return RepoResult.Ok(updated)
        }

        override suspend fun deleteManualCourse(arrangementId: Long): RepoResult<Unit> {
            val removed = courses.removeAll { it.id == arrangementId }
            courseFlow.value = courses.toList()
            return if (removed) RepoResult.Ok(Unit) else RepoResult.Err(DataError(DataErrorCode.COURSE_NOT_FOUND))
        }

        override suspend fun checkArrangementConflict(
            semesterId: Long, draft: ArrangementDraft, excludingArrangementId: Long?,
        ): RepoResult<List<CourseArrangement>> = RepoResult.Ok(
            ScheduleRules.findConflicts(draft, courses, excludingArrangementId)
        )

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
