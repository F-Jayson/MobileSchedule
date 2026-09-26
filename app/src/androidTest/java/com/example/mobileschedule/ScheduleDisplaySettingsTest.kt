package com.example.mobileschedule

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.ui.schedule.ScheduleDisplayOptions
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsScreen
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleDisplaySettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun displayChoicesSurviveStoreRecreationWithoutChangingCourseData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "display-test-${UUID.randomUUID()}"
        try {
            val first = ScheduleDisplaySettingsStore(context, name)
            assertEquals(ScheduleDisplayOptions(showLocation = true, showTeacher = true), first.read())
            assertTrue(first.save(ScheduleDisplayOptions(showLocation = false, showTeacher = true)))
            val reopened = ScheduleDisplaySettingsStore(context, name)
            assertEquals(ScheduleDisplayOptions(showLocation = false, showTeacher = true), reopened.read())
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun displaySettingsPageLetsUserChooseLocationAndTeacherIndependently() {
        compose.setContent { MaterialTheme {
            var options by remember { mutableStateOf(ScheduleDisplayOptions()) }
            ScheduleDisplaySettingsScreen(options, onOptionsChange = { options = it }, onBack = {})
        } }
        compose.onNodeWithTag("display_settings_page").assertIsDisplayed()
        compose.onNodeWithTag("display_location_switch").performClick()
        compose.onNodeWithTag("display_teacher_switch").performClick()
        compose.onNodeWithTag("display_location_switch").assertIsOff()
        compose.onNodeWithTag("display_teacher_switch").assertIsOff()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "schedule-display-settings.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileScheduleTest")
        }) ?: error("Cannot create settings evidence")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            compose.onNodeWithTag("display_settings_page").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Cannot write settings evidence")
    }
}
