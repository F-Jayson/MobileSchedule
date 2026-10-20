package com.example.mobileschedule

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.cloud.WebDavBackupItem
import com.example.mobileschedule.data.cloud.WebDavClient
import com.example.mobileschedule.data.cloud.WebDavConfig
import com.example.mobileschedule.data.cloud.WebDavConfigStore
import com.example.mobileschedule.data.cloud.WebDavSyncManager
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseOriginType
import com.example.mobileschedule.data.local.entity.CourseWeekEntity
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.model.SemesterConfigDraft
import com.example.mobileschedule.data.model.RepoResult
import com.example.mobileschedule.data.repository.OfflineScheduleRepository
import com.example.mobileschedule.data.repository.ScheduleManagementService
import com.example.mobileschedule.ui.settings.ScheduleManagementScreen
import com.example.mobileschedule.ui.settings.SettingsHomeUiState
import com.example.mobileschedule.ui.settings.WebDavSyncDialog
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WebDavSyncUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineScheduleRepository
    private lateinit var management: ScheduleManagementService
    private lateinit var mockServer: MockWebServer

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = OfflineScheduleRepository(db)
        management = ScheduleManagementService(db)
        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun tearDown() {
        db.close()
        mockServer.shutdown()
    }

    @Test
    fun testWebDavCardRendersAndOpensDialog() {
        val sampleConfig = WebDavConfig(
            serverUrl = "https://dav.jianguoyun.com/dav/",
            username = "student@school.edu.cn",
            password = "safe_app_password",
            remoteDir = "/MobileSchedule/",
        )

        compose.setContent {
            MobileScheduleTheme {
                ScheduleManagementScreen(
                    state = SettingsHomeUiState.Ready(emptyList(), null),
                    webDavConfig = sampleConfig,
                )
            }
        }

        // WebDAV Card displayed
        compose.onNodeWithTag("management_webdav_card").assertIsDisplayed()
        compose.onNodeWithText("WebDAV 私有云同步", substring = true).assertIsDisplayed()
        compose.onNodeWithText("已配置").assertIsDisplayed()

        // Click open sync dialog
        compose.onNodeWithTag("management_webdav_open_button").performClick()
        compose.onNodeWithTag("webdav_dialog").assertIsDisplayed()
        compose.onNodeWithText("WebDAV 课表云同步").assertIsDisplayed()

        // Capture evidence
        saveSyntheticScreenshot(
            "webdav-sync-dialog.png",
            compose.onNodeWithTag("webdav_dialog").captureToImage().asAndroidBitmap(),
        )
    }

    @Test
    fun testWebDavDuplicateSemesterDetectionAndRoomOverwriteTransaction() = runBlocking {
        // 1. Create a local semester "2026-2027学年秋季学期" with a course "高等数学"
        val originalSemester = (repository.createSemester(
            SemesterConfigDraft("2026-2027学年秋季学期", LocalDate.of(2026, 9, 7), 16, 2, emptyList())
        ) as RepoResult.Ok).value

        val courseId = db.courseDao().insertCourses(
            listOf(
                CourseEntity(
                    semesterId = originalSemester.id,
                    name = "高等数学",
                    teacher = "张教授",
                    location = "知明1-101",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    originType = CourseOriginType.MANUAL,
                )
            )
        ).single()
        db.courseDao().insertWeeks(listOf(CourseWeekEntity(courseId, 1), CourseWeekEntity(courseId, 2)))

        // 2. Prepare a remote backup JSON with the SAME semester name but updated course "大学物理"
        val cloudBackupJson = """
            {
              "formatVersion": 1,
              "semester": {
                "displayName": "2026-2027学年秋季学期",
                "firstWeekMonday": "2026-09-07",
                "totalWeeks": 16,
                "totalSections": 2,
                "sectionTimes": [
                  {"section": 1, "start": "08:20", "end": "09:05"},
                  {"section": 2, "start": "09:15", "end": "10:00"}
                ]
              },
              "arrangements": [
                {
                  "name": "大学物理",
                  "teacher": "李老师",
                  "location": "实验楼B202",
                  "dayOfWeek": 3,
                  "startSection": 1,
                  "endSection": 2,
                  "weeks": [1, 2, 3]
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(cloudBackupJson))

        val client = WebDavClient()
        val fakeStore = WebDavConfigStore()
        val serverUrl = mockServer.url("/").toString()
        val config = WebDavConfig(serverUrl, "user", "pass", "/MobileSchedule/")
        fakeStore.saveConfig(config)

        val syncManager = WebDavSyncManager(client, fakeStore, management)
        val backupItem = WebDavBackupItem(
            fileName = "backup_semester_2026-2027秋_20261019_220000.json",
            fileUrl = config.resolveFileUrl("backup_semester_2026-2027秋_20261019_220000.json"),
            semesterName = "2026-2027学年秋季学期",
            lastModifiedEpochMillis = System.currentTimeMillis(),
            sizeBytes = cloudBackupJson.length.toLong(),
        )

        // 3. Download and prepare preview -> must detect duplicate semester!
        val previewResult = syncManager.downloadAndPreparePreview(backupItem, config)
        assertTrue(previewResult.isSuccess)
        val preview = previewResult.getOrThrow()

        assertEquals("2026-2027学年秋季学期", preview.displayName)
        assertNotNull("Should detect existing duplicate semester ID", preview.existingSemesterId)
        assertEquals(originalSemester.id, preview.existingSemesterId)

        // 4. Test Overwrite Room Transaction
        val commitResult = management.commitImport(preview, overwriteExisting = true)
        assertTrue(commitResult.isSuccess)
        val targetId = commitResult.getOrThrow()
        assertEquals("Target semester ID should remain same on overwrite", originalSemester.id, targetId)

        // Verify Room data: "高等数学" should be gone, "大学物理" should be present
        val updatedCourses = db.courseDao().getSemesterCourses(targetId)
        assertEquals(1, updatedCourses.size)
        assertEquals("大学物理", updatedCourses.first().course.name)
    }

    @Test
    fun testWebDavDuplicateSemesterImportAsNewTransaction() = runBlocking {
        // 1. Create a local semester "2026秋季学期"
        val originalSemester = (repository.createSemester(
            SemesterConfigDraft("2026秋季学期", LocalDate.of(2026, 9, 7), 16, 2, emptyList())
        ) as RepoResult.Ok).value

        db.courseDao().insertCourses(
            listOf(
                CourseEntity(
                    semesterId = originalSemester.id,
                    name = "数据结构",
                    teacher = "王老师",
                    location = "立诚1-201",
                    dayOfWeek = 2,
                    startSection = 1,
                    endSection = 2,
                    originType = CourseOriginType.MANUAL,
                )
            )
        )

        // 2. Prepare cloud backup with same semester name
        val cloudBackupJson = """
            {
              "formatVersion": 1,
              "semester": {
                "displayName": "2026秋季学期",
                "firstWeekMonday": "2026-09-07",
                "totalWeeks": 16,
                "totalSections": 2,
                "sectionTimes": [
                  {"section": 1, "start": "08:20", "end": "09:05"},
                  {"section": 2, "start": "09:15", "end": "10:00"}
                ]
              },
              "arrangements": [
                {
                  "name": "编译原理",
                  "teacher": "赵老师",
                  "location": "立诚2-302",
                  "dayOfWeek": 4,
                  "startSection": 1,
                  "endSection": 2,
                  "weeks": [1, 2]
                }
              ]
            }
        """.trimIndent()

        val preview = management.importSemesterFromJson(cloudBackupJson).getOrThrow()
        assertNotNull(preview.existingSemesterId)

        // 3. Commit as NEW semester (overwriteExisting = false)
        val commitResult = management.commitImport(preview, overwriteExisting = false)
        assertTrue(commitResult.isSuccess)
        val newSemesterId = commitResult.getOrThrow()
        assertNotEquals(originalSemester.id, newSemesterId)

        // 4. Verify original and new semesters both exist in Room
        val originalCourses = db.courseDao().getSemesterCourses(originalSemester.id)
        assertEquals("数据结构", originalCourses.first().course.name)

        val newCourses = db.courseDao().getSemesterCourses(newSemesterId)
        assertEquals("编译原理", newCourses.first().course.name)

        val newSemesterEntity = db.semesterDao().getSemester(newSemesterId)
        assertNotNull(newSemesterEntity)
        assertEquals("2026秋季学期 (导入)", newSemesterEntity!!.semester.displayName)
    }
}
