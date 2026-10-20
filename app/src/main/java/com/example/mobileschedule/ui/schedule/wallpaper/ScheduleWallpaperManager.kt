package com.example.mobileschedule.ui.schedule.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class WallpaperMetadata(
    val filePath: String,
    val isDark: Boolean,
    val width: Int,
    val height: Int,
)

object ScheduleWallpaperManager {

    private const val WALLPAPER_DIR = "wallpaper"
    private const val WALLPAPER_FILENAME = "schedule_bg.png"
    private const val MAX_IMAGE_DIMENSION = 2560

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
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    /**
     * Reads, scales down if needed, detects luminance, and persists an image from a content URI.
     */
    fun saveWallpaperFromUri(context: Context, uri: Uri): WallpaperMetadata? {
        return runCatching {
            val contentResolver = context.contentResolver

            // 1. Decode bounds to compute inSampleSize
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            var sampleSize = 1
            val maxDim = max(options.outWidth, options.outHeight)
            while (maxDim / (sampleSize * 2) >= MAX_IMAGE_DIMENSION) {
                sampleSize *= 2
            }

            // 2. Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            saveWallpaperBitmap(context, bitmap)
        }.getOrNull()
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

        return WallpaperMetadata(
            filePath = targetFile.absolutePath,
            isDark = isDark,
            width = bitmap.width,
            height = bitmap.height,
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
