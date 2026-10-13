package com.example.mobileschedule.data.updater

import android.os.Build

data class UpdateAsset(
    val abi: String,
    val fileName: String,
    val downloadUrl: String,
    val byteSize: Long?,
)

/**
 * Utility for matching the best APK asset for the current device's CPU architecture.
 */
object DeviceAbiMatcher {

    /**
     * Extracts and normalizes an ABI tag from file name or raw abi string.
     */
    fun normalizeAbi(nameOrAbi: String): String {
        val lower = nameOrAbi.lowercase()
        return when {
            lower.contains("arm64-v8a") || lower.contains("arm64") || lower.contains("aarch64") -> "arm64-v8a"
            lower.contains("armeabi-v7a") || lower.contains("armv7") || lower.contains("armeabi") -> "armeabi-v7a"
            lower.contains("x86_64") || lower.contains("x64") -> "x86_64"
            lower.contains("x86") -> "x86"
            lower.contains("universal") -> "universal"
            else -> "universal"
        }
    }

    /**
     * Human readable display label for an ABI tag.
     */
    fun getAbiDisplayName(abi: String): String {
        return when (normalizeAbi(abi)) {
            "arm64-v8a" -> "ARM64 专版"
            "armeabi-v7a" -> "ARM32 兼容版"
            "x86_64" -> "x86_64 模拟器版"
            "universal" -> "全架构通用版"
            else -> abi
        }
    }

    /**
     * Finds the best matching asset for the current device according to [supportedAbis].
     * Priority:
     * 1. Exact match of preferred native ABI (e.g. arm64-v8a)
     * 2. Secondary matching supported ABI (e.g. armeabi-v7a)
     * 3. Universal package (compatible with all architectures)
     * 4. Fallback to the first available APK asset
     */
    fun findBestAsset(
        assets: List<UpdateAsset>,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray(),
    ): UpdateAsset? {
        if (assets.isEmpty()) return null
        if (assets.size == 1) return assets.first()

        // 1. Try matching device supported ABIs in priority order
        for (abi in supportedAbis) {
            val normalizedTarget = normalizeAbi(abi)
            val matched = assets.firstOrNull {
                normalizeAbi(it.abi) == normalizedTarget || normalizeAbi(it.fileName) == normalizedTarget
            }
            if (matched != null) return matched
        }

        // 2. Try universal package
        val universal = assets.firstOrNull {
            normalizeAbi(it.abi) == "universal" || normalizeAbi(it.fileName) == "universal"
        }
        if (universal != null) return universal

        // 3. Fallback to the first asset
        return assets.first()
    }
}
