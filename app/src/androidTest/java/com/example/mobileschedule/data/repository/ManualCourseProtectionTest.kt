package com.example.mobileschedule.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.model.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualCourseProtectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "manual-course-protection-test.db"
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before
    fun open() {
        context.deleteDatabase(dbName)
        database = AppDatabase.open(context, dbName)
        repository = OfflineScheduleRepository(database)
    }

    @After
    fun close() {
        database.close()
        context.deleteDatabase(dbName)
    }

    private fun <T> ok(result: RepoResult<T>): T {
        assertTrue("Expected Ok, got $result", result is RepoResult.Ok)
        return (result as RepoResult.Ok).value
    }

    private suspend fun createSemester(name: String = "福建师范大学2026秋季学期") = ok(
        repository.createSemester(
            SemesterConfigDraft(name, LocalDate.of(2026, 9, 7), 20, 12, emptyList())
        )
    )

    private fun candidate(
        index: Int,
        name: String,
        day: Int,
        startSec: Int,
        endSec: Int,
        weeks: Set<Int> = (1..16).toSet(),
    ) = ImportCandidate(
        sourceIndex = index,
        sourceEntryId = "entry-$index",
        name = name,
        teacher = "教师-$index",
        location = "教室-$index",
        dayOfWeek = day,
        startSection = startSec,
        endSection = endSec,
        weeks = weeks,
    )

    private fun importRequest(
        semesterId: Long,
        rows: List<ImportCandidate>,
        school: String = "fjnu",
        source: String = "dom",
        term: String = "2026-fall",
    ) = ImportRequest(
        targetSemesterId = semesterId,
        schoolId = school,
        sourceId = source,
        sourceTermId = term,
        sourceTermLabel = "2026-2027学年第一学期",
        sourceObservedCount = rows.size,
        reportedSourceTotalCount = rows.size,
        candidates = rows,
        parseIssues = emptyList(),
        completeness = CompletenessEvidence(
            CompletenessStatus.VERIFIED_FULL,
            term,
            listOf(0),
            1,
            rows.size,
            "synthetic 14-course fixture",
        ),
    )

    private suspend fun commitRequest(request: ImportRequest): ImportReceipt {
        val preview = ok(repository.prepareImport(request))
        assertTrue("Preview should be committable: $preview", preview.canCommit)
        return ok(
            repository.commitImport(
                preview.previewId,
                ImportConfirmation(preview.previewId, preview.scope!!, preview.replaceCount!!, preview.validCount),
            )
        )
    }

    @Test
    fun manualCourseCrudOperationsWorkAtomically() = runBlocking {
        val semester = createSemester()

        // 1. Create manual course
        val createDraft = ArrangementDraft(
            name = "高等数学",
            teacher = "张教授",
            location = "知明1-101",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = setOf(1, 2, 3, 4),
        )
        val created = ok(repository.createManualCourse(semester.id, createDraft))
        assertEquals("高等数学", created.name)
        assertEquals("张教授", created.teacher)
        assertEquals("知明1-101", created.location)
        assertEquals(1, created.dayOfWeek)
        assertEquals(1, created.startSection)
        assertEquals(2, created.endSection)
        assertEquals(setOf(1, 2, 3, 4), created.weeks)
        assertEquals(CourseOrigin.Manual, created.origin)

        // Verify observeWeek
        val week1Courses = ok(repository.observeWeek(semester.id, 1).first()).arrangements
        assertEquals(1, week1Courses.size)
        assertEquals(created.id, week1Courses.first().id)

        val week5Courses = ok(repository.observeWeek(semester.id, 5).first()).arrangements
        assertTrue("Week 5 should have no arrangement", week5Courses.isEmpty())

        // 2. Conflict checking
        val overlapDraft = ArrangementDraft("线性代数", null, null, 1, 2, 3, setOf(2, 5))
        val conflicts = ok(repository.checkArrangementConflict(semester.id, overlapDraft))
        assertEquals(1, conflicts.size)
        assertEquals(created.id, conflicts.first().id)

        val disjointDraft = ArrangementDraft("大学英语", null, null, 2, 1, 2, setOf(1, 2))
        val noConflicts = ok(repository.checkArrangementConflict(semester.id, disjointDraft))
        assertTrue(noConflicts.isEmpty())

        // 3. Update manual course
        val updateDraft = createDraft.copy(
            name = "高等数学(重修班)",
            teacher = "李教授",
            location = "知明2-202",
            dayOfWeek = 2,
            startSection = 3,
            endSection = 4,
            weeks = setOf(2, 4),
        )
        val updated = ok(repository.updateManualCourse(created.id, updateDraft))
        assertEquals("高等数学(重修班)", updated.name)
        assertEquals("李教授", updated.teacher)
        assertEquals(2, updated.dayOfWeek)
        assertEquals(3, updated.startSection)
        assertEquals(4, updated.endSection)
        assertEquals(setOf(2, 4), updated.weeks)
        assertEquals(CourseOrigin.Manual, updated.origin)

        // Verify week 1 no longer has the course, but week 2 has it on day 2
        val week1After = ok(repository.observeWeek(semester.id, 1).first()).arrangements
        assertTrue(week1After.isEmpty())
        val week2After = ok(repository.observeWeek(semester.id, 2).first()).arrangements
        assertEquals(1, week2After.size)
        assertEquals(updated.id, week2After.first().id)
        assertEquals(2, week2After.first().dayOfWeek)

        // 4. Delete manual course
        ok(repository.deleteManualCourse(created.id))
        val week2Final = ok(repository.observeWeek(semester.id, 2).first()).arrangements
        assertTrue(week2Final.isEmpty())
    }

    /**
     * Core Anti-Overwrite Protection Test:
     * 验证“教务导入 14 门课 -> 手动添加 2 门课 -> 再次重导教务课表 -> 手动 2 门课依然存在且教务课表正确替换”。
     */
    @Test
    fun schoolReimportProtectsManualCoursesFromBeingOverwritten() = runBlocking {
        val target = createSemester("福建师范大学 2026-2027学年第1学期")

        // Step 1: 教务导入 14 门课
        val initialSchoolCourses = (0 until 14).map { i ->
            val day = (i % 5) + 1
            val startSec = ((i / 5) * 2) + 1
            candidate(i, "教务课程_$i", day, startSec, startSec + 1, weeks = (1..16).toSet())
        }
        val firstRequest = importRequest(target.id, initialSchoolCourses)
        val firstReceipt = commitRequest(firstRequest)

        assertEquals(0, firstReceipt.removedCount)
        assertEquals(14, firstReceipt.savedCount)

        val week1AfterFirstImport = ok(repository.observeWeek(target.id, 1).first()).arrangements
        assertEquals(14, week1AfterFirstImport.size)
        val firstBatchCourseIds = week1AfterFirstImport.map { it.id }.toSet()
        assertTrue(week1AfterFirstImport.all { it.origin is CourseOrigin.SchoolImport })
        assertTrue(week1AfterFirstImport.all { (it.origin as CourseOrigin.SchoolImport).batchId == firstReceipt.batchId })

        // Step 2: 手动添加 2 门课
        val manualDraft1 = ArrangementDraft(
            name = "社团活动",
            teacher = "陈指导",
            location = "学生活动中心301",
            dayOfWeek = 3,
            startSection = 9,
            endSection = 10,
            weeks = (1..10).toSet(),
        )
        val manual1 = ok(repository.createManualCourse(target.id, manualDraft1))
        assertEquals("社团活动", manual1.name)
        assertEquals(CourseOrigin.Manual, manual1.origin)

        val manualDraft2 = ArrangementDraft(
            name = "创新创业实验",
            teacher = "林导师",
            location = "创新实验室402",
            dayOfWeek = 5,
            startSection = 7,
            endSection = 8,
            weeks = (1..16).toSet(),
        )
        val manual2 = ok(repository.createManualCourse(target.id, manualDraft2))
        assertEquals("创新创业实验", manual2.name)
        assertEquals(CourseOrigin.Manual, manual2.origin)

        // 验证当前总课程数变为 16 门（14 门教务课 + 2 门手动课）
        val week1WithManual = ok(repository.observeWeek(target.id, 1).first()).arrangements
        assertEquals(16, week1WithManual.size)
        assertTrue(week1WithManual.any { it.id == manual1.id && it.origin is CourseOrigin.Manual })
        assertTrue(week1WithManual.any { it.id == manual2.id && it.origin is CourseOrigin.Manual })

        // Step 3: 再次重导教务课表（同源同范围替换：14 门新版教务课）
        val updatedSchoolCourses = (0 until 14).map { i ->
            val day = (i % 5) + 1
            val startSec = ((i / 5) * 2) + 1
            candidate(i, "教务课程_更新版_$i", day, startSec, startSec + 1, weeks = (1..16).toSet())
        }
        val secondRequest = importRequest(target.id, updatedSchoolCourses)
        val secondReceipt = commitRequest(secondRequest)

        assertEquals("Should replace exactly the 14 imported courses", 14, secondReceipt.removedCount)
        assertEquals("Should insert exactly 14 new courses", 14, secondReceipt.savedCount)
        assertNotEquals("Should have a new batch ID", firstReceipt.batchId, secondReceipt.batchId)

        // Step 4: 核心断言：手动 2 门课依然存在，且教务课表正确替换！
        val week1Final = ok(repository.observeWeek(target.id, 1).first()).arrangements
        assertEquals("Total arrangement count must remain 16 (14 replaced school + 2 intact manual)", 16, week1Final.size)

        // 断言 2 门手动课完好无损，ID、名称、周次与 Manual 标记均保持原样
        val retainedManual1 = week1Final.firstOrNull { it.id == manual1.id }
        assertNotNull("手动课程1必须依然存在", retainedManual1)
        assertEquals("社团活动", retainedManual1!!.name)
        assertEquals("学生活动中心301", retainedManual1.location)
        assertEquals(CourseOrigin.Manual, retainedManual1.origin)
        assertEquals((1..10).toSet(), retainedManual1.weeks)

        val retainedManual2 = week1Final.firstOrNull { it.id == manual2.id }
        assertNotNull("手动课程2必须依然存在", retainedManual2)
        assertEquals("创新创业实验", retainedManual2!!.name)
        assertEquals("创新实验室402", retainedManual2.location)
        assertEquals(CourseOrigin.Manual, retainedManual2.origin)
        assertEquals((1..16).toSet(), retainedManual2.weeks)

        // 断言 14 门教务课全部已替换为新版，且旧教务课程全部移除
        val schoolCoursesFinal = week1Final.filter { it.origin is CourseOrigin.SchoolImport }
        assertEquals(14, schoolCoursesFinal.size)
        assertTrue("All 14 school courses should belong to the new batch",
            schoolCoursesFinal.all { (it.origin as CourseOrigin.SchoolImport).batchId == secondReceipt.batchId })
        assertTrue("All 14 school courses should have the updated names",
            schoolCoursesFinal.all { it.name.startsWith("教务课程_更新版_") })
        assertTrue("None of the old course IDs should survive",
            schoolCoursesFinal.none { it.id in firstBatchCourseIds })
    }
}
