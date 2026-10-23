package com.example.mobileschedule.ui.schedule.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class WallpaperMetadata(
    val filePath: String,
    val isDark: Boolean,
    val width: Int,
    val height: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

object ScheduleWallpaperManager {

    private const val WALLPAPER_DIR = "wallpaper"
    private const val WALLPAPER_FILENAME = "schedule_bg.png"
    const val MAX_IMAGE_DIMENSION = 2560

    private val cacheLock = Any()
    @Volatile
    private var cachedBitmap: Bitmap? = null
    @Volatile
    private var cachedPath: String? = null
    @Volatile
    private var cachedModified: Long = 0L

    fun getWallpaperFile(context: Context): File {
        val dir = File(context.filesDir, WALLPAPER_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, WALLPAPER_FILENAME)
    }

    fun hasWallpaper(context: Context): Boolean {
        val file = getWallpaperFile(context)
        return file.exists() && file.length() > 0L
    }

    fun clearWallpaper(context: Context): Boolean {
        synchronized(cacheLock) {
            cachedBitmap = null
            cachedPath = null
            cachedModified = 0L
        }
        val file = getWallpaperFile(context)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    fun loadWallpaperBitmap(filePath: String?): Bitmap? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null

        val lastModified = file.lastModified()
        synchronized(cacheLock) {
            val cached = cachedBitmap
            if (cached != null && !cached.isRecycled && cachedPath == file.absolutePath && cachedModified == lastModified) {
                return cached
            }
        }

        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.also { loaded ->
                synchronized(cacheLock) {
                    cachedBitmap = loaded
                    cachedPath = file.absolutePath
                    cachedModified = lastModified
                }
            }
        }.getOrNull()
    }

    /**
     * Calculates the inSampleSize down-sampling factor so that neither dimension exceeds [maxDimension].
     */
    fun calculateSampleSize(width: Int, height: Int, maxDimension: Int = MAX_IMAGE_DIMENSION): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Reads an image from a content URI safely via single stream copy, handles EXIF orientation,
     * downsamples safely to prevent OOM, analyzes luminance, and persists to internal sandbox storage.
     */
    fun saveWallpaperFromUri(context: Context, uri: Uri): WallpaperMetadata? {
        val tempFile = File(context.cacheDir, "wallpaper_picker_${System.currentTimeMillis()}.tmp")
        return try {
            val contentResolver = context.contentResolver

            // 1. Single stream read: pipe content URI directly into internal cache file once to avoid Permission Denial on reopen
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!tempFile.exists() || tempFile.length() == 0L) return null

            // 2. Extract EXIF orientation before bitmap decoding
            val orientation = runCatching {
                val exif = ExifInterface(tempFile.absolutePath)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            // 3. Decode bounds to compute robust sampleSize preventing OOM
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)
            val sampleSize = calculateSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)

            // 4. Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions) ?: return null

            // 5. Correct orientation using Matrix if needed
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            saveWallpaperBitmap(context, bitmap)
        } catch (_: Throwable) {
            null
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Detects luminance and saves a Bitmap to application internal sandbox storage.
     */
    fun saveWallpaperBitmap(context: Context, bitmap: Bitmap): WallpaperMetadata {
        val isDark = analyzeIsWallpaperDark(bitmap)
        val targetFile = getWallpaperFile(context)

        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        val now = System.currentTimeMillis()
        targetFile.setLastModified(now)

        synchronized(cacheLock) {
            cachedBitmap = bitmap
            cachedPath = targetFile.absolutePath
            cachedModified = targetFile.lastModified()
        }

        return WallpaperMetadata(
            filePath = targetFile.absolutePath,
            isDark = isDark,
            width = bitmap.width,
            height = bitmap.height,
            updatedAt = now,
        )
    }

    /**
     * Determines whether the wallpaper is overall dark by sampling pixels across the image.
     */
    fun analyzeIsWallpaperDark(bitmap: Bitmap): Boolean {
        val sampleGrid = 32
        val stepX = max(1, bitmap.width / sampleGrid)
        val stepY = max(1, bitmap.height / sampleGrid)

        var totalLuminance = 0.0
        var sampledCount = 0

        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF) / 255.0
                val g = (pixel shr 8 and 0xFF) / 255.0
                val b = (pixel and 0xFF) / 255.0

                // Standard relative luminance
                val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                totalLuminance += lum
                sampledCount++
            }
        }

        val avgLuminance = if (sampledCount > 0) totalLuminance / sampledCount else 0.5
        return avgLuminance < 0.45
    }
}
