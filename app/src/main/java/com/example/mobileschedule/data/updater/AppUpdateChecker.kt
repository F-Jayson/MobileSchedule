package com.example.mobileschedule.data.updater

import com.example.mobileschedule.BuildConfig
import com.example.mobileschedule.data.model.AppUpdateInfo
import com.example.mobileschedule.data.model.DownloadMirror
import com.example.mobileschedule.data.model.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateChecker(
    private val owner: String = "F-Jayson",
    private val repo: String = "MobileSchedule",
) {

    /**
     * Checks online for latest release information from either the personal blog API or GitHub Releases API.
     */
    suspend fun checkUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME,
        mirror: DownloadMirror = DownloadMirror.DEFAULT,
        blogOrigin: String = DownloadMirror.DEFAULT_BLOG_ORIGIN,
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        if (mirror == DownloadMirror.BLOG) {
            checkBlogUpdate(currentVersion, blogOrigin)
        } else {
            checkGithubUpdate(currentVersion)
        }
    }

    private fun checkBlogUpdate(
        currentVersion: String,
        blogOrigin: String,
    ): Result<AppUpdateInfo> {
        val base = blogOrigin.trim().removeSuffix("/")
        val endpoint = "$base/api/app/schedule/latest"
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MobileSchedule-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("versionName", "").trim()
                val body = json.optString("releaseNotes", "暂无更新说明。").ifBlank { "暂无更新说明。" }
                val rawDownloadUrl = json.optString("downloadUrl", "").trim()
                val fallbackApkUrl = DownloadMirror.BLOG.buildDownloadUrl(rawDownloadUrl, blogOrigin = blogOrigin).takeIf { it.isNotBlank() }
                val fallbackApkSize = json.optLong("apkSize", 0L).takeIf { it > 0 }
                val publishedAt = json.optString("publishedAt").takeIf { it.isNotBlank() }
                val forceUpdate = json.optBoolean("forceUpdate", false)

                val assetsArray = json.optJSONArray("assets")
                val assetsList = mutableListOf<UpdateAsset>()
                if (assetsArray != null) {
                    for (i in 0 until assetsArray.length()) {
                        val item = assetsArray.optJSONObject(i) ?: continue
                        val abi = item.optString("abi", "universal")
                        val fileName = item.optString("fileName", "")
                        val rawUrl = item.optString("downloadUrl", "")
                        val fullUrl = DownloadMirror.BLOG.buildDownloadUrl(rawUrl, blogOrigin = blogOrigin)
                        val size = item.optLong("apkSize", 0L).takeIf { it > 0 }
                        if (fullUrl.isNotBlank()) {
                            assetsList.add(UpdateAsset(abi, fileName, fullUrl, size))
                        }
                    }
                }

                val bestAsset = DeviceAbiMatcher.findBestAsset(assetsList)
                val finalDownloadUrl = bestAsset?.downloadUrl ?: fallbackApkUrl
                val finalApkSize = bestAsset?.byteSize ?: fallbackApkSize
                val targetAbi = bestAsset?.let { DeviceAbiMatcher.normalizeAbi(it.abi) }

                val hasUpdate = VersionComparator.isNewer(tagName, currentVersion)
                val updateInfo = AppUpdateInfo(
                    currentVersionName = currentVersion,
                    latestVersionName = tagName.removePrefix("v").removePrefix("V"),
                    releaseTitle = "v$tagName 课表新版本",
                    releaseNotes = body,
                    downloadUrl = finalDownloadUrl,
                    browserUrl = "$base/api/app/schedule/download",
                    hasUpdate = hasUpdate,
                    apkSize = finalApkSize,
                    publishedAt = publishedAt,
                    targetAbi = targetAbi,
                    forceUpdate = forceUpdate,
                )
                Result.success(updateInfo)
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Result.failure(IllegalStateException("个人博客后台暂未发布新版本课表安装包。"))
            } else {
                Result.failure(IllegalStateException("从个人博客检查更新失败 (HTTP $responseCode)，请稍后重试。"))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("无法连接到个人博客服务 ($endpoint)，请检查网络连接 (${e.localizedMessage ?: "网络异常"})。"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun checkGithubUpdate(
        currentVersion: String,
    ): Result<AppUpdateInfo> {
        val endpoint = "https://api.github.com/repos/$owner/$repo/releases/latest"
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "MobileSchedule-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseText = reader.use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "").trim()
                val title = json.optString("name", tagName).ifBlank { tagName }
                val body = json.optString("body", "暂无更新说明。").ifBlank { "暂无更新说明。" }
                val htmlUrl = json.optString("html_url", "https://github.com/$owner/$repo/releases")
                val publishedAt = json.optString("published_at").takeIf { it.isNotBlank() }
                val forceUpdate = body.contains("[force-update]", ignoreCase = true) || body.contains("【强制更新】")

                val assets = json.optJSONArray("assets") ?: JSONArray()
                val assetsList = mutableListOf<UpdateAsset>()

                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val assetUrl = asset.optString("browser_download_url")
                        val size = asset.optLong("size", 0L).takeIf { it > 0 }
                        if (assetUrl.isNotBlank()) {
                            val abi = DeviceAbiMatcher.normalizeAbi(assetName)
                            assetsList.add(UpdateAsset(abi, assetName, assetUrl, size))
                        }
                    }
                }

                val bestAsset = DeviceAbiMatcher.findBestAsset(assetsList)
                val finalDownloadUrl = bestAsset?.downloadUrl
                val finalApkSize = bestAsset?.byteSize
                val targetAbi = bestAsset?.let { DeviceAbiMatcher.normalizeAbi(it.abi) }

                val hasUpdate = VersionComparator.isNewer(tagName, currentVersion)
                val updateInfo = AppUpdateInfo(
                    currentVersionName = currentVersion,
                    latestVersionName = tagName.removePrefix("v").removePrefix("V"),
                    releaseTitle = title,
                    releaseNotes = body,
                    downloadUrl = finalDownloadUrl,
                    browserUrl = htmlUrl,
                    hasUpdate = hasUpdate,
                    apkSize = finalApkSize,
                    publishedAt = publishedAt,
                    targetAbi = targetAbi,
                    forceUpdate = false,
                )
                Result.success(updateInfo)
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Result.failure(IllegalStateException("暂无已发布的版本。当前已是最新版本 ($currentVersion)。"))
            } else {
                Result.failure(IllegalStateException("检查更新失败 (HTTP $responseCode)，请稍后重试。"))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("无法连接到 GitHub 更新服务器，请切换下载源重试 (${e.localizedMessage ?: "网络异常"})。"))
        } finally {
            connection?.disconnect()
        }
    }
}
