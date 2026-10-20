package com.example.mobileschedule.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

@Singleton
class WebDavClient(
    private val okHttpClient: OkHttpClient,
) {
    @Inject
    constructor() : this(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    )

    /**
     * Tests connectivity and authentication against the WebDAV server.
     */
    suspend fun testConnection(config: WebDavConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "请填写完整的服务器地址、用户名及密码" }
            val targetUrl = config.serverUrl.trim().removeSuffix("/")

            val request = buildRequest(config, targetUrl, method = "PROPFIND")
                .header("Depth", "0")
                .build()

            execute(request).use { response ->
                when (response.code) {
                    200, 207, 204 -> Unit
                    401 -> throw IllegalArgumentException("用户名或密码错误，鉴权失败 (HTTP 401)")
                    403 -> throw IllegalArgumentException("访问被拒绝，权限不足 (HTTP 403)")
                    404 -> Unit // Server is reachable, but path may require creation
                    else -> throw IllegalStateException("服务器响应异常 (HTTP ${response.code} ${response.message})")
                }
            }
        }
    }

    /**
     * Ensures that the target remote collection directory exists on the WebDAV server.
     */
    suspend fun ensureRemoteDir(config: WebDavConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dirPath = config.normalizedRemoteDir()
            if (dirPath == "/") return@runCatching

            val segments = dirPath.split("/").filter { it.isNotBlank() }
            var currentPath = config.serverUrl.trim().removeSuffix("/")

            for (segment in segments) {
                currentPath = "$currentPath/$segment"
                val mkcolRequest = buildRequest(config, currentPath, method = "MKCOL", body = null).build()
                execute(mkcolRequest).use { response ->
                    when (response.code) {
                        201, 200, 204, 405 -> {
                            // 201: Created; 405: Already exists as collection; all treated as success
                        }
                        else -> {
                            // Verify whether it exists via PROPFIND Depth 0
                            val checkRequest = buildRequest(config, currentPath, method = "PROPFIND")
                                .header("Depth", "0")
                                .build()
                            execute(checkRequest).use { checkResponse ->
                                if (checkResponse.code !in listOf(200, 207, 204)) {
                                    throw IllegalStateException("创建远程目录失败: $currentPath (HTTP ${response.code})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Lists files inside the configured remote directory (Depth: 1).
     */
    suspend fun listFiles(config: WebDavConfig): Result<List<WebDavFileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val collectionUrl = config.resolveCollectionUrl()
            val request = buildRequest(config, collectionUrl, method = "PROPFIND")
                .header("Depth", "1")
                .build()

            execute(request).use { response ->
                when (response.code) {
                    207, 200 -> {
                        val body = response.body?.string().orEmpty()
                        parseMultiStatusXml(body, collectionUrl)
                    }
                    404 -> emptyList() // Directory not yet created or empty
                    401 -> throw IllegalArgumentException("WebDAV 鉴权失败，请检查账号密码 (HTTP 401)")
                    else -> throw IllegalStateException("获取云端列表失败 (HTTP ${response.code} ${response.message})")
                }
            }
        }
    }

    /**
     * Uploads content to a file in the configured remote directory.
     */
    suspend fun uploadFile(config: WebDavConfig, fileName: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureRemoteDir(config).getOrThrow()
            val fileUrl = config.resolveFileUrl(fileName)
            val requestBody = content.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = buildRequest(config, fileUrl, method = "PUT", body = requestBody).build()

            execute(request).use { response ->
                if (response.code in listOf(200, 201, 204)) {
                    Unit
                } else if (response.code == 401) {
                    throw IllegalArgumentException("WebDAV 上传鉴权失败 (HTTP 401)")
                } else {
                    throw IllegalStateException("上传至 WebDAV 失败 (HTTP ${response.code} ${response.message})")
                }
            }
        }
    }

    /**
     * Downloads file content from the WebDAV server.
     */
    suspend fun downloadFile(config: WebDavConfig, fileNameOrUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileUrl = if (fileNameOrUrl.startsWith("http://") || fileNameOrUrl.startsWith("https://")) {
                fileNameOrUrl
            } else {
                config.resolveFileUrl(fileNameOrUrl)
            }

            val request = buildRequest(config, fileUrl, method = "GET").build()
            execute(request).use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: throw IllegalStateException("服务器返回空内容")
                } else if (response.code == 404) {
                    throw IllegalArgumentException("远程备份文件不存在 (HTTP 404)")
                } else if (response.code == 401) {
                    throw IllegalArgumentException("WebDAV 下载鉴权失败 (HTTP 401)")
                } else {
                    throw IllegalStateException("从 WebDAV 下载失败 (HTTP ${response.code} ${response.message})")
                }
            }
        }
    }

    /**
     * Deletes a file on the WebDAV server.
     */
    suspend fun deleteFile(config: WebDavConfig, fileNameOrUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileUrl = if (fileNameOrUrl.startsWith("http://") || fileNameOrUrl.startsWith("https://")) {
                fileNameOrUrl
            } else {
                config.resolveFileUrl(fileNameOrUrl)
            }

            val request = buildRequest(config, fileUrl, method = "DELETE").build()
            execute(request).use { response ->
                if (response.code in listOf(200, 204, 404)) {
                    Unit
                } else {
                    throw IllegalStateException("删除文件失败 (HTTP ${response.code} ${response.message})")
                }
            }
        }
    }

    private fun buildRequest(
        config: WebDavConfig,
        url: String,
        method: String,
        body: RequestBody? = null,
    ): Request.Builder {
        val credentials = Credentials.basic(config.username, config.password, Charsets.UTF_8)
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .header("User-Agent", "MobileSchedule-WebDAV/1.0")

        return when (method.uppercase()) {
            "GET" -> builder.get()
            "PUT" -> builder.put(checkNotNull(body) { "PUT requires body" })
            "DELETE" -> builder.delete(body)
            "MKCOL" -> builder.method("MKCOL", body)
            "PROPFIND" -> builder.method("PROPFIND", body)
            else -> builder.method(method, body)
        }
    }

    private fun execute(request: Request): Response {
        return okHttpClient.newCall(request).execute()
    }

    /**
     * Parses standard WebDAV XML response for PROPFIND Depth: 1.
     */
    private fun parseMultiStatusXml(xml: String, collectionUrl: String): List<WebDavFileItem> {
        if (xml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xml)))
        val responseNodes = doc.getElementsByTagNameNS("*", "response")

        val items = mutableListOf<WebDavFileItem>()
        val normalizedCollectionPath = cleanPath(collectionUrl)

        for (i in 0 until responseNodes.length) {
            val element = responseNodes.item(i) as? Element ?: continue
            val href = getChildText(element, "href") ?: continue
            val cleanedHref = cleanPath(href)

            // Skip the collection itself
            if (cleanedHref == normalizedCollectionPath || cleanedHref.isEmpty()) {
                continue
            }

            val isDirectory = hasChild(element, "collection")
            val rawName = getChildText(element, "displayname")
                ?: href.removeSuffix("/").substringAfterLast("/")
            val decodedName = try {
                URLDecoder.decode(rawName, "UTF-8")
            } catch (_: Exception) {
                rawName
            }

            val lengthStr = getChildText(element, "getcontentlength")
            val contentLength = lengthStr?.toLongOrNull() ?: 0L

            val lastModifiedStr = getChildText(element, "getlastmodified")
            val epochMillis = parseHttpDate(lastModifiedStr)

            items.add(
                WebDavFileItem(
                    href = href,
                    name = decodedName,
                    isDirectory = isDirectory,
                    contentLength = contentLength,
                    lastModified = lastModifiedStr,
                    lastModifiedEpochMillis = epochMillis,
                )
            )
        }
        return items
    }

    private fun cleanPath(urlOrPath: String): String {
        val path = if (urlOrPath.contains("://")) {
            urlOrPath.substringAfter("://").substringAfter("/", "")
        } else {
            urlOrPath
        }
        return "/" + path.trim().removePrefix("/").removeSuffix("/")
    }

    private fun getChildText(element: Element, localName: String): String? {
        val list = element.getElementsByTagNameNS("*", localName)
        if (list.length > 0) {
            return list.item(0).textContent?.trim()
        }
        return null
    }

    private fun hasChild(element: Element, localName: String): Boolean {
        val list = element.getElementsByTagNameNS("*", localName)
        return list.length > 0
    }

    private fun parseHttpDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            try {
                ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
