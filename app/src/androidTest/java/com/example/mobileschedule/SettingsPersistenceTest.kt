package com.example.mobileschedule

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.model.ActiveWeek
import com.example.mobileschedule.data.model.CourseDetail
import com.example.mobileschedule.data.repository.OfflineScheduleRepository
import com.example.mobileschedule.ui.settings.ConfigEditorState
import com.example.mobileschedule.ui.settings.SectionTimeText
import com.example.mobileschedule.ui.settings.SemesterFormInput
import com.example.mobileschedule.ui.settings.SettingsEditorViewModel
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "settings-ui-isolated-test.db"
    private val monday = LocalDate.of(2026, 9, 21)
    private lateinit var database: AppDatabase

    @Before fun open() {
        context.deleteDatabase(dbName)
        database = AppDatabase.open(context, dbName)
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(dbName)
    }

    @Test fun editorSaveUpdatesSubscribedWeekAndDetailAndSurvivesDatabaseReopen() = runBlocking {
        var repository = OfflineScheduleRepository(database)
        val create = SettingsEditorViewModel(repository)
        create.open(null)
        create.updateInput { SemesterFormInput("合成学期", monday, "12", "2") }
        create.save()
        withTimeout(10_000) { create.state.first { it == ConfigEditorState.Saved } }
        val semester = (repository.observeActiveSemester().first() as RepoResult.Ok).value!!
        assertEquals("合成学期", semester.displayName)

        val courseId = database.courseDao().insertCourses(listOf(CourseEntity(
            name = "数据库联测课程", teacher = "教师", location = "A101", dayOfWeek = 1,
            startSection = 1, endSection = 2, semesterId = semester.id,
            originType = CourseOriginType.MANUAL,
        ))).single()
        database.courseDao().insertWeeks(listOf(CourseWeekEntity(courseId, 1)))
        val weeks = repository.observeActiveWeek(1).produceIn(this)
        val details = repository.observeCourseDetail(courseId).produceIn(this)
        assertNotNull((weeks.receive() as RepoResult.Ok).value)
        assertEquals(null, (details.receive() as RepoResult.Ok).value?.startTime)

        val edit = SettingsEditorViewModel(repository)
        edit.open(semester.id)
        withTimeout(10_000) { edit.state.first { it is ConfigEditorState.Editing } }
        edit.updateInput { it.copy(displayName = "修改后学期", timesEnabled = true,
            sectionTimes = mapOf(1 to SectionTimeText("08:00", "08:45"),
                2 to SectionTimeText("08:50", "09:35"))) }
        edit.save()
        assertTrue((edit.state.value as ConfigEditorState.Editing).impactPending)
        edit.confirmImpactAndSave()
        withTimeout(10_000) { edit.state.first { it == ConfigEditorState.Saved } }
        val updatedWeek = withTimeout(10_000) {
            var observed: ActiveWeek? = null
            while (observed == null) {
                val next = (weeks.receive() as RepoResult.Ok).value!!
                if (next.semester.displayName == "修改后学期" && next.semester.config!!.sectionTimes.size == 2) observed = next
            }
            requireNotNull(observed)
        }
        assertEquals(courseId, updatedWeek.schedule.arrangements.single().id)
        val updatedDetail = withTimeout(10_000) {
            var observed: CourseDetail? = null
            while (observed == null) {
                val next = (details.receive() as RepoResult.Ok).value!!
                if (next.startTime == LocalTime.of(8, 0)) observed = next
            }
            requireNotNull(observed)
        }
        assertEquals(LocalTime.of(9, 35), updatedDetail.endTime)
        assertEquals("修改后学期", updatedDetail.semesterDisplayName)
        weeks.cancel()
        details.cancel()

        database.close()
        database = AppDatabase.open(context, dbName)
        repository = OfflineScheduleRepository(database)
        val restored = (repository.observeActiveSemester().first() as RepoResult.Ok).value!!
        assertEquals("修改后学期", restored.displayName)
        assertEquals(2, restored.config!!.revision)
        assertEquals(LocalTime.of(8, 0), restored.config.sectionTimes.first().start)
        assertEquals(courseId, (repository.observeActiveWeek(1).first() as RepoResult.Ok)
            .value!!.schedule.arrangements.single().id)
        assertEquals(LocalTime.of(9, 35), (repository.observeCourseDetail(courseId).first() as RepoResult.Ok)
            .value!!.endTime)
        val reopenedEditor = SettingsEditorViewModel(repository)
        reopenedEditor.open(semester.id)
        val restoredForm = withTimeout(10_000) {
            reopenedEditor.state.first { it is ConfigEditorState.Editing } as ConfigEditorState.Editing
        }
        assertEquals("修改后学期", restoredForm.input.displayName)
        assertEquals("08:00", restoredForm.input.sectionTimes.getValue(1).start)
    }
}
