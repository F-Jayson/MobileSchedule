package com.example.mobileschedule.data.cloud

import com.example.mobileschedule.data.model.SemesterImportPreview
import com.example.mobileschedule.data.repository.ScheduleManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavSyncManager @Inject constructor(
    private val client: WebDavClient,
    private val configStore: WebDavConfigStore,
    private val scheduleManagementService: ScheduleManagementService,
) {

    /**
     * Verifies WebDAV server connectivity and ensures directory existence.
     */
    suspend fun testConnection(config: WebDavConfig = configStore.getConfig()): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "请先配置 WebDAV 服务器地址、用户名及密码" }
            client.testConnection(config).getOrThrow()
            client.ensureRemoteDir(config).getOrThrow()
        }
    }

    /**
     * Exports a semester and silently uploads it to the WebDAV cloud.
     * Naming convention: backup_semester_{semesterName}_{yyyyMMdd_HHmmss}.json
     */
    suspend fun backupSemester(
        semesterId: Long,
        config: WebDavConfig = configStore.getConfig(),
    ): Result<WebDavBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "WebDAV 未配置，无法上传备份" }

            val jsonContent = scheduleManagementService.exportSemester(semesterId)
            val semesterDisplayName = parseSemesterDisplayName(jsonContent)
            val safeName = semesterDisplayName.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val fileName = "backup_semester_${safeName}_${timestamp}.json"

            client.uploadFile(config, fileName, jsonContent).getOrThrow()

            WebDavBackupItem(
                fileName = fileName,
                fileUrl = config.resolveFileUrl(fileName),
                semesterName = semesterDisplayName,
                lastModifiedEpochMillis = System.currentTimeMillis(),
                sizeBytes = jsonContent.toByteArray(Charsets.UTF_8).size.toLong(),
            )
        }
    }

    /**
     * Fetches all schedule backups from the cloud directory.
     */
    suspend fun listCloudBackups(
        config: WebDavConfig = configStore.getConfig(),
    ): Result<List<WebDavBackupItem>> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "WebDAV 未配置" }
            val rawFiles = client.listFiles(config).getOrThrow()

            rawFiles.filter { !it.isDirectory && it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(".json") }
                .map { file ->
                    val semesterName = extractSemesterNameFromFileName(file.name)
                    WebDavBackupItem(
                        fileName = file.name,
                        fileUrl = file.href,
                        semesterName = semesterName,
                        lastModifiedEpochMillis = file.lastModifiedEpochMillis,
                        sizeBytes = file.contentLength,
                    )
                }
                .sortedByDescending { it.lastModifiedEpochMillis }
        }
    }

    /**
     * Downloads a remote backup JSON and prepares a [SemesterImportPreview] using existing validation logic.
     */
    suspend fun downloadAndPreparePreview(
        item: WebDavBackupItem,
        config: WebDavConfig = configStore.getConfig(),
    ): Result<SemesterImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "WebDAV 未配置" }
            val jsonString = client.downloadFile(config, item.fileName).getOrThrow()
            scheduleManagementService.importSemesterFromJson(jsonString).getOrThrow()
        }
    }

    /**
     * Deletes a cloud backup file.
     */
    suspend fun deleteCloudBackup(
        item: WebDavBackupItem,
        config: WebDavConfig = configStore.getConfig(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "WebDAV 未配置" }
            client.deleteFile(config, item.fileName).getOrThrow()
        }
    }

    private fun parseSemesterDisplayName(jsonString: String): String {
        return try {
            val root = Json.parseToJsonElement(jsonString).jsonObject
            val semesterObj = root["semester"]?.jsonObject
            semesterObj?.get("displayName")?.jsonPrimitive?.content ?: "课程表"
        } catch (_: Exception) {
            "课程表"
        }
    }

    private fun extractSemesterNameFromFileName(fileName: String): String {
        // Filename format: backup_semester_{name}_{yyyyMMdd_HHmmss}.json
        val stripped = fileName.removePrefix(BACKUP_PREFIX).removeSuffix(".json")
        val lastUnderscore = stripped.lastIndexOf('_')
        return if (lastUnderscore > 0) {
            val candidate = stripped.substring(0, lastUnderscore)
            val secondLastUnderscore = candidate.lastIndexOf('_')
            if (secondLastUnderscore > 0 && stripped.substring(lastUnderscore + 1).length == 6) {
                // matched yyyyMMdd_HHmmss
                candidate.substring(0, secondLastUnderscore)
            } else {
                candidate
            }
        } else {
            stripped
        }
    }

    companion object {
        const val BACKUP_PREFIX = "backup_semester_"
    }
}
