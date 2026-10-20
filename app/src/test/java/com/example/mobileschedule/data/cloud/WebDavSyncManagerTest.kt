package com.example.mobileschedule.data.cloud

import com.example.mobileschedule.data.repository.ScheduleManagementService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class WebDavSyncManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient
    private lateinit var syncManager: WebDavSyncManager
    private lateinit var config: WebDavConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val baseUrl = server.url("/").toString()
        config = WebDavConfig(
            serverUrl = baseUrl,
            username = "test_user",
            password = "test_password",
            remoteDir = "/MobileSchedule/",
        )
        client = WebDavClient()

        val fakeStore = WebDavConfigStore()
        fakeStore.saveConfig(config)

        // ScheduleManagementService without DB can parse JSON
        val managementService = ScheduleManagementService()
        syncManager = WebDavSyncManager(client, fakeStore, managementService)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listCloudBackups_filtersBackupsAndExtractsSemesterName() = runBlocking {
        val xmlBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
                <D:response>
                    <D:href>/MobileSchedule/</D:href>
                    <D:propstat>
                        <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
                <D:response>
                    <D:href>/MobileSchedule/backup_semester_2026%E7%A7%8B%E5%AD%A3%E5%AD%A6%E6%9C%9F_20261019_220000.json</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:displayname>backup_semester_2026秋季学期_20261019_220000.json</D:displayname>
                            <D:resourcetype/>
                            <D:getcontentlength>4096</D:getcontentlength>
                            <D:getlastmodified>Mon, 19 Oct 2026 22:00:00 GMT</D:getlastmodified>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
                <D:response>
                    <D:href>/MobileSchedule/other_file.txt</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:displayname>other_file.txt</D:displayname>
                            <D:resourcetype/>
                            <D:getcontentlength>128</D:getcontentlength>
                            <D:getlastmodified>Mon, 19 Oct 2026 21:00:00 GMT</D:getlastmodified>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
            </D:multistatus>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(207).setBody(xmlBody))

        val result = syncManager.listCloudBackups(config)
        assertTrue(result.isSuccess)
        val backups = result.getOrThrow()

        // other_file.txt should be filtered out
        assertEquals(1, backups.size)
        val item = backups.first()
        assertEquals("backup_semester_2026秋季学期_20261019_220000.json", item.fileName)
        assertEquals("2026秋季学期", item.semesterName)
        assertEquals(4096L, item.sizeBytes)
        assertEquals("4 KB", item.formattedSize)
    }

    @Test
    fun downloadAndPreparePreview_downloadsAndParsesJson() = runBlocking {
        val validJson = """
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
                  "name": "高等数学",
                  "teacher": "张教授",
                  "location": "知明1-101",
                  "dayOfWeek": 1,
                  "startSection": 1,
                  "endSection": 2,
                  "weeks": [1, 2, 3]
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(validJson))

        val backupItem = WebDavBackupItem(
            fileName = "backup_semester_2026秋季学期_20261019_220000.json",
            fileUrl = config.resolveFileUrl("backup_semester_2026秋季学期_20261019_220000.json"),
            semesterName = "2026秋季学期",
            lastModifiedEpochMillis = System.currentTimeMillis(),
            sizeBytes = validJson.length.toLong(),
        )

        val result = syncManager.downloadAndPreparePreview(backupItem, config)
        assertTrue("downloadAndPreparePreview should succeed", result.isSuccess)

        val preview = result.getOrThrow()
        assertEquals("2026秋季学期", preview.displayName)
        assertEquals(16, preview.totalWeeks)
        assertEquals(2, preview.totalSections)
        assertEquals(1, preview.arrangementCount)
        assertEquals("高等数学", preview.arrangements.first().name)
    }
}
