package com.example.mobileschedule.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.*
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleManagementTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineScheduleRepository
    private lateinit var management: ScheduleManagementService

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java).build()
        repository = OfflineScheduleRepository(db)
        management = ScheduleManagementService(db)
    }
    @After fun close() { db.close() }

    private suspend fun semester(name: String): Semester = (repository.createSemester(SemesterConfigDraft(
        name, LocalDate.of(2026, 9, 7), 20, 8, emptyList())) as RepoResult.Ok).value

    private suspend fun addCourse(id: Long, name: String) {
        val courseId = db.courseDao().insertCourses(listOf(CourseEntity(name = name, teacher = "教师", location = "A101",
            dayOfWeek = 2, startSection = 1, endSection = 2, semesterId = id, originType = CourseOriginType.MANUAL))).single()
        db.courseDao().insertWeeks(listOf(CourseWeekEntity(courseId, 1), CourseWeekEntity(courseId, 3)))
    }

    @Test fun exportIncludesAllWeeksAndOnlySelectedSemester() = runBlocking {
        val first = semester("导出学期")
        val second = semester("另一学期")
        addCourse(first.id, "课程甲")
        addCourse(second.id, "课程乙")
        val json = Json.parseToJsonElement(management.exportSemester(first.id)).jsonObject
        assertEquals("导出学期", json.getValue("semester").jsonObject.getValue("displayName").jsonPrimitive.content)
        val rows = json.getValue("arrangements").jsonArray
        assertEquals(1, rows.size)
        assertEquals("课程甲", rows.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(listOf(1, 3), rows.single().jsonObject.getValue("weeks").jsonArray.map { it.jsonPrimitive.int })
        assertEquals(1, (repository.observeWeek(first.id, 3).first() as RepoResult.Ok).value.arrangements.size)
    }

    @Test fun clearingSemesterExpiresItsPreparedImportToken() = runBlocking {
        val target = semester("预览失效合成学期")
        addCourse(target.id, "待清除合成课程")
        val parsed = ZhengfangOnlineRead.withQuickImportAcceptance(ZhengfangOnlineRead.parse(
            """{"xsxx":{"XNM":"2026","XQM":"3"},"sjkList":[],"kbList":[{"kcmc":"待导入合成课程","jc":"1-2节","xqj":"2","zcd":"1周"}]}""",
            target.id, ZhengfangOnlineRead.selectTerm("2026", 1)!!))!!
        val preview = (repository.prepareImport(parsed.request) as RepoResult.Ok).value
        assertTrue(preview.canCommit)
        assertEquals(1, management.clearSemester(target.id))
        val result = repository.commitImport(preview.previewId, ImportConfirmation(preview.previewId,
            preview.scope!!, preview.replaceCount!!, preview.validCount))
        assertEquals(DataErrorCode.PREVIEW_STALE, (result as RepoResult.Err).error.code)
        assertTrue((repository.observeWeek(target.id, 1).first() as RepoResult.Ok).value.arrangements.isEmpty())
    }

    @Test fun clearRetainsCalendarAndOtherSemestersAndReleasesSchoolBinding() = runBlocking {
        val first = semester("清除学期")
        val second = semester("保留学期")
        repository.setActiveSemester(first.id)
        addCourse(first.id, "手动课程")
        addCourse(second.id, "保留课程")
        db.semesterDao().insertSourceBinding(SourceBindingEntity("fjnu", "fjnu-zhengfang-web", "term", first.id, "来源"))
        db.importBatchDao().insertBatch(ImportBatchEntity("batch", first.id, "fjnu", "fjnu-zhengfang-web", "term", 0, 1, 0))
        val courseId = db.courseDao().insertCourses(listOf(CourseEntity(name = "教务课程", teacher = null, location = null,
            dayOfWeek = 1, startSection = 1, endSection = 2, semesterId = first.id,
            originType = CourseOriginType.SCHOOL_IMPORT, importBatchId = "batch"))).single()
        db.courseDao().insertWeeks(listOf(CourseWeekEntity(courseId, 1)))
        assertEquals(2, management.clearSemester(first.id))
        assertTrue((repository.observeWeek(first.id, 1).first() as RepoResult.Ok).value.arrangements.isEmpty())
        assertEquals(1, (repository.observeWeek(second.id, 1).first() as RepoResult.Ok).value.arrangements.size)
        val active = (repository.observeActiveSemester().first() as RepoResult.Ok).value!!
        assertEquals(first.id, active.id)
        assertEquals(first.config!!.firstWeekMonday, active.config!!.firstWeekMonday)
        assertTrue(active.config.revision > first.config.revision)
        assertTrue(active.sourceBindings.isEmpty())
        assertNull(db.importBatchDao().getBatch("batch"))
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }
}
