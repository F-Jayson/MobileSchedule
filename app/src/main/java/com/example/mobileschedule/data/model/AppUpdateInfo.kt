package com.example.mobileschedule.data.model

import java.io.File

/**
 * Information about an available application update.
 */
data class AppUpdateInfo(
    val currentVersionName: String,
    val latestVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val browserUrl: String,
    val hasUpdate: Boolean,
    val apkSize: Long? = null,
    val publishedAt: String? = null,
    val targetAbi: String? = null,
    val forceUpdate: Boolean = false,
)

/**
 * Robust semantic version comparator for version strings like "1.0.0", "v1.0.1", "2.0".
 */
object VersionComparator {

    /**
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionNumbers(remoteVersion)
        val currentParts = parseVersionNumbers(currentVersion)
        val maxLength = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionNumbers(versionStr: String): List<Int> {
        val clean = versionStr.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split("-", "_", "+")
            .firstOrNull() ?: ""

        return clean.split(".")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(0) }
    }
}
