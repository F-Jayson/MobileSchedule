package com.example.mobileschedule.data.updater

import android.content.Context
import android.os.Environment
import com.example.mobileschedule.data.model.DownloadMirror
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateDownloader(private val context: Context) {

    /**
     * Downloads an APK from [downloadUrl] using [mirror] acceleration and notifies progress via [onProgress].
     * @param onProgress Callback with progress fraction (0f to 1f), downloaded bytes, and total bytes.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        versionName: String,
        mirror: DownloadMirror = DownloadMirror.DEFAULT,
        customPrefix: String = "",
        blogOrigin: String = DownloadMirror.DEFAULT_BLOG_ORIGIN,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit = { _, _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetUrl = mirror.buildDownloadUrl(downloadUrl, customPrefix, blogOrigin)
        var connection: HttpURLConnection? = null
        try {
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply {
                if (!exists()) mkdirs()
            }
            val destinationFile = File(downloadDir, "MobileSchedule-v$versionName.apk")

            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            // Follow redirect manually if needed
            val actualConnection = if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                }
            } else {
                connection
            }

            val totalBytes = actualConnection.contentLengthLong
            val inputStream = actualConnection.inputStream
            val outputStream = FileOutputStream(destinationFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        onProgress(progress.coerceIn(0f, 1f), downloadedBytes, totalBytes)
                    }
                }
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("下载更新包失败: ${e.localizedMessage ?: "网络中断"}"))
        } finally {
            connection?.disconnect()
        }
    }
}
