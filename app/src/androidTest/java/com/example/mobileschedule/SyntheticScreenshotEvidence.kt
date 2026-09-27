package com.example.mobileschedule

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

/** Saves only synthetic test images; API 26 cannot insert into shared MediaStore without storage permission. */
internal fun saveSyntheticScreenshot(name: String, bitmap: Bitmap) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileScheduleTest")
        }) ?: error("Cannot create screenshot")
        resolver.openOutputStream(uri) ?: error("Cannot write screenshot")
    } else {
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: error("External app storage is unavailable"), "MobileScheduleTest")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create screenshot directory" }
        FileOutputStream(File(directory, name))
    }
    output.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot encode screenshot" } }
}
