package com.example.mobileschedule.data.cloud

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Configuration for connecting to a private WebDAV server.
 */
data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteDir: String = "/MobileSchedule/",
    val autoBackupOnExport: Boolean = false,
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /**
     * Normalizes the remote directory path to always start and end with '/'.
     */
    fun normalizedRemoteDir(): String {
        val trimmed = remoteDir.trim()
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        val withStart = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return if (withStart.endsWith("/")) withStart else "$withStart/"
    }

    /**
     * Resolves the full URL for the configured remote directory.
     */
    fun resolveCollectionUrl(): String {
        val base = serverUrl.trim().removeSuffix("/")
        val dir = normalizedRemoteDir()
        return "$base$dir"
    }

    /**
     * Resolves the full URL for a specific file inside the remote directory.
     */
    fun resolveFileUrl(fileName: String): String {
        val collection = resolveCollectionUrl()
        val cleanFile = fileName.trim().removePrefix("/")
        return "$collection$cleanFile"
    }
}

/**
 * Raw file item retrieved from a WebDAV PROPFIND response.
 */
data class WebDavFileItem(
    val href: String,
    val name: String,
    val isDirectory: Boolean,
    val contentLength: Long = 0L,
    val lastModified: String? = null,
    val lastModifiedEpochMillis: Long = 0L,
)

/**
 * High-level schedule backup item stored in the WebDAV cloud.
 */
data class WebDavBackupItem(
    val fileName: String,
    val fileUrl: String,
    val semesterName: String,
    val lastModifiedEpochMillis: Long,
    val sizeBytes: Long = 0L,
) {
    val formattedDate: String
        get() = if (lastModifiedEpochMillis > 0L) {
            val instant = Instant.ofEpochMilli(lastModifiedEpochMillis)
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } else {
            "未知时间"
        }

    val formattedSize: String
        get() = when {
            sizeBytes <= 0L -> ""
            sizeBytes < 1024 -> "${sizeBytes} B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", sizeBytes.toDouble() / (1024 * 1024))
        }
}
