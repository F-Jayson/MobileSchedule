package com.example.mobileschedule.data.cloud

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebDavClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient
    private lateinit var config: WebDavConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val baseUrl = server.url("/").toString()
        config = WebDavConfig(
            serverUrl = baseUrl,
            username = "test_user",
            password = "test_password123",
            remoteDir = "/MobileSchedule/",
        )
        client = WebDavClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testConnection_success_on207MultiStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody("<D:multistatus xmlns:D=\"DAV:\"></D:multistatus>"))

        val result = client.testConnection(config)
        assertTrue("testConnection should succeed on 207", result.isSuccess)

        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertEquals("0", recorded.getHeader("Depth"))
        assertNotNull(recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun testConnection_failure_on401Unauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = client.testConnection(config)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun ensureRemoteDir_sendsMkcol_successOn201Created() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = client.ensureRemoteDir(config)
        assertTrue(result.isSuccess)

        val recorded = server.takeRequest()
        assertEquals("MKCOL", recorded.method)
        assertTrue(recorded.path?.contains("MobileSchedule") == true)
    }

    @Test
    fun ensureRemoteDir_successOn405_whenAlreadyExists() = runBlocking {
        // In WebDAV, MKCOL on an existing collection returns 405 Method Not Allowed
        server.enqueue(MockResponse().setResponseCode(405))

        val result = client.ensureRemoteDir(config)
        assertTrue("MKCOL 405 should be treated as already existing and succeed", result.isSuccess)
    }

    @Test
    fun listFiles_parsesXmlMultistatus_returnsItems() = runBlocking {
        val xmlBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
                <D:response>
                    <D:href>/MobileSchedule/</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:resourcetype><D:collection/></D:resourcetype>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
                <D:response>
                    <D:href>/MobileSchedule/backup_semester_2026%E7%A7%8B_20261019_220000.json</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:displayname>backup_semester_2026秋_20261019_220000.json</D:displayname>
                            <D:resourcetype/>
                            <D:getcontentlength>2048</D:getcontentlength>
                            <D:getlastmodified>Mon, 19 Oct 2026 21:00:00 GMT</D:getlastmodified>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
            </D:multistatus>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(207).setBody(xmlBody))

        val result = client.listFiles(config)
        assertTrue(result.isSuccess)
        val files = result.getOrThrow()

        assertEquals(1, files.size)
        val file = files.first()
        assertEquals("backup_semester_2026秋_20261019_220000.json", file.name)
        assertFalse(file.isDirectory)
        assertEquals(2048L, file.contentLength)
        assertTrue(file.lastModifiedEpochMillis > 0L)
    }

    @Test
    fun uploadFile_createsDirAndPutsContent() = runBlocking {
        // 1. MKCOL response
        server.enqueue(MockResponse().setResponseCode(201))
        // 2. PUT response
        server.enqueue(MockResponse().setResponseCode(201))

        val jsonContent = """{"formatVersion": 1, "test": true}"""
        val result = client.uploadFile(config, "test_backup.json", jsonContent)
        assertTrue(result.isSuccess)

        val mkcolReq = server.takeRequest()
        assertEquals("MKCOL", mkcolReq.method)

        val putReq = server.takeRequest()
        assertEquals("PUT", putReq.method)
        assertTrue(putReq.path?.endsWith("test_backup.json") == true)
        assertEquals(jsonContent, putReq.body.readUtf8())
        assertTrue(putReq.getHeader("Content-Type")?.contains("application/json") == true)
    }

    @Test
    fun downloadFile_sendsGet_returnsContent() = runBlocking {
        val jsonContent = """{"formatVersion": 1, "semester": {"displayName": "2026秋季学期"}}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonContent))

        val result = client.downloadFile(config, "test_backup.json")
        assertTrue(result.isSuccess)
        assertEquals(jsonContent, result.getOrThrow())

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path?.endsWith("test_backup.json") == true)
    }

    @Test
    fun deleteFile_sendsDelete_successOn204() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = client.deleteFile(config, "test_backup.json")
        assertTrue(result.isSuccess)

        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
    }
}
